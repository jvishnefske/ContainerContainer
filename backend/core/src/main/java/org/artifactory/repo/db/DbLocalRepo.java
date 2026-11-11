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

package org.artifactory.repo.db;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.SnapshotVersionBehaviorDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.io.NullResourceStreamHandle;
import org.artifactory.io.SimpleResourceStreamHandle;
import org.artifactory.io.checksum.policy.ChecksumPolicy;
import org.artifactory.io.checksum.policy.LocalRepoChecksumPolicy;
import org.artifactory.md.Properties;
import org.artifactory.model.LocalConfig;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepoBase;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.SaveResourceContext;
import org.artifactory.repo.db.importexport.DbRepoExportHandler;
import org.artifactory.repo.db.importexport.DbRepoImportHandler;
import org.artifactory.repo.local.LocalRepoChecksumPolicyType;
import org.artifactory.repo.local.PathDeletionContext;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.StoringRepoInitContext;
import org.artifactory.repo.snapshot.MavenSnapshotVersionAdapter;
import org.artifactory.repo.snapshot.SnapshotVersionAdapterBase;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.schedule.TaskCallback;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.MutableVfsFolder;
import org.artifactory.storage.fs.MutableVfsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.artifactory.mime.DockerNaming.MANIFEST_FILENAME;

/**
 * A local repository implementation backed by db.
 *
 * @author Yossi Shaul
 */
public class DbLocalRepo<T extends LocalRepoDescriptor, S extends LocalConfig>
        extends RealRepoBase<T, S> implements LocalRepo<T, S> {
    private static final Logger log = LoggerFactory.getLogger(DbLocalRepo.class);
    public static final String BYPASS_UNSCANNED_BLOCKING_HEADER = "X-JFrog-Bypass-Unscanned-Blocking";
    public static final String DOWNLOAD_BLOCK_REASON_MESSAGE = "download blocking policy configured in Xray";
    /*
     contains list of files for each package that should bypass unscanned blocking. In case two or more entries for
     one RepoType, two entries should be present in the list.
     */
    private static final List<BypassPackageFile> filesForBypassing = List.of(
            new BypassPackageFile(RepoType.Docker, MANIFEST_FILENAME),
            new BypassPackageFile(RepoType.OCI, MANIFEST_FILENAME),
            new BypassPackageFile(RepoType.HelmOCI, MANIFEST_FILENAME),
            new BypassPackageFile(RepoType.Conan, "conanmanifest.txt")
    );
    private static final List<BypassPackageFileExtension> extensionsForBypassing = List.of(
            new BypassPackageFileExtension(RepoType.Go, ".mod"),
            new BypassPackageFileExtension(RepoType.Go, ".info")
    );

    final DbStoringRepoMixin<LocalRepoDescriptor, LocalConfig> mixin;

    // For local non-cache repositories use the special local repo checksum policy
    private final LocalRepoChecksumPolicy checksumPolicy = new LocalRepoChecksumPolicy();

    private final MavenSnapshotVersionAdapter mavenSnapshotVersionAdapter;

    public DbLocalRepo(T descriptor, S repoConfig, InternalRepositoryService repositoryService,
            DbLocalRepo<T, S> oldLocalRepo, CentralConfigService configService) {
        super(descriptor, repoConfig, repositoryService, configService);
        DbStoringRepoMixin<LocalRepoDescriptor, LocalConfig> oldMixin =
                oldLocalRepo != null ? oldLocalRepo.mixin : null;
        mixin = new DbStoringRepoMixin<>(descriptor, repoConfig, oldMixin);
        // TODO: 09/03/2022 consider to align them as a part of this task, I don't see a real reason to have two
        LocalRepoChecksumPolicyType checksumPolicyType = repoConfig.getRepoTypeConfig().getChecksumPolicyType();
        org.artifactory.descriptor.repo.LocalRepoChecksumPolicyTypeDescriptor localRepoChecksumPolicyType =
                org.artifactory.descriptor.repo.LocalRepoChecksumPolicyTypeDescriptor
                        .valueOf(checksumPolicyType.name());
        checksumPolicy.setPolicyType(localRepoChecksumPolicyType);
        org.artifactory.repo.packagetype.SnapshotVersionBehavior snapshotVersionBehavior =
                repoConfig.getRepoTypeConfig().getSnapshotVersionBehavior();
        mavenSnapshotVersionAdapter =
                SnapshotVersionAdapterBase.getByType(
                        SnapshotVersionBehaviorDescriptor.valueOf(snapshotVersionBehavior.name()));
        patternExcludes = createPattern(excludes);
    }

    @Override
    public void init(boolean existenceValidated) throws StorageException {
        mixin.init(existenceValidated);
    }

    @Override
    public void init(@NonNull StoringRepoInitContext initContext, boolean existenceValidated) throws StorageException {
        mixin.init(initContext, existenceValidated);
    }

    @Override
    public RepoResource getInfo(InternalRequestContext context) throws FileExpectedException {
        final String path = context.getResourcePath();
        RepoPath repoPath = InternalRepoPathFactory.create(getKey(), path);
        RepoResource repoResource = checkResourceStatusBeforeDownload(context, repoPath);
        if (repoResource != null) {
            return repoResource;
        }
        RepoResource res = mixin.getInfo(context);
        return checkResourceStatusAfterDownload(context, res, repoPath);
    }

    @Override
    public ResourceStreamHandle getResourceStreamHandle(InternalRequestContext requestContext, RepoResource res)
            throws IOException {
        return mixin.getResourceStreamHandle(requestContext, res);
    }

    @Override
    public boolean itemExists(String relativePath) {
        return mixin.itemExists(relativePath);
    }

    @Override
    @NonNull
    public List<RepoPath> getExistingItems(@NonNull List<RepoPath> repoPaths) {
        return mixin.filterExistingRepoPaths(repoPaths);
    }

    @Override
    public String getKey() {
        return mixin.getKey();
    }

    @Override
    public InputStream getFileContent(String path) {
        RepoPath repoPath = RepoPathFactory.create(getKey(), path);
        try {
            VfsFile<?> file = mixin.getImmutableFile(repoPath);
            return file.getStream();
        } catch (FileExpectedException e) {
            log.error("Invalid request trying to get content from directory", e);
        } catch (Exception e) {
            log.error("Failed to retrieve content for for file in:" + repoPath, e);
        }
        return null;
    }

    @Override
    public boolean isWriteLocked(RepoPath repoPath) {
        return mixin.isWriteLocked(getRepoPath(repoPath.getPath()));
    }

    @NonNull
    @Override
    public VfsItem<ItemInfo> getImmutableFsItem(@NonNull ItemInfo itemInfo) {
        return mixin.getImmutableItem(itemInfo);
    }

    @Override
    public StatusHolder checkDownloadIsAllowed(RepoPath repoPath) {
        BasicStatusHolder status = assertValidPath(repoPath, true);
        if (status.isError()) {
            return status;
        }
        assertReadPermissions(repoPath, status);
        return status;
    }

    @Override
    public StatusHolder checkDownloadIsNotBlocked(RequestContext context, RepoPath repoPath) {
        XrayAddon xrayAddon = ContextHelper.get().beanForType(AddonsManager.class).addonByType(XrayAddon.class);
        boolean skipUnscanned = isDownloadWithBypassHeader(context, repoPath);
        boolean blocked = xrayAddon.isDownloadBlocked(repoPath, skipUnscanned);
        return blocked ? createErrorStatusHolder(repoPath, xrayAddon) : new BasicStatusHolder();
    }

    @VisibleForTesting
    boolean isDownloadWithBypassHeader(RequestContext context, RepoPath repoPath) {
        RepoType repoType = this.getDescriptor().getType();
        String fileName = repoPath.getName();
        boolean oneOfEntriesMatches = filesForBypassing.stream()
                .anyMatch(entry -> entry.packageType == repoType && entry.fileName.equals(fileName));
        if (!oneOfEntriesMatches) {
            oneOfEntriesMatches = extensionsForBypassing.stream()
                    .anyMatch(entry -> entry.packageType == repoType && fileName.endsWith(entry.extension));
        }
        if (!oneOfEntriesMatches) {
            return false;
        }
        Request request = context.getRequest();
        if (request == null) {
            return false;
        }
        Enumeration<String> values = request.getHeaderValues(BYPASS_UNSCANNED_BLOCKING_HEADER);
        if (values == null) {
            return false;
        }
        boolean headerValuesExists = values.hasMoreElements();
        if (!headerValuesExists) {
            return false;
        }
        String headerValue = values.nextElement();
        log.debug("Bypass header value for {} is value {}.", repoPath.getPath(), headerValue);
        return "true".equalsIgnoreCase(headerValue);
    }

    private StatusHolder createErrorStatusHolder(RepoPath repoPath, XrayAddon xrayAddon) {
        BasicStatusHolder status = new BasicStatusHolder();
        status.setActivateLogging(false);
        String message = String.format("Artifact download request rejected:" +
                        " %s was not downloaded due to the " + DOWNLOAD_BLOCK_REASON_MESSAGE + " for %s.",
                repoPath.getPath(), repoPath.getRepoKey());
        if (!xrayAddon.isXrayAliveByHeartbeat()) {
            message += " Xray is unavailable.";
        }
        log.debug(message);
        status.error(message, HttpStatus.SC_FORBIDDEN, log);
        return status;
    }

    @Override
    public RepoResource saveResource(SaveResourceContext context) throws RepoRejectException, IOException {
        return mixin.saveResource(context);
    }

    @Override
    public boolean shouldProtectPathDeletion(PathDeletionContext pathDeletionContext) {
        return mixin.shouldProtectPathDeletion(pathDeletionContext);
    }

    @Override
    public void importFrom(ImportSettings settings) {
        DbRepoImportHandler importHandler =
                new DbRepoImportHandler(this, settings, TaskCallback.currentTaskId(), "");
        importHandler.executeImport();
        importHandler.finalizeImport();
    }

    @Override
    public void exportTo(ExportSettings settings) {
        new DbRepoExportHandler(this, settings).export();
    }

    @Override
    public String getTextFileContent(RepoPath repoPath) {
        VfsFile file = mixin.getImmutableFile(repoPath);
        if (file != null) {
            InputStream is = null;
            try {
                is = file.getStream();
                return IOUtils.toString(is, Charsets.UTF_8.name());
            } catch (IOException e) {
                throw new RuntimeException("Could not read text file content from '" + repoPath + "'.", e);
            } finally {
                IOUtils.closeQuietly(is);
            }
        }
        return "";
    }

    @Override
    public ResourceStreamHandle getFileContent(RepoPath repoPath) {
        VfsFile file = mixin.getImmutableFile(repoPath);
        if (file != null) {
            InputStream is = file.getStream();
            return new SimpleResourceStreamHandle(is, file.length());
        } else {
            return new NullResourceStreamHandle();
        }
    }

    @Override
    public void undeploy(DeleteContext ctx) {
        undeploy(ctx, new BasicStatusHolder());
    }

    @Override
    public void undeploy(DeleteContext ctx, BasicStatusHolder statusHolder) {
        mixin.undeploy(ctx, statusHolder);
    }

    @Override
    public SnapshotVersionBehaviorDescriptor getMavenSnapshotVersionBehavior() {
        // TODO: 02/02/2022 replace?/remove duplicates
        return SnapshotVersionBehaviorDescriptor.valueOf(
                getRepoConfig().getRepoTypeConfig().getSnapshotVersionBehavior().name());
    }

    @Override
    public MavenSnapshotVersionAdapter getMavenSnapshotVersionAdapter() {
        return mavenSnapshotVersionAdapter;
    }

    @Override
    public ChecksumPolicy getChecksumPolicy() {
        return checksumPolicy;
    }

    @Override
    public void close() {
        // nothing to close
    }

    @Override
    @Nullable
    public MutableVfsItem getMutableFsItem(RepoPath repoPath) {
        return mixin.getMutableItem(repoPath);
    }

    @Override
    @Nullable
    public MutableVfsItem<MutableItemInfo> getMutableFsItem(VfsItem<ItemInfo> vfsItem, ItemInfo originalItemInfo,
            Properties originalProperties) {
        return mixin.getMutableItem(vfsItem, originalItemInfo, originalProperties);
    }

    @Override
    @Nullable
    public MutableVfsFile getMutableFile(RepoPath repoPath) {
        return mixin.getMutableFile(repoPath);
    }

    @Override
    @NonNull
    public MutableVfsFile createOrGetFile(RepoPath repoPath) {
        return mixin.createOrGetFile(repoPath);
    }

    @Override
    @Nullable
    public MutableVfsFolder getMutableFolder(RepoPath repoPath) {
        return mixin.getMutableFolder(repoPath);
    }

    @Override
    @NonNull
    public MutableVfsFolder createOrGetFolder(RepoPath repoPath) {
        return mixin.createOrGetFolder(repoPath);
    }

    @Override
    @Nullable
    public VfsItem getImmutableFsItem(RepoPath repoPath) {
        return mixin.getImmutableItem(repoPath);
    }

    @Override
    @NonNull
    public Map<RepoPath, Optional<VfsItem<ItemInfo>>> getImmutableFsItems(@NonNull List<RepoPath> repoPaths) {
        return mixin.getImmutableItems(repoPaths);
    }

    @Override
    @Nullable
    public VfsFile getImmutableFile(RepoPath repoPath) {
        return mixin.getImmutableFile(repoPath);
    }

    @Override
    public VfsFolder getImmutableFolder(RepoPath repoPath) {
        return mixin.getImmutableFolder(repoPath);
    }

    private record BypassPackageFile(RepoType packageType, String fileName) {
    }

    private record BypassPackageFileExtension(RepoType packageType, String extension) {
    }
}
