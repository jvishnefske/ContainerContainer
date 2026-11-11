/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo.service.trash;
import static org.artifactory.sh.cleanup.events.EventsGcConfiguration.useEventGCinMinorGC;
import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.config.ConfigurationException;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.trash.TrashGCHelper;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.GCCandidate;
import org.artifactory.storage.db.binstore.service.cleanup.TrashUtil;
import org.artifactory.storage.db.fs.model.DbFsItemProvider;
import org.artifactory.storage.fs.lock.SessionLockEntry;
import org.artifactory.storage.fs.session.StorageSession;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.util.RepoPathUtils;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.LOW_PRIORITY;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;

/**
 * @author Shay Yaakov
 */
@Slf4j
@Reloadable(beanClass = TrashService.class, initAfter = {InternalCentralConfigService.class,
        ArtifactoryBinaryService.class},
        listenOn = CentralConfigKey.none)
@Service
public class TrashServiceImpl implements TrashService {
    private final InternalRepositoryService repoService;
    private final SecurityService securityService;

    private final CentralConfigService configService;

    private final PropertiesService propertiesService;

    private final org.artifactory.storage.fs.service.PropertiesService storagePropsService;

    private final NonTrashableItems nonTrashableItems;

    private final TrashGCHelper trashGcHelper;

    @Autowired
    public TrashServiceImpl(InternalRepositoryService repoService,
            SecurityService securityService,
            CentralConfigService configService,
            PropertiesService propertiesService,
            org.artifactory.storage.fs.service.PropertiesService storagePropsService,
            NonTrashableItems nonTrashableItems,
            TrashGCHelper trashGcHelper) {
        this.repoService = repoService;
        this.securityService = securityService;
        this.configService = configService;
        this.propertiesService = propertiesService;
        this.storagePropsService = storagePropsService;
        this.nonTrashableItems = nonTrashableItems;
        this.trashGcHelper = trashGcHelper;
    }

    @Override
    public boolean copyToTrash(RepoPath repoPath) {
        if (shouldSkipCopyToTrash(repoPath)) {
            return false;
        }

        SecurityContext securityContext = SecurityContextHolder.getContext();
        Authentication originalAuthentication = securityContext.getAuthentication();
        if (originalAuthentication == null) {
            log.debug("Original Authentication can't be null while copying to trash");
            throw new IllegalStateException("Original Authentication can't be null while copying to trash");
        }
        try {
            trashAsSystem(repoPath, originalAuthentication.getName());
        } finally {
            securityContext.setAuthentication(originalAuthentication);
        }
        return true;
    }

    @Override
    public boolean shouldSkipCopyToTrash(RepoPath repoPath) {
        if (RepoPathUtils.isTrash(repoPath)) {
            return true;
        }
        LocalConfig localConfig = repoService.localFederatedOrCachedRepoConfigByKey(repoPath.getRepoKey());
        if (localConfig == null || localConfig.isCache()) {
            return true;
        }
        if (!configService.getDescriptor().getTrashcanConfig().isEnabled()) {
            return true;
        }
        return nonTrashableItems.skipTrash(localConfig, repoPath.getPath());
    }

    public void copyFilesToTrash(@NonNull List<RepoPath> repoPaths) {
        for (RepoPath repoPath : repoPaths) {
            copyToTrash(repoPath);
        }
    }

    private void trashAsSystem(RepoPath repoPath, String deletedBy) {
        securityService.authenticateAsSystem();
        RepoPath trashPath = RepoPathFactory.create(TRASH_REPO_KEY, repoPath.getRepoKey() + "/" + repoPath.getPath());
        if (repoPath.isFile()) {
            undeployIfAncestorFilesInTrash(trashPath);
            log.debug("Trashing item '{}'", repoPath);
            Properties properties = buildDeletedFileProperties(repoPath, deletedBy);
            repoService.copy(repoPath, trashPath, false, true, true);
            propertiesService.setProperties(trashPath, properties, true);
        } else {
            log.debug("Overriding folder properties on trashed item '{}'", trashPath);
            if (repoService.exists(trashPath)) {
                propertiesService.setProperties(trashPath, buildDeletedDirectoryProperties(repoPath, deletedBy), true);
            }
        }
    }

    /**
     * TrashPath is the future path of the deleted file. If any of the folders in the path exist in trash as a file
     * it will be deleted from trash and replaced with the folder hierarchy. This in order to preserve the trash can
     * policy to save the last thing deleted.
     *
     * + an actual override of folders happens here. We need to remove the folder from the trashcan and get a new
     * MutableVfsFolder
     * under during the transcation, in order to finally save the new folder at the final destination safely later on.
     */
    void undeployIfAncestorFilesInTrash(RepoPath trashPath) {
        List<RepoPath> ancestors = DbFsItemProvider.getAncestors(trashPath);
        //we want to iterate the path from the beginning
        Collections.reverse(ancestors);
        for (RepoPath ancestor : ancestors) {
            try {
                repoService.getFileInfo(ancestor);
                repoService.undeploy(ancestor);
                StorageSession storageSession = StorageSessionHolder.getSession();
                if (storageSession != null) {
                    SessionLockEntry lockEntry = storageSession.getLockEntry(ancestor);
                    lockEntry.save();
                    lockEntry.setWriteFsItem(null);
                    RepoRepoPath<Repo> repoRepoPath = repoService.getRepoRepoPath(ancestor);
                    DbLocalRepo repo = (DbLocalRepo) repoRepoPath.getRepo();
                    lockEntry.setWriteFsItem(repo.createOrGetFolder(ancestor));
                }
            } catch (ItemNotFoundRuntimeException e) {
                return;
            } catch (FileExpectedException e) {
                //continue to check next folder in path.
            }
        }
    }

    @Override
    public MoveMultiStatusHolder restoreBulk(RepoPath repoPath, String restoreRepo, String restorePath,
            int transactionSize) {
        final MoveMultiStatusHolder finalStatus = new MoveMultiStatusHolder();
        if (repoService.localOrFederatedRepoConfigByKey(restoreRepo) == null) {
            finalStatus.warn("Restore repo '" + restoreRepo + "' doesn't exist", log);
            return finalStatus;
        }

        List<String> propertiesToRemove = Lists.newArrayList();
        propertiesToRemove.add(PROP_TRASH_TIME);
        propertiesToRemove.add(PROP_DELETED_BY);
        propertiesToRemove.add(TRASH_REPO_KEY);
        propertiesToRemove.add(PROP_ORIGIN_REPO_TYPE);
        propertiesToRemove.add(PROP_ORIGIN_PATH);

        boolean wholeRepoRestore = RepoPathFactory.create(restoreRepo, restorePath).isRoot();
        RepoPath targetRepoRoot = RepoPathFactory.create(restoreRepo);
        Properties targetRootSnapshot = wholeRepoRestore
                ? propertiesService.getProperties(targetRepoRoot)
                : null;

        // NOTE: We intentionally pass an EMPTY propsAppliedToEachMovedItem to moveFlat —
        // there is no intention to apply any properties to all moved items.
        // See RTDEV-65263 and RTDEV-84660.
        final Properties emptyPropsForRestore = new PropertiesImpl();
        MoveMultiStatusHolder status = repoService.moveFlat(repoPath, restoreRepo, restorePath,
                emptyPropsForRestore, propertiesToRemove, false, true, transactionSize);
        finalStatus.merge(status);

        if (wholeRepoRestore && !status.isError() && targetRootSnapshot != null
                && !targetRootSnapshot.isEmpty()) {
            try {
                Properties reapplied = propertiesService.updateProperties(targetRepoRoot, targetRootSnapshot);
                if (reapplied == null) {
                    finalStatus.error("Restore completed but failed to re-apply repository-level properties to '"
                            + restoreRepo + "': repository root not found post-move", log);
                }
            } catch (RuntimeException e) {
                finalStatus.error("Restore completed but failed to re-apply repository-level properties to '"
                        + restoreRepo + "': " + e.getMessage(), e, log);
            }
        }

        return finalStatus;
    }

    @Override
    public MoveMultiStatusHolder restoreBulk(RepoPath repoPath, String restoreRepo, String restorePath) {
        int transactionSize = ConstantValues.moveCopyDefaultTransactionSize.getInt();
        return restoreBulk(repoPath, restoreRepo, restorePath, transactionSize);

    }

    @Override
    public MoveMultiStatusHolder restore(RepoPath repoPath, String restoreRepo, String restorePath) {
        MoveMultiStatusHolder status = new MoveMultiStatusHolder();
        if (repoService.localOrFederatedRepoConfigByKey(restoreRepo) == null) {
            status.warn("Restore repo '" + restoreRepo + "' doesn't exist", log);
            return status;
        }

        TrashPropertiesUtils.cleanupTrashProperties(propertiesService, repoPath);

        RepoPath restoreRepoPath = RepoPathFactory.create(restoreRepo, restorePath);
        final MoveMultiStatusHolder finalStatus = new MoveMultiStatusHolder();
        if (isRootRepoRestore(repoPath)) {
            repoService.getChildren(repoPath).stream()
                    .map(ItemInfo::getRepoPath)
                    .forEach(child -> {
                        MoveMultiStatusHolder moveMultiStatusHolder = restorePath(child, restoreRepoPath);
                        finalStatus.merge(moveMultiStatusHolder);
                    });
        } else {
            finalStatus.merge(restorePath(repoPath, restoreRepoPath));
        }

        return finalStatus;
    }

    private MoveMultiStatusHolder restorePath(RepoPath repoPath, RepoPath restoreRepoPath) {
        return repoService.moveMultiTx(repoPath, restoreRepoPath, false, true, true);
    }

    private boolean isRootRepoRestore(RepoPath repoPath) {
        RepoPath parent = repoPath.getParent();
        return parent != null && StringUtils.isBlank(parent.getPath());
    }

    @Override
    public StatusHolder empty() {
        return withQueryRateLimiter(LOW_PRIORITY,
                () -> repoService.undeployMultiTransaction(InternalRepoPathFactory.repoRootPath(TRASH_REPO_KEY)));
    }

    private Properties buildDeletedFileProperties(RepoPath repoPath, String deletedBy) {
        Properties properties = buildDeletedDirectoryProperties(repoPath, deletedBy);
        properties.replaceValues(PROP_TRASH_TIME, Lists.newArrayList(String.valueOf(System.currentTimeMillis())));
        return properties;
    }
    private Properties buildDeletedDirectoryProperties(RepoPath repoPath, String deletedBy) {
        Properties properties = propertiesService.getProperties(repoPath);
        PropertiesImpl newProperties = new PropertiesImpl(properties);
        String repoKey = repoPath.getRepoKey();
        newProperties.replaceValues(PROP_DELETED_BY, Lists.newArrayList(deletedBy));
        newProperties.replaceValues(PROP_ORIGIN_REPO, Lists.newArrayList(repoKey));
        RepoConfig repoConfig = repoService.getRepoConfigByKey(repoKey);
        if (repoConfig != null) {          // RTDEV-66981
            newProperties.replaceValues(PROP_ORIGIN_REPO_TYPE,
                    Lists.newArrayList(repoConfig.getPackageType().getType()));
        }
        newProperties.replaceValues(PROP_ORIGIN_PATH, Lists.newArrayList(repoPath.getPath()));
        return newProperties;
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        //noop
    }

    @Override
    public void destroy() {

    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {

    }

    @Override
    public List<GCCandidate> getGCCandidatesFromTrash() {
        if (configService.getDescriptor().getTrashcanConfig().isEnabled()) {
            String validFrom = getTrashValidFrom();
            if (StringUtils.isNotBlank(validFrom) && Long.valueOf(validFrom) > 0) {
                return storagePropsService.getGCCandidatesFromTrash(validFrom);
            }
        }
        return new ArrayList<>();
    }

    @Override
    public List<GCCandidate> getTrashBinariesMarkedForGC() {
        if (configService.getDescriptor().getTrashcanConfig().isEnabled()) {
            return trashGcHelper
                    .getTrashFilesMarkedForGC().stream()
                    .filter(item -> !repoService.existsBySha1(item.getSha1()))
                    .map(item -> new GCCandidate(null, item.getSha1(), item.getSha2(), null, item.getLength())).collect(
                            Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isTrashcanEnabled() {
        return configService.getDescriptor().getTrashcanConfig().isEnabled();
    }

    //For GC use only
    @Override
    public void undeployFromTrash(GCCandidate gcCandidate) {
        if (gcCandidate.repoPath().isFile() && !gcCandidate.repoPath().isRoot()) {
            if (!TrashUtil.isTrashItem(gcCandidate)) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping non-trash item {}", gcCandidate.repoPath().toPath());
                }
                return;
            }
            long itemDeleteStartTime = System.currentTimeMillis();
            boolean pruneEmptyFolders = !ConstantValues.keepTrashCanEmptyFolders.getBoolean();
            log.debug("Deleting item: {}, and {} empty folders",
                    gcCandidate, pruneEmptyFolders ? "removing" : "keeping");
            if (useEventGCinMinorGC()) {
                repoService.undeploy(gcCandidate.repoPath(), false, pruneEmptyFolders);
            } else {
                repoService.gcUndeploy(gcCandidate.repoPath(), false, pruneEmptyFolders);
            }
            if (log.isDebugEnabled()) {
                log.debug("Deleting item: {} took {} millis", gcCandidate.repoPath(),
                        System.currentTimeMillis() - itemDeleteStartTime);
            }
        }
    }

    private String getTrashValidFrom() {
        int retentionPeriodDays = configService.getDescriptor().getTrashcanConfig().getRetentionPeriodDays();

        long valueFrom = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionPeriodDays);
        validateRetentionPeriodTimestamp(valueFrom);
        return String.valueOf(valueFrom);
    }

    @Override
    public void validateRetentionPeriodTimestamp(long retentionDaysTimestamp) {
        long minAllowedTimestamp = 1044106642176L; //2003, January 1
        if (retentionDaysTimestamp < minAllowedTimestamp) {
            throw new ConfigurationException("Illegal retention date. Retention period goes back too far.");
        }
    }
}
