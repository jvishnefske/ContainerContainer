package org.artifactory.ph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jfrog.ph.acl.exception.PackageCancelException;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageRuntimeException;
import com.jfrog.ph.acl.exception.PackageVerificationException;
import com.jfrog.ph.acl.listener.artifact.PackageArtifactEventListener;
import com.jfrog.ph.acl.listener.artifact.PackageArtifactEventListenerContext;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpRequestBase;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.rest.restmodel.JsonUtil;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.repo.PackageType;
import org.artifactory.event.EventHintType;
import org.artifactory.exception.CancelException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.repo.remote.interceptor.RemoteRepoInterceptor;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.DownloadRequestContext;
import org.artifactory.request.HeadersMultiMap;
import org.artifactory.request.Request;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.util.StringInputStream;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;

@Slf4j
public class PackageStorageInterceptorImpl extends StorageInterceptorAdapter implements RemoteRepoInterceptor {

    private final PackageSpecificationAdapter packageSpecificationAdapter;
    private final PackageArtifactEventListener packagesListener;
    private final InternalRepositoryService repositoryService;

    public PackageStorageInterceptorImpl(PackageArtifactEventListener packagesListener,
            InternalRepositoryService repositoryService, PackageSpecificationAdapter packageSpecificationAdapter) {
        this.packagesListener = packagesListener;
        this.repositoryService = repositoryService;
        this.packageSpecificationAdapter = packageSpecificationAdapter;
    }

    @Override
    public void beforeCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder) {
        if (fsItem.isFolder()) {
            return;
        }
        try {
            if (packageSpecificationAdapter.isSupportedPackage(fsItem.getRepoKey())) {
                RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(fsItem.getRepoKey());
                if (repoConfig instanceof LocalConfig localConfig && !localConfig.isCache()) {
                    markOriginalPropertiesIfNeeded(fsItem);
                    PackageArtifact artifact = getArtifact(fsItem);
                    if (artifact != null) {
                        packagesListener.beforeCreate(artifact, repoConfig.getPackageTypeConfig());
                    }
                }
            }
        } catch (Exception e) {
            error(e, "beforeCreate", fsItem.getRepoPath());
        }
    }

    @Override
    public void afterCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder,
            InterceptorCreateContext ctx) {
        if (fsItem.isFolder()) {
            return;
        }
        try {
            if (packageSpecificationAdapter.isSupportedPackage(fsItem.getRepoKey())) {
                PackageArtifact artifact = getArtifact(fsItem);
                if (artifact == null) {
                    return;
                }
                RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(artifact.getRepoKey());
                if (!(repoConfig instanceof LocalConfig localConfig)) {
                    return;
                }
                if (!localConfig.isCache()) {
                    packagesListener.afterCreate(artifact);
                    return;
                }
                if (packageSpecificationAdapter.shouldVerifyRemoteArtifact(artifact) &&
                        fsItem instanceof VfsFile fsFile) {
                    try (InputStream is = fsFile.getStream()) {
                        packageSpecificationAdapter.verifyRemoteArtifactNotCorrupted(artifact, is);
                    }
                }
            }
        } catch (PackageVerificationException e) {
            error(e, "afterCreate", fsItem.getRepoPath());
            statusHolder.error(e.getInternalReason(), e.getStatus(), e, log);
            throw new PackageRuntimeException(e.getStatus(), e.getPublicReason(), e.getInternalReason(), e);
        } catch (Exception e) {
            error(e, "afterCreate", fsItem.getRepoPath());
        }
    }

    @Override
    public void beforeDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (fsItem.isFolder()) {
            return;
        }
        try {
            if (packageSpecificationAdapter.isSupportedPackage(fsItem.getRepoKey())) {
                RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(fsItem.getRepoKey());
                if (repoConfig instanceof LocalConfig localConfig && !localConfig.isCache()) {
                    PackageArtifactEventListenerContext context = new PackageArtifactEventListenerContext();
                    markOriginalPropertiesIfNeeded(fsItem);
                    packagesListener.beforeDelete(fsItem.getRepoKey(), fsItem.getPath(), context);
                    storeHints(fsItem.getRepoKey(), fsItem.getPath(), context);
                }
            }
        } catch (Exception e) {
            error(e, "beforeDelete", fsItem.getRepoPath());
        }
    }

    @Override
    public void afterDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (fsItem.isFolder() || ctx.isTriggeredByMove()) {
            return;
        }
        try {
            if (packageSpecificationAdapter.isSupportedPackage(fsItem.getRepoKey())) {
                PackageArtifact artifact = getArtifact(fsItem);
                if (artifact != null) {
                    RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(artifact.getRepoKey());
                    if (repoConfig instanceof LocalConfig localConfig && !localConfig.isCache()) {
                        packagesListener.afterDelete(artifact);
                    }
                }
            }
        } catch (Exception e) {
            error(e, "afterDelete", fsItem.getRepoPath());
        }
    }

    @Override
    public CopyMoveCheckResult checkCopyMove(VfsItem<? extends ItemInfo> sourceItem,
            RepoPath targetRepoPath, MutableStatusHolder status) {
        if (!sourceItem.isFolder() && packageSpecificationAdapter.isSupportedPackage(targetRepoPath.getRepoKey())) {
            PackageArtifact artifact = getArtifact(sourceItem);
            if (artifact != null) {
                try {
                    return packagesListener.isCopyOrMoveAllowed(artifact, targetRepoPath.getRepoKey(),
                            targetRepoPath.getPath()) ? ALLOW : REJECT;
                } catch (PackageCancelException e) {
                    status.error(e.getMessage(), e.getStatus(), e, log);
                    return REJECT;
                }
            }
        }
        return ALLOW;
    }

    //We only need to check if the target path is an acceptable location to copy or move into.
    // We would like to provide customers with the option to copy or move content to other folders of different types.
    @Override
    public CopyMoveCheckResult checkDirCopyMove(VfsItem<? extends ItemInfo> sourceItem,
            RepoPath targetRepoPath, MutableStatusHolder status) {
        if (sourceItem.isFolder() && packageSpecificationAdapter.isSupportedPackage(targetRepoPath.getRepoKey())) {
            try {
                return packagesListener.isDirCopyOrMoveAllowed(targetRepoPath.getRepoKey(),
                        targetRepoPath.getPath()) ? ALLOW : REJECT;
            } catch (PackageCancelException e) {
                status.warn(e.getMessage(), e.getStatus(), e, log);
                return REJECT;
            }
        }
        return ALLOW;
    }

    @Override
    public void afterMove(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        if (sourceItem == null || targetItem == null || sourceItem.isFolder()) {
            return;
        }
        try {
            RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(targetItem.getRepoKey());
            if (repoConfig instanceof LocalConfig localConfig && !localConfig.isCache() &&
                    (packageSpecificationAdapter.isSupportedPackage(sourceItem.getRepoKey()) ||
                            packageSpecificationAdapter.isSupportedPackage(targetItem.getRepoKey()))) {
                PackageArtifact source = getArtifact(sourceItem);
                PackageArtifact target = getArtifact(targetItem);
                packagesListener.afterMove(source, target);
            }
        } catch (Exception e) {
            error(e, "afterMove", sourceItem.getRepoPath());
        }
    }

    @Override
    public void afterCopy(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        if (sourceItem == null || targetItem == null || sourceItem.isFolder()) {
            return;
        }
        try {
            RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(targetItem.getRepoKey());
            if (repoConfig instanceof LocalConfig localConfig && !localConfig.isCache() &&
                    packageSpecificationAdapter.isSupportedPackage(targetItem.getRepoKey())) {
                PackageArtifact target = getArtifact(targetItem);
                PackageArtifact source = getArtifact(sourceItem);
                packagesListener.afterCopy(source, target);
            }
        } catch (Exception e) {
            error(e, "afterCopy", sourceItem.getRepoPath());
        }
    }

    @Override
    public void beforePropertyCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key,
            String... values) {
        if (fsItem.isFolder()) {
            return;
        }
        try {
            if (packageSpecificationAdapter.isSupportedPackage(fsItem.getRepoKey())) {
                RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(fsItem.getRepoKey());
                if (repoConfig instanceof LocalConfig localConfig && !localConfig.isCache()) {
                    markOriginalPropertiesIfNeeded(fsItem);
                }
            }
        } catch (Exception e) {
            error(e, "beforePropertyCreate", fsItem.getRepoPath());
        }
    }

    @Override
    public void afterPropertyCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key,
            String... values) {
        if (fsItem.isFolder()) {
            return;
        }
        try {
            if (packageSpecificationAdapter.isSupportedPackage(fsItem.getRepoKey())) {
                PackageArtifact artifact = getArtifact(fsItem);
                if (artifact != null) {
                    RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(artifact.getRepoKey());
                    if (repoConfig instanceof LocalConfig localConfig && !localConfig.isCache()) {
                        packagesListener.afterPropertyChange(artifact, key, false, values);
                    }
                }
            }
        } catch (Exception e) {
            error(e, "afterPropertyCreate", fsItem.getRepoPath());
        }
    }

    @Override
    public void beforePropertyDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key) {
        try {
            if (fsItem.isFolder()) {
                return;
            }
            if (packageSpecificationAdapter.isSupportedPackage(fsItem.getRepoKey())) {
                RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(fsItem.getRepoKey());
                if (repoConfig instanceof LocalConfig localConfig && !localConfig.isCache()) {
                    markOriginalPropertiesIfNeeded(fsItem);
                }
            }
        } catch (Exception e) {
            error(e, "beforePropertyDelete", fsItem.getRepoPath());
        }
    }

    @Override
    public void afterPropertyDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key) {
        try {
            if (fsItem.isFolder()) {
                return;
            }
            if (packageSpecificationAdapter.isSupportedPackage(fsItem.getRepoKey())) {
                PackageArtifact artifact = getArtifact(fsItem);
                if (artifact != null) {
                    RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(artifact.getRepoKey());
                    if (repoConfig instanceof LocalConfig localConfig && !localConfig.isCache()) {
                        packagesListener.afterPropertyChange(artifact, key, true);
                    }
                }
            }
        } catch (Exception e) {
            error(e, "afterPropertyDelete", fsItem.getRepoPath());
        }
    }

    @Override
    public void afterRemoteDownload(RepoResource remoteResource) throws RepoRejectException {
        try {
            if (packageSpecificationAdapter.isSupportedPackage(remoteResource.getRepoKey())) {
                PackageArtifact artifact = getArtifact(remoteResource);
                if (artifact != null && repositoryService.remoteRepositoryByKey(artifact.getRepoKey()) != null) {
                    packagesListener.afterRemoteDownload(artifact);
                }
            }
        } catch (Exception e) {
            error(e, "afterRemoteDownload", remoteResource.getRepoPath());
        }
    }

    @Override
    public void beforeRemoteHttpMethodExecution(HttpRequestBase outgoingRequest, @Nullable Request incomingRequest,
            HeadersMultiMap headers, RepoPath repoPath) {
        try {
            String repoKey = repoPath.getRepoKey();
            if (packageSpecificationAdapter.isSupportedPackage(repoKey)) {
                DownloadRequestContext downloadRequestContext = incomingRequest != null
                        ? new DownloadRequestContext((ArtifactoryRequest) incomingRequest)
                        : null;
                if (repositoryService.remoteRepositoryByKey(repoKey) != null) {
                    packagesListener.beforeRemoteHttpMethodExecution(outgoingRequest, downloadRequestContext,
                            headers.getMultiHeaders(), getPackageType(repoKey), repoKey, repoPath.getPath());
                }
            }
        } catch (Exception e) {
            error(e, "beforeRemoteHttpMethodExecution", repoPath);
        }
    }

    @Nullable
    private PackageArtifact getArtifact(VfsItem<? extends ItemInfo> fsItem) {
        String repoKey = fsItem.getRepoKey();
        PackageType packageType = getPackageType(repoKey);
        if (packageType == null) {
            return null;
        }
        return PackageArtifactMapper.artifact(fsItem, packageType);
    }

    @Nullable
    private PackageArtifact getArtifact(RepoResource remoteResource) {
        String repoKey = remoteResource.getRepoKey();
        PackageType packageType = getPackageType(repoKey);
        if (packageType == null) {
            return null;
        }
        return PackageArtifactMapper.artifact(remoteResource, packageType);
    }

    @Nullable
    private PackageType getPackageType(String repoKey) {
        return packageType(getRepo(repoKey));
    }

    @Nullable
    private RepoConfig getRepo(String repoKey) {
        RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(repoKey);
        if (repoConfig == null) {
            log.error("Failed to identify repo for key: {} ", repoKey);
        }
        return repoConfig;
    }

    private void storeHints(String repoKey, String path, PackageArtifactEventListenerContext context) {
        if (MapUtils.isNotEmpty(context.getEventExtraInfo())) {
            //If we already have hint value for this hint, we should load it and add the new hints
            RepoPath key = RepoPathFactory.create(repoKey, path);
            Map<String, Object> extraInfo = null;
            if (EventLogHintManager.contains(EventHintType.PackageIndexExtraInfo, key)) {
                String hintValue =
                        EventHintType.getValue(EventLogHintManager.getHints(key), EventHintType.PackageIndexExtraInfo);
                if (StringUtils.isNotEmpty(hintValue)) {
                    try {
                        extraInfo = JsonUtil.mapDataToModel(new StringInputStream(hintValue), new TypeReference<>() {
                        });
                    } catch (Exception e) {
                        log.error("Failed to deserialize hint: {}, value: {}",
                                EventHintType.PackageIndexExtraInfo.name(), hintValue, e);
                    }
                }
            }

            if (extraInfo == null) {
                extraInfo = new HashMap<>(context.getEventExtraInfo().size());
            }

            extraInfo.putAll(context.getEventExtraInfo());
            String hintValue = JsonUtil.jsonToString(extraInfo);
            EventLogHintManager.put(EventHintType.PackageIndexExtraInfo, key, hintValue);
            log.debug("Event hint {} added on {}/{}: {}", EventHintType.PackageIndexExtraInfo.name(),
                    repoKey, path, hintValue);

        }
    }

    private PackageType packageType(@Nullable RepoConfig repoConfig) {
        try {
            if (repoConfig == null) {
                return null;
            }
            return PackageType.valueOf(repoConfig.getPackageType().name().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void error(Exception e, String type, RepoPath repoPath) {
        if (e instanceof PackageCancelException pce) {
            log.debug("Canceled {} event on {}: {}, {}, {}, {}", type, repoPath.toPath(),
                    pce.getStatus(), pce.getInternalReason(), pce.getPublicReason(), e.getMessage(), e);
            throw new CancelException(pce.getMessage(), pce, pce.getStatus());
        } else if (e instanceof PackageException pe) {
            log.error("Failed to handle {} event on {}: {}, {}, {}, {}", type, repoPath.toPath(),
                    pe.getStatus(), pe.getInternalReason(), pe.getPublicReason(), e.getMessage(), e);
        } else {
            log.error("Failed to handle {} event on {}: {}", type, repoPath.toPath(), e.getMessage(), e);
        }
    }

    /**
     * Save (if needed) file's current properties (before they get overridden)as additional snapshot for later usage in
     * the
     * afterCreate/afterPropertyChange callback
     */
    private void markOriginalPropertiesIfNeeded(VfsItem<? extends ItemInfo> fsItem) {
        if (fsItem instanceof MutableVfsFile mutableVfsFile && !mutableVfsFile.isOriginallyNew() &&
                packageSpecificationAdapter.shouldMarkOriginalPropertiesInBeforeEvents(fsItem.getRepoKey(),
                        fsItem.getPath())) {
            mutableVfsFile.markOriginalProperties();
        }
    }
}
