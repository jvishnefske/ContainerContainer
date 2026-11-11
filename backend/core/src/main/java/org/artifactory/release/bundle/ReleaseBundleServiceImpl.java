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

package org.artifactory.release.bundle;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jfrog.distribution.handler.acl.bridge.DistributionHandlerBridgeToModule;
import com.jfrog.distribution.handler.acl.model.actions.TargetBundleActionsBaseModel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.component.ComponentDetails;
import org.artifactory.api.component.ComponentDetailsFetcher;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.release.bundle.ReleaseBundleService;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactProperty;
import org.artifactory.api.rest.distribution.bundle.models.ArtifactsBundleModel;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseArtifact;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseBundleModel;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.bundle.BundleNameAndRepo;
import org.artifactory.bundle.BundleTransactionStatus;
import org.artifactory.bundle.BundleType;
import org.artifactory.bundle.LightArtifactsBundle;
import org.artifactory.md.Properties;
import org.artifactory.model.bundle.ArtifactsBundleImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.release.bundle.exception.ReleaseBundleDeletionException;
import org.artifactory.release.bundle.interceptor.ReleaseBundleInterceptors;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.artifactory.storage.db.bundle.dao.BundleBlobsDao;
import org.artifactory.storage.db.bundle.model.BundleNode;
import org.artifactory.storage.db.bundle.model.DBArtifactsBundle;
import org.artifactory.storage.db.bundle.model.DBBundleResult;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.entity.Node;
import org.artifactory.storage.db.fs.entity.NodePath;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.session.StorageSession;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.jfrog.common.ExecutionUtils;
import org.jfrog.common.RetryException;
import org.jfrog.model.api.distribution.handler.actions.TargetBundleAction;
import org.jfrog.storage.StorageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.releaseBundleRetryDeleteSharedArtifacts;

/**
 * @author Rotem Kfir
 */
@Slf4j
@Service
public class ReleaseBundleServiceImpl implements ReleaseBundleService {

    private static final long RELATED_BUNDLES_LIMIT = 5L;
    static final String COULD_NOT_DELETE_RB_ERROR_MSG = "Could not delete Release Bundle";

    private final MeterRegistry metrics;
    private final ArtifactBundlesDao bundlesDao;
    private final BundleBlobsDao blobsDao;
    private final NodesDao nodesDao;
    private final InternalRepositoryService repoService;
    private final PropertiesService propertiesService;
    private final ComponentDetailsFetcher componentDetailsFetcher;
    private final AuthorizationService authService;
    private final SecurityService securityService;
    private final InternalReleaseBundleService internalReleaseBundleService;
    private final FileService fileService;
    private final ReleaseBundleInterceptors releaseBundleInterceptors;
    private final DistributionHandlerBridgeToModule bridgeToModule;

    long initialTimeBetweenRetries = TimeUnit.MINUTES.toMillis(1);
    ExecutorService executorService = new ThreadPoolExecutor(0, 2, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());

    @Autowired
    public ReleaseBundleServiceImpl(ArtifactBundlesDao bundlesDao, BundleBlobsDao blobsDao, NodesDao nodesDao,
            InternalRepositoryService repoService, PropertiesService propertiesService,
            ComponentDetailsFetcher componentDetailsFetcher, AuthorizationService authorizationService,
            SecurityService securityService, InternalReleaseBundleService internalReleaseBundleService,
            FileService fileService, ReleaseBundleInterceptors releaseBundleInterceptors, MeterRegistry metrics,
            DistributionHandlerBridgeToModule bridgeToModule) {
        this.bundlesDao = bundlesDao;
        this.blobsDao = blobsDao;
        this.nodesDao = nodesDao;
        this.repoService = repoService;
        this.propertiesService = propertiesService;
        this.componentDetailsFetcher = componentDetailsFetcher;
        this.authService = authorizationService;
        this.securityService = securityService;
        this.internalReleaseBundleService = internalReleaseBundleService;
        this.fileService = fileService;
        this.releaseBundleInterceptors = releaseBundleInterceptors;
        this.metrics = metrics;
        this.bridgeToModule = bridgeToModule;
    }

    @Override
    @Nonnull
    public List<Long> findExpiredBundlesIds(Long cleanupPeriod) {
        try {
            return bundlesDao.getExpiredBundlesIds(cleanupPeriod);
        } catch (SQLException e) {
            throw new StorageException("Could not get expired Release Bundle transactions", e);
        }
    }

    @Override
    public boolean deleteBundle(ArtifactsBundleModel bundleModel) {
        try {
            List<BundleNode> bundleNodes = bundlesDao.getBundleNodes(bundleModel.getId());
            assertNoPathsWithoutDeletePermission(bundleModel, bundleNodes);

            Set<Long> nodeIdsWithArtifactsBelongingOnlyToThisBundle = Sets.newHashSet();
            Set<Long> nodeIdsWithArtifactsBelongingToVariousBundles = Sets.newHashSet();

            // Find all nodes that are related only to this bundle
            filterNodesIds(bundleNodes, nodeIdsWithArtifactsBelongingOnlyToThisBundle,
                    nodeIdsWithArtifactsBelongingToVariousBundles);

            DBArtifactsBundle artifactsBundle = getDBArtifactsBundle(bundleModel.getId());
            internalReleaseBundleService
                    .deleteReleaseBundleInternal(bundleModel, bundleNodes,
                            nodeIdsWithArtifactsBelongingOnlyToThisBundle);

            if (releaseBundleRetryDeleteSharedArtifacts.getBoolean()) {
                //Try to delete nodeIds with artifacts belonging to various bundles after committing the transaction
                StorageSession session = StorageSessionHolder.getSession();
                if (session != null) {
                    session.getOrCreateResource(DeleteReleaseBundleArtifactsTransactionResource.class)
                            .init(internalReleaseBundleService, bundleModel, bundleNodes,
                                    nodeIdsWithArtifactsBelongingToVariousBundles);
                } else {
                    log.warn("StorageSessionHolder.getSession() is null, cannot retry delete shared artifacts");
                }
            }

            reportBundleLocallyDeletedFromTarget(artifactsBundle);
            addMetricForBundleDeletion(bundleModel.getType());
            return true;
        } catch (SQLException e) {
            throw new StorageException(COULD_NOT_DELETE_RB_ERROR_MSG, e);
        }
    }

    @VisibleForTesting
    void filterNodesIds(List<BundleNode> bundleNodes, Set<Long> nodeIdsWithArtifactsBelongingOnlyToThisBundle,
            Set<Long> nodeIdsWithArtifactsBelongingToVariousBundles) {
        for (BundleNode bundleNode : bundleNodes) {
            if (shouldDeleteNode(bundleNode)) {
                nodeIdsWithArtifactsBelongingOnlyToThisBundle.add(bundleNode.getNodeId());
            } else {
                nodeIdsWithArtifactsBelongingToVariousBundles.add(bundleNode.getNodeId());
            }
        }
    }

    private boolean shouldDeleteNode(BundleNode bundleNode) {
        try {
            return bundlesDao.getAllBundlesRelatedToNode(bundleNode.getNodeId()).stream()
                    .allMatch(relatedBundleId -> relatedBundleId.equals(bundleNode.getBundleId()));
        } catch (SQLException e) {
            throw new StorageException(COULD_NOT_DELETE_RB_ERROR_MSG, e);
        }
    }

    @Override
    public boolean deleteBundle(Long id) {
        log.debug("Deleting Release Bundle with id '{}'", id);
        try {
            DBArtifactsBundle artifactsBundle = bundlesDao.getArtifactsBundle(id);
            if (artifactsBundle == null) {
                throw new NotFoundException("Release bundle not found");
            }
            if (bridgeToModule.isRetentionFeatureEnabled()) {
                assertBundleIsNotMarkedAsKeep(artifactsBundle);
            }
            ArtifactsBundleModel artifactsBundleModel =
                    ReleaseBundleUtils.convertToArtifactsBundleModel(artifactsBundle);
            return deleteBundle(artifactsBundleModel);
        } catch (SQLException e) {
            throw new StorageException(COULD_NOT_DELETE_RB_ERROR_MSG, e);
        }
    }

    @Override
    public boolean redistributeBundle(Long id) {
        log.info("Redistributing Release Bundle with id '{}'", id);
        try {
            return bundlesDao.updateArtifactsBundleCreationDate(id) > 0;
        } catch (SQLException e) {
            throw new StorageException("Could not redistribute Release Bundle with id '" + id + "'", e);
        }
    }

    @Override
    public List<LightArtifactsBundle> getRelatedBundleDetails(RepoPath repoPath) {
        try {
            boolean isFile = !repoPath.isRoot() && !isFolder(repoPath);
            return bundlesDao.getRelatedArtifactsBundles(repoPath.toPath(), isFile, RELATED_BUNDLES_LIMIT).stream()
                    .map(ab -> ArtifactsBundleImpl.builder()
                            .id(ab.getId())
                            .name(ab.getName())
                            .version(ab.getVersion())
                            .build())
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            String message = "Could not check if Repo path '" + repoPath + "' is related to a Release Bundle";
            log.error(message, e);
            throw new StorageException(message, e);
        }
    }

    @Override
    public boolean isReleaseBundleExistByName(@NonNull String releaseBundleName) {
        try {
            return bundlesDao.isReleaseBundleExist(releaseBundleName);
        } catch (SQLException sqe) {
            String message = "Failed to verify release bundle " + releaseBundleName + " exists";
            log.error(message);
            throw new StorageException(message, sqe);
        }
    }

    private boolean isFolder(RepoPath repoPath) {
        try {
            Boolean isFolder = fileService.isFolder(repoPath);
            return isFolder != null && isFolder;
        } catch (Exception e) {
            log.warn("Failed to check type of repoKey:{}, path:{}", repoPath.getRepoKey(), repoPath.getPath(), e);
            return false;
        }
    }

    @Override
    public void deleteAllBundles() {
        try {
            bundlesDao.deleteAllBundleNodes();
            blobsDao.deleteAllBlobs();
            bundlesDao.deleteAllArtifactsBundles();
        } catch (SQLException e) {
            log.error("Failed to delete all release bundles", e);
        }
    }

    @Override
    public void copyBundleArtifactsAsync(ReleaseBundleModel bundleModel, String storingRepo,
            Map<String, String> artifactMapping) throws SQLException {
        String bundleNameAndVersion = bundleModel.getName() + "/" + bundleModel.getVersion();
        // artifacts should be stored as storingRepo/(RB-name/RB-version)/targetRepo/artifacts
        log.info("Coping Release Bundle {} content to the storing repository: {}", bundleNameAndVersion, storingRepo);
        for (ReleaseArtifact artifact : bundleModel.getArtifacts()) {
            try {
                copyArtifact(artifact, storingRepo, bundleNameAndVersion, artifactMapping, bundleModel);
            } catch (Exception e) {
                log.error("Failed to copy artifact {} in source release bundle {}: {}", artifact.getRepoPath(),
                        bundleNameAndVersion,
                        e.getMessage(), e);
                setBundleStatusWithRetries(bundleModel.getName(), bundleModel.getVersion(),
                        BundleTransactionStatus.FAILED, BundleType.SOURCE);
                return;
            }
        }
        // set bundle as completed
        bundlesDao.tryToSetBundleStatus(bundleModel.getName(),
                bundleModel.getVersion(),
                BundleType.SOURCE,
                BundleTransactionStatus.COMPLETE);
    }

    @Override
    public void createBundleNode(long artifactsBundleId, RepoPath targetRepoPath, ComponentDetails componentDetails)
            throws SQLException {
        long nodeId = nodesDao.getNodeId(NodePath.fromRepoPath(targetRepoPath));
        BundleNode bundleNode = new BundleNode();
        bundleNode.setBundleId(artifactsBundleId);
        bundleNode.setNodeId(nodeId);
        bundleNode.setRepoPath(targetRepoPath.toPath());
        if (componentDetails !=
                null) { // in case this node is created at the target artifactory it doesn't have details
            bundleNode.setOriginalFileDetails(ComponentDetails.toJson(componentDetails));
        }
        bundlesDao.create(bundleNode);
    }

    @Override
    public void createBundleNodes(long artifactsBundleId, @NonNull List<RepoPath> repoPaths) throws SQLException {
        Map<NodePath, Node> nodePathNodeMap = nodesDao.get(NodePath.fromRepoPaths(repoPaths));

        List<BundleNode> bundleNodesToCreate = new ArrayList<>();
        for (Map.Entry<NodePath, Node> entry : nodePathNodeMap.entrySet()) {
            BundleNode bundleNode = new BundleNode();
            bundleNode.setBundleId(artifactsBundleId);
            bundleNode.setNodeId(entry.getValue().getNodeId());
            bundleNode.setRepoPath(entry.getKey().toRepoPath().toPath());
            bundleNodesToCreate.add(bundleNode);
        }
        bundlesDao.createBulk(bundleNodesToCreate);
    }

    @Override
    public ComponentDetails getOriginalComponentDetails(@NonNull RepoPath repoPath) throws SQLException {
        String jsonDetails = bundlesDao.getComponentDetails(repoPath.toPath());
        if (jsonDetails == null) {
            log.debug("Original component details does not exist for repoPath {}", repoPath);
            return null;
        }
        return ComponentDetails.fromJson(jsonDetails);
    }

    @Override
    public List<BundleNameAndRepo> getAllCompletedBundles(@Nullable BundleType bundleType) {
        List<DBBundleResult> dbBundles;
        try {
            dbBundles = bundlesDao.getAllCompletedBundles(bundleType);
        } catch (SQLException e) {
            throw new org.artifactory.storage.StorageException("Could not retrieve the list of release-bundles", e);
        }
        List<BundleNameAndRepo> bundles = Lists.newArrayList();
        dbBundles.stream().map(dbBundle -> new BundleNameAndRepo(dbBundle.getName(), dbBundle.getStoringRepo()))
                .forEach(bundles::add);
        return bundles;
    }

    @Override
    public String getDefaultStoringRepo() {
        return "release-bundles";
    }

    public void deleteReleaseBundle(@NonNull String bundleName, @NonNull String bundleVersion,
            @NonNull BundleType type) {
        String errorMessage;
        try {
            DBArtifactsBundle artifactsBundle = bundlesDao.getArtifactsBundle(bundleName, bundleVersion, type);
            if (artifactsBundle == null) {
                throw new NotFoundException("Release bundle not found");
            }
            if (bridgeToModule.isRetentionFeatureEnabled()) {
                assertBundleIsNotMarkedAsKeep(artifactsBundle);
            }
            ReleaseBundleModel releaseBundleModel =
                    getReleaseBundleModelWithNameAndVersionOnly(bundleName, bundleVersion);

            if (authService.canDelete(
                    getBundleRepoPath(artifactsBundle.getStoringRepo(), artifactsBundle.getName(),
                            artifactsBundle.getVersion()))) {
                ArtifactsBundleModel artifactsBundleModel =
                        ReleaseBundleUtils.convertToArtifactsBundleModel(artifactsBundle);
                releaseBundleInterceptors.onDeleteReleaseBundleRequestStarted(releaseBundleModel);
                deleteBundle(artifactsBundleModel);
                releaseBundleInterceptors.onDeleteReleaseBundleRequestCompleted(releaseBundleModel);
            } else {
                errorMessage = "Delete release bundle " + bundleName + ":" + bundleVersion + " is forbidden";
                releaseBundleInterceptors.onDeleteReleaseBundleRequestFailed(
                        releaseBundleModel, errorMessage);
                throw new ForbiddenWebAppException(errorMessage);
            }
        } catch (StorageException e) { //failed in deleteBundle()
            releaseBundleInterceptors.onDeleteReleaseBundleRequestFailed(
                    getReleaseBundleModelWithNameAndVersionOnly(bundleName, bundleVersion), e.getMessage());
            throw e;
        } catch (SQLException e) { //failed in getArtifactsBundle()
            errorMessage = "Error deleting release bundle. See logs for further details";
            log.error("Caught exception while trying to read release bundle ", e);
            releaseBundleInterceptors.onDeleteReleaseBundleRequestFailed(
                    getReleaseBundleModelWithNameAndVersionOnly(bundleName, bundleVersion), errorMessage);
            throw new StorageException(errorMessage);
        }
    }

    private RepoPath getBundleRepoPath(String repo, String name, String version) {
        return RepoPathFactory.create(repo, name + "/" + version);
    }

    @NonNull
    private ReleaseBundleModel getReleaseBundleModelWithNameAndVersionOnly(@NonNull String bundleName,
            @NonNull String bundleVersion) {
        ReleaseBundleModel model = new ReleaseBundleModel();
        model.setName(bundleName);
        model.setVersion(bundleVersion);
        return model;
    }

    private void assertNoPathsWithoutDeletePermission(ArtifactsBundleModel bundleModel, List<BundleNode> bundleNodes) {
        // Check if the user has delete permission on all the bundle nodes
        List<String> pathsWithoutDeletePermission = bundleNodes.stream()
                .map(BundleNode::getRepoPath)
                .filter(repoPath -> !authService.canDelete(RepoPathFactory.create(repoPath)))
                .toList();
        if (!pathsWithoutDeletePermission.isEmpty()) {
            log.error("Delete release bundle {}:{} is forbidden for user: '{}'", bundleModel.getName(),
                    bundleModel.getVersion(), securityService.currentUsername());
            throw new ForbiddenWebAppException(
                    "Delete release bundle " + bundleModel.getName() + ":" + bundleModel.getVersion() +
                            " is forbidden for user: '" + securityService.currentUsername() +
                            "' because user does not have delete permission on the following paths: " +
                            pathsWithoutDeletePermission);
        }
    }

    private void copyArtifact(ReleaseArtifact artifact, String storingRepo, String bundlePrefix,
            Map<String, String> artifactMapping, ReleaseBundleModel bundleModel) throws SQLException {
        // target path is storingRepo/bundleName/bundleVersion/artifactPath
        String targetPath = bundlePrefix + '/' + artifact.getRepoPath();
        RepoPath target = RepoPathFactory.create(storingRepo, targetPath);
        String mapped = artifactMapping.get(artifact.getRepoPath());
        if (mapped == null) {
            String path = target.toPath();
            log.error("No artifact mapping provided for {}, can't determine source artifact", path);
            throw new IllegalArgumentException(
                    "No artifact mapping provided for " + path + ", can't determine source artifact");
        }
        RepoPath source = RepoPathFactory.create(mapped);
        log.trace("Copying {} to {}", source.getPath(), target.getPath());
        repoService.copy(source, target, false, true, false);
        setProperties(artifact.getProps(), target);
        try {
            ComponentDetails originalComponentDetails = componentDetailsFetcher.calcComponentDetails(source);
            createBundleNode(bundleModel.getBundleId(), target, originalComponentDetails);
        } catch (SQLException e) {
            log.error("Unable to add artifact '{}' to bundle {}", target.toPath(), bundlePrefix);
            throw e;
        }
    }

    private void setProperties(List<ArtifactProperty> propsToAttach, RepoPath targetPath) {
        Properties properties = new PropertiesImpl();
        propsToAttach.forEach(
                artifactProperty -> properties.putAll(artifactProperty.getKey(), artifactProperty.getValues()));
        if (propertiesService.setProperties(targetPath, properties, false)) {
            log.trace("Properties were set successfully on {}", targetPath);
        } else {
            log.error("Failed to set properties on {}", targetPath);
        }
    }

    @Override
    public void setBundleStatusWithRetries(@NonNull String bundleName, @NonNull String bundleVersion,
            @NonNull BundleTransactionStatus status, @NonNull BundleType type) {
        ExecutionUtils.RetryOptions options = ExecutionUtils.RetryOptions.builder()
                .timeout((int) initialTimeBetweenRetries)
                .backoffMaxDelay((int) (10 * initialTimeBetweenRetries))
                .exponentialBackoffMultiplier(2)
                .numberOfRetries(10)
                .build();

        ExecutionUtils.retry(() -> {
            try {
                return bundlesDao.tryToSetBundleStatus(bundleName, bundleVersion, type, status);
            } catch (SQLException e) {
                String errorMessage = String.format(
                        "Failed updating the status of Release Bundle %s/%s with type %s to %s. Reason: %s",
                        bundleName, bundleVersion, type, status.name(), e.getMessage());
                throw new RetryException(errorMessage, e);
            }
        }, options, executorService);
    }

    @Override
    public void cleanOrphanAndUnidentifiedArtifacts() {
        try {
            log.debug("Starting deleting orphan artifacts from _intransit repository");
            MoveMultiStatusHolder statusHolder = deleteOrphanRBVItems();
            statusHolder.merge(deleteUnidentifiedInTransitItems());
            if (statusHolder.hasErrors() || statusHolder.hasWarnings()) {
                log.warn("Errors/warnings were found while deleting orphan/unidentified items from " +
                        "_intransit repository. status message: {}", statusHolder.getStatusMsg());
            } else {
                log.info("Finished deleting orphan/unidentified items from _intransit repository");
            }
        } catch (Exception e) {
            log.error("Failed to delete orphan/unidentified items from _intransit repository", e);
        }
    }

    @Override
    public boolean getTargetBundleKeep(@NonNull String bundleName, @NonNull String bundleVersion) {
        try {
            return bundlesDao.isKeepBundle(bundleName, bundleVersion);
        } catch (SQLException e) {
            log.debug(e.getMessage(), e);
            log.error("Error while checking for target bundle {}/{} keep status. error: {}", bundleName, bundleVersion,
                    e.getMessage());
            throw new StorageException(e.getMessage());
        }
    }

    @Override
    public boolean setTargetBundleKeep(@NonNull String bundleName, @NonNull String bundleVersion, boolean keep) {
        try {
            int numUpdated = bundlesDao.setKeepBundle(bundleName, bundleVersion, keep);
            return numUpdated == 1;
        } catch (SQLException e) {
            log.debug(e.getMessage(), e);
            log.error("Error while setting target bundle {}/{} keep status. to {}. error: {}", bundleName,
                    bundleVersion, keep, e.getMessage());
            throw new StorageException(e.getMessage());
        }
    }

    @Override
    @Nullable
    public String getBundleSourceServiceId(@NonNull String bundleName, @NonNull String bundleVersion) {
        try {
            return bundlesDao.getSourceServiceId(bundleName, bundleVersion);
        } catch (SQLException e) {
            log.debug(e.getMessage(), e);
            log.error("Error while getting source service id for bundle {}/{}. error: {}", bundleName,
                    bundleVersion, e.getMessage());
            throw new StorageException(e.getMessage());
        }
    }

    @NonNull
    private MoveMultiStatusHolder deleteOrphanRBVItems() {
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        try {
            Set<RepoPath> repoPathsToDelete = bundlesDao.getOrphanRBVRepoPathsFromIntransit();
            if (repoPathsToDelete.isEmpty()) {
                log.debug("No orphan release bundle version folders were found on _intransit repository");
                return statusHolder;
            }
            statusHolder = repoService.delete(repoPathsToDelete, true, false);
            log.info("Deleted {} orphan items from _intransit repository", repoPathsToDelete.size());
        } catch (Exception e) {
            log.error("Failed to delete orphan items from _intransit repository", e);
        }
        return statusHolder;
    }

    @NonNull
    private MoveMultiStatusHolder deleteUnidentifiedInTransitItems() {
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        try {
            Set<RepoPath> repoPathsToDelete = bundlesDao.getUnidentifiedRepoPathsOnInTransit();
            if (repoPathsToDelete.isEmpty()) {
                log.debug("No unidentified items were found on _intransit repository");
                return statusHolder;
            }
            statusHolder = repoService.delete(repoPathsToDelete, true, false);
            log.info("Deleted {} unidentified items from _intransit repository", repoPathsToDelete.size());
        } catch (Exception e) {
            log.error("Failed to delete unidentified items from _intransit repository", e);
        }
        return statusHolder;
    }

    private void assertBundleIsNotMarkedAsKeep(@NonNull DBArtifactsBundle artifactsBundle) {
        if (Boolean.TRUE.equals(artifactsBundle.getKeep())) {
            throw ReleaseBundleDeletionException
                    .cannotDeleteBundlesMarkedAsKeep(artifactsBundle.getName(), artifactsBundle.getVersion());
        }
    }

    private void addMetricForBundleDeletion(@NonNull BundleType type) {
        String actor = CurrentActivity.ACTOR.get("unknown");
        Counter.builder("release.bundle.deletion")
                .description("Number of release bundles deleted by actor and type")
                .tags("actor", actor, "bundle_type", type.name())
                .register(this.metrics)
                .increment();
    }

    @NonNull
    private DBArtifactsBundle getDBArtifactsBundle(long bundleId) throws SQLException {
        return Optional.ofNullable(bundlesDao.getArtifactsBundle(bundleId))
                .orElseThrow(() -> new NotFoundException("Release bundle not found"));
    }

    private void reportBundleLocallyDeletedFromTarget(@NonNull DBArtifactsBundle artifactsBundle) {
        reportBundleLocallyDeletedFromTarget(artifactsBundle.getStoringRepo(), artifactsBundle.getName(),
                artifactsBundle.getVersion(), artifactsBundle.getSourceServiceId(), artifactsBundle.getSignature());
    }

    public void reportBundleLocallyDeletedFromTarget(@NonNull String storingRepo, @NonNull String releaseBundleName,
            @NonNull String releaseBundleVersion, @Nullable String sourceServiceId, @NonNull String signature) {
        if (!bridgeToModule.isActionsFeatureEnabled()) {
            log.debug("Target Bundle Actions feature is disabled, no bundles deleted at target are reported.");
            return;
        }
        String actor = CurrentActivity.ACTOR.get("unknown");
        if (!actor.equals(ReleaseBundleActors.DISTRIBUTION_SERVICE)) {
            String bundleSignatureChecksum = DigestUtils.sha256Hex(signature);
            TargetBundleActionsBaseModel targetBundleActionsBaseModel =
                    new TargetBundleActionsBaseModel(TargetBundleAction.DELETE_AT_TARGET,
                            releaseBundleName, releaseBundleVersion,
                            sourceServiceId, bundleSignatureChecksum,
                            securityService.currentUsername(), storingRepo);

            bridgeToModule.writeBundleLocallyDeletedFromTarget(targetBundleActionsBaseModel);
        }
    }
}
