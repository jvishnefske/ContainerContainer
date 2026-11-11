package org.artifactory.ph.curation;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.ph.acl.exception.PackageBlockedByCurationException;
import com.jfrog.ph.acl.exception.PackageCurationException;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageExpiredException;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.model.PackageDownloadContext;
import com.jfrog.ph.acl.model.PackageUploadContextImpl;
import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import com.jfrog.ph.acl.service.PackageArtifactService;
import com.jfrog.ph.acl.service.PackageCurationService;
import com.jfrog.ph.acl.service.PackageRepoService;
import com.jfrog.ph.acl.service.PackageSecurityService;
import com.jfrog.ph.acl.service.PackageUploadService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.curation.packages.PackageVersionCurationController;
import org.artifactory.addon.curation.packages.PackagesCurationCompliantVersionService;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.guard.ConflictsGuardProvider;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.curation.gems.GemsPackageVersionCurationController;
import org.artifactory.maven.curation.GAVCPackageVersionCurationController;
import org.artifactory.repo.RemoteRepoBase;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.resource.FileResource;
import org.artifactory.resource.ResolvedResource;
import org.jfrog.common.concurrency.ConflictsGuard;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.artifactory.ph.curation.PackagesCurationCompliantVersionUtil.FORBIDDEN_MESSAGE_PROPERTY;
import static org.artifactory.ph.curation.PackagesCurationCompliantVersionUtil.getDefaultForbiddenMessage;
import static org.artifactory.ph.curation.PackagesCurationCompliantVersionUtil.CurationLockKind;
import static org.artifactory.ph.curation.PackagesCurationCompliantVersionUtil.getForbiddenMarkerPath;
import static org.artifactory.curation.CurationPassThroughUtils.isPassThroughHeaderPresent;

/**
 * Compliant-version (curated index) metadata handling per repository package type.
 * <p>
 * Maven-metadata-based curation is shared by the Maven repository family.
 * {@link #getPackageVersionCurationController(RepoType)} lists which {@link RepoType}s use the GAVC controller today;
 * to enable Ivy, add {@link RepoType#Ivy} to that {@code switch} (mapping is already defined in
 * {@link #mavenMetadataRepoTypeToCurationPackageType(RepoType)}).
 *
 * @author Ron Malka
 */

@Slf4j
public class PackagesCurationCompliantVersionServiceImpl<T extends PackagesCurationMetadataWrapper>
        implements PackagesCurationCompliantVersionService {

    private final PackageCurationService packageCurationService;
    private final PackageArtifactService packageArtifactService;
    private final PackageUploadService packageUploadService;
    private final PackageRepoService packageRepoService;
    private final PackageSecurityService packageSecurityService;
    private final ConflictsGuardProvider<String> conflictsGuardProvider;

    private static final String PACKAGE_METADATA_CURATION_LOCK = "PACKAGE-METADATA-CURATION";
    private static final byte[] EMPTY_MARKER_CONTENT = new byte[0];

    public PackagesCurationCompliantVersionServiceImpl(PackageCurationService packageCurationService,
            PackageArtifactService packageArtifactService, PackageUploadService packageUploadService,
            PackageRepoService packageRepoService, PackageSecurityService packageSecurityService,
            ConflictsGuardProvider<String> conflictsGuardProvider) {
        this.packageCurationService = packageCurationService;
        this.packageArtifactService = packageArtifactService;
        this.packageUploadService = packageUploadService;
        this.packageRepoService = packageRepoService;
        this.packageSecurityService = packageSecurityService;
        this.conflictsGuardProvider = conflictsGuardProvider;

    }

    @VisibleForTesting
    PackageVersionCurationController<T> getPackageVersionCurationController(RepoType repoType) {
        return switch (repoType) {
            case Maven, Gradle, SBT -> createGAVCController(mavenMetadataRepoTypeToCurationPackageType(repoType));
            case Gems -> createGemsController();
            default -> {
                log.debug("No curation compliant version controller available for package type: {}", repoType);
                yield null;
            }
        };
    }

    /**
     * {@link PackageType} passed to Xray curation for maven-metadata compliant-version flows.
     * Must stay aligned with {@link #getPackageVersionCurationController(RepoType)} multi-case labels for
     * {@link RepoType#Maven}, {@link RepoType#Gradle}, and {@link RepoType#SBT}.
     */
    @VisibleForTesting
    static PackageType mavenMetadataRepoTypeToCurationPackageType(RepoType repoType) {
        return switch (repoType) {
            case Maven -> PackageType.MAVEN;
            case Gradle -> PackageType.GRADLE;
            case SBT -> PackageType.SBT;
            default -> throw new IllegalArgumentException(
                    "Unsupported maven-metadata curation repo type: " + repoType + ". Add mapping in "
                            + "getPackageVersionCurationController and mavenMetadataRepoTypeToCurationPackageType.");
        };
    }

    @SuppressWarnings("unchecked")
    public PackageVersionCurationController<T> createGAVCController(PackageType curationPackageType) {
        GAVCPackageVersionCurationController controller =
                new GAVCPackageVersionCurationController(packageSecurityService, packageArtifactService,
                        curationPackageType);
        return (PackageVersionCurationController<T>) controller;
    }

    @SuppressWarnings("unchecked")
    public PackageVersionCurationController<T> createGemsController() {
        GemsPackageVersionCurationController controller =
                new GemsPackageVersionCurationController(packageSecurityService, packageArtifactService);
        return (PackageVersionCurationController<T>) controller;
    }


    private RepoResource createCuratedMetadataAndRepoResource(PackageVersionCurationController<T> controller,
            String remoteUrl, InternalRequestContext requestContext, RemoteRepoBase<?, ?> repo, RepoResource res,
            boolean passThrough)
            throws PackageException {
        String repoKey = PackagesCurationCompliantVersionUtil.getRepoKeyWithoutCache(res.getRepoKey(),
                repo.getRepositoryService());
        String curatePath = controller.getCuratedFilePath(new PackageDownloadContext(repoKey,
                res.getPath()));
        String curatedLockKey = PackagesCurationCompliantVersionUtil.getCuratedLockKey(repoKey, curatePath,
                passThrough ? CurationLockKind.PASS_THROUGH : CurationLockKind.BUILD);
        ConflictsGuard<String> conflictsGuard =
                conflictsGuardProvider.getConflictsGuard(PACKAGE_METADATA_CURATION_LOCK);
        int curatedLockTimeoutSec = ConstantValues.curatedVersionsCacheLockPeriodSeconds.getInt();
        boolean locked = false;
        try {
            log.debug("Trying to lock on curated key: {}", curatedLockKey);
            locked = conflictsGuard.tryToLock(curatedLockKey, curatedLockTimeoutSec, TimeUnit.SECONDS);
            if (locked) {
                return handleCuratedMetadataCreation(controller, curatePath, remoteUrl, requestContext, repo, res,
                        passThrough);
            } else {
                log.debug("Failed to lock on curated key: {} after {} seconds, returning original package stream",
                        curatedLockKey, curatedLockTimeoutSec);
            }
        } catch (PackageBlockedByCurationException e) {
            throw e;
        } catch (InterruptedException e) {
            log.debug("Interrupted while trying to lock on curated key: {}", curatedLockKey, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("There was an issue while trying to handle the curated file path: {}, with a message: {}",
                    curatePath, e.getMessage());
            log.debug("", e);
        } finally {
            try {
                if (locked) {
                    conflictsGuard.unlock(curatedLockKey);
                    log.debug("Unlocking curated file path: {}", curatedLockKey);
                }
            } catch (Exception e) {
                log.error("Failed to unlock curated file path: {}", curatedLockKey, e);
            }
        }
        // Should not get here
        throw new PackageCurationException(
                String.format("Failed to create curated metadata for repo: %s, path: %s", repoKey, curatePath));
    }

    private RepoResource handleCuratedMetadataCreation(PackageVersionCurationController<T> controller,
            String curatePath, String remoteUrl, InternalRequestContext requestContext, RemoteRepoBase<?, ?> repo,
            RepoResource res, boolean passThrough)
            throws RepoRejectException, IOException, PackageCurationException, PackageNotFoundException {
        String repoKey = PackagesCurationCompliantVersionUtil.getRepoKeyWithoutCache(res.getRepoKey(),
                repo.getRepositoryService());
        String remoteCacheRepoKey = packageRepoService.getRemoteCacheRepoKey(repoKey);
        if (!passThrough) {
            throwIfForbiddenMarkerPresent(controller, remoteCacheRepoKey, curatePath, res.getPath());

            RepoResource cachedCuratedRepoResource = getResourceFromCache(repoKey, curatePath);
            if (cachedCuratedRepoResource != null) {
                return cachedCuratedRepoResource;
            }
        }
        // If we reach here, it means the curated file does not exist or expired,
        // and we got the cluster lock to build it.
        log.info("Building compliant version metadata for repo: {}, path: {}", repoKey, curatePath);
        T curatedMetadataWrapper;
        try (InputStream originPkgStream = repo.getResourceStreamHandle(requestContext, res).getInputStream()) {
            T originMetadataWrapper = controller.getMetadataModelOrigin(originPkgStream);
            CurationRequest curationRequest =
                    controller.createMetadataCurationRequest(originMetadataWrapper, repoKey, remoteUrl);
            if (isPassThroughHeaderPresent(requestContext.getRequestHeaders())) {
                curationRequest = curationRequest.withPassThrough(true);
            }
            CurationResponse curationResponse = getCurationResponse(curatePath, repoKey, curationRequest);
            if (!PackagesCurationCompliantVersionUtil.verifyCurationResponse(curationResponse)) {
                if (curationResponse == null) {
                    log.debug("Curation response is null for repo: {}, path: {}, returning original package stream",
                            repoKey, curatePath);
                    return null;
                }
                curationRequest.packages().forEach(pkg -> log.info(
                        "Curation request for package: {} contains {} versions to check but there are no version " +
                                "to block, caching the origin metadata as the compliant version",
                        pkg.packageName(), pkg.versions().size()));
                if (passThrough) {
                    return null;
                }
                return saveInCacheAndReturnResource(controller, repoKey, curatePath, originMetadataWrapper, repo);
            }

            curationRequest.packages().forEach(pkg -> curationResponse.blockedPackages().stream()
                    .filter(blockedPkg -> pkg.packageName().equals(blockedPkg.packageName()))
                    .findFirst().ifPresent(blockedPackage ->
                            log.info(
                                    "Curation request for package: {} contains {} versions to check and received {} " +
                                            "versions to block",
                                    pkg.packageName(), pkg.versions().size(),
                                    blockedPackage.versions().size())));

            curatedMetadataWrapper = controller.getCuratedMetadataModel(curationResponse, originMetadataWrapper);
        } catch (PackageBlockedByCurationException e) {
            if (!passThrough) {
                saveForbiddenMarker(repoKey, curatePath, res.getPath(), e.getPublicReason());
            }
            throw e;
        } catch (PackageException e) {
            log.warn("Curation compliant version failed for repo: {}, path: {} with an error: {}", repoKey, curatePath,
                    e.getMessage());
            log.debug("", e);
            return null;
        }
        if (passThrough) {
            return returnPassThroughCuratedResource(controller, curatedMetadataWrapper, res);
        }
        return saveInCacheAndReturnResource(controller, repoKey, curatePath, curatedMetadataWrapper, repo);
    }

    private RepoResource returnPassThroughCuratedResource(PackageVersionCurationController<T> controller,
            T curatedMetadata, RepoResource originalResource) throws IOException, PackageCurationException {
        try (InputStream curatedMetadataStream = controller.toInputStream(curatedMetadata)) {
            String content = new String(curatedMetadataStream.readAllBytes(), StandardCharsets.UTF_8);
            return new ResolvedResource(originalResource, content);
        }
    }

    private CurationResponse getCurationResponse(String curatePath, String repoKey, CurationRequest curationRequest) {
        CurationResponse curationResponse;
        try {
            log.debug("Executing curation filter blocked versions request for repo: {}, path: {}", repoKey,
                    curatePath);
            long startTime = System.currentTimeMillis();
            curationResponse = packageCurationService.filterBlockedVersions(curationRequest);
            log.debug("Curation compliant version request for repo: {}, path: {} took {} ms", repoKey, curatePath,
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.warn("Curation compliant version request failed for repo: {}, path: {} with an error: {}", repoKey,
                    curatePath, e.getMessage());
            log.debug("", e);
            return null;
        }

        return curationResponse;
    }

    @Nullable
    private RepoResource saveInCacheAndReturnResource(PackageVersionCurationController<T> controller, String repoKey,
            String curatePath, T curatedMetadata, RemoteRepoBase<?, ?> repo) throws IOException {
        log.debug("Got curated metadata for repo: {}, path: {}, trying to save it.", repoKey, curatePath);
        removeForbiddenMarker(repoKey, curatePath);
        try (InputStream curatedMetadataStream = controller.toInputStream(curatedMetadata)) {
            packageUploadService.uploadToVirtualOrRemoteCache(repoKey, curatePath, curatedMetadataStream);
            return createFileResource(repo, repoKey, curatePath);
        } catch (PackageException e) {
            log.warn("Failed to save compliant version metadata for repo: {}, path: {} with an error: {}", repoKey,
                    curatePath, e.getMessage());
            log.debug("", e);
        }
        return null;
    }

    @VisibleForTesting
    RepoResource createFileResource(RemoteRepoBase<?, ?> repo, String repoKey, String curatePath)
            throws PackageNotFoundException {
        try {
            FileInfo fileInfo = repo.getRepositoryService()
                    .getFileInfo(RepoPathFactory.create(packageRepoService.getRemoteCacheRepoKey(repoKey), curatePath));
            return new FileResource(fileInfo);
        } catch (ItemNotFoundRuntimeException | FileExpectedException e) {
            log.warn(
                    "Failed to find or create the file for compliant version metadata resource for repo: {}, path: {}" +
                            " with an error: {}",
                    repoKey, curatePath, e.getMessage());
            log.debug("", e);
        }
        return null;
    }

    @Override
    public boolean canCallFilterBlockedVersions(RepoResource res, RemoteRepoBase<?, ?> remoteRepo) {
        PackageVersionCurationController<T> controller =
                getPackageVersionCurationController(remoteRepo.getRepoConfig().getPackageType());

        if (controller == null || !controller.shouldCheckForCuration(res.getPath())) {
            log.debug("No curation compliant version controller available for package type: {} or " +
                            "curation check is not required for path: {}",
                    remoteRepo.getRepoConfig().getPackageType(), res.getPath());
            return false;
        }

        if (!packageCurationService.canCallFilterBlockedVersions(
                PackagesCurationCompliantVersionUtil.getRepoKeyWithoutCache(res.getRepoKey(),
                        remoteRepo.getRepositoryService()))) {
            log.debug("Curation compliant version is disabled for repo: {}", res.getRepoKey());
            return false;
        }

        if (!remoteRepo.isStoreArtifactsLocally()) {
            log.warn("Repository {} is not configured to store artifacts locally. " +
                    "Skipping curation compliant version check.", remoteRepo.getKey());
            return false;
        }

        return true;
    }

    @Nullable
    @Override
    public RepoResource getCuratedMetadataResource(InternalRequestContext requestContext, RemoteRepoBase<?, ?> repo,
            RepoResource res) throws PackageBlockedByCurationException {
        if (!res.isFound()) {
            return null;
        }
        PackageVersionCurationController<T> controller =
                getPackageVersionCurationController(repo.getRepoConfig().getPackageType());
        if (controller == null) {
            return null;
        }

        try {
            String repoKey = PackagesCurationCompliantVersionUtil.getRepoKeyWithoutCache(res.getRepoKey(),
                    repo.getRepositoryService());
            boolean passThrough = isPassThroughHeaderPresent(requestContext.getRequestHeaders());
            String remoteUrl = PackagesCurationCompliantVersionUtil.extractRemoteUrl(repoKey, packageRepoService);
            if (StringUtils.isBlank(remoteUrl)) {
                return null;
            }
            remoteUrl = remoteUrl + res.getPath();
            String curatePath = controller.getCuratedFilePath(new PackageDownloadContext(repoKey,
                    res.getPath()));
            String remoteCacheRepoKey = packageRepoService.getRemoteCacheRepoKey(repoKey);
            if (!passThrough) {
                RepoResource cachedResource = getResourceFromCache(repoKey, curatePath);
                if (cachedResource != null) {
                    return cachedResource;
                }
                throwIfForbiddenMarkerPresent(controller, remoteCacheRepoKey, curatePath, res.getPath());
            }
            return createCuratedMetadataAndRepoResource(controller, remoteUrl, requestContext, repo, res,
                    passThrough);
        } catch (PackageBlockedByCurationException e) {
            throw e;
        } catch (PackageException e) {
            log.debug("Failed to create curated metadata for repo: {}, path: {} with an error: {}", res.getRepoKey(),
                    res.getPath(), e.getMessage());
            return null;
        }
    }


    @VisibleForTesting
    @Nullable
    RepoResource getResourceFromCache(String repoKey, String curatePath) {
        RepoResource cachedCuratedRepoResource;
        try {
            log.debug("Trying to get cached curated metadata stream for repo: {}, path: {}", repoKey, curatePath);
            cachedCuratedRepoResource = (RepoResource) packageArtifactService.getResource(
                    new PackageDownloadContext(packageRepoService.getRemoteCacheRepoKey(repoKey), curatePath));
        } catch (PackageException | ClassCastException e) {
            log.debug("Failed to get cached curated metadata for repo: {}, path: {}", repoKey,
                    curatePath, e);
            return null;
        }

        if (cachedCuratedRepoResource.isFound() && !cachedCuratedRepoResource.isExpired()) {
            log.debug("Found cached curated metadata for repo: {}, path: {}", repoKey, curatePath);
            return cachedCuratedRepoResource;
        } else {
            log.debug("Cached curated metadata for repo: {}, path: {} is expired or not found.", repoKey, curatePath);
            return null;
        }

    }

    private void throwIfForbiddenMarkerPresent(PackageVersionCurationController<T> controller,
            String remoteCacheRepoKey, String curatePath, String metadataPath)
            throws PackageBlockedByCurationException {
        String forbiddenMarkerPath = getForbiddenMarkerPath(curatePath);
        String message;
        try {
            PackageArtifact marker = packageArtifactService.getArtifact(remoteCacheRepoKey, forbiddenMarkerPath);
            message = resolveForbiddenMessage(marker, metadataPath);
        } catch (PackageNotFoundException | PackageExpiredException e) {
            log.debug("Forbidden marker not found or expired for repo: {}, path: {}. Status: {}, message: {}",
                    remoteCacheRepoKey, forbiddenMarkerPath, e.getStatus(), e.getMessage());
            return;
        } catch (PackageException e) {
            log.debug("Failed to read forbidden marker for repo: {}, path: {}", remoteCacheRepoKey,
                    forbiddenMarkerPath);
            return;
        }
        log.debug("Forbidden marker found for repo: {}, path: {}", remoteCacheRepoKey, forbiddenMarkerPath);
        controller.throwPackageBlockedByCurationException(message);
    }

    private String resolveForbiddenMessage(PackageArtifact marker, String metadataPath) {
        Collection<String> messageValues = marker.getProperties() == null
                ? null
                : marker.getProperties().get(FORBIDDEN_MESSAGE_PROPERTY);
        String propertyMessage = messageValues == null ? null : messageValues.stream().findFirst().orElse(null);
        if (StringUtils.isNotBlank(propertyMessage)) {
            return propertyMessage;
        }
        return getDefaultForbiddenMessage(null, metadataPath);
    }

    private void saveForbiddenMarker(String repoKey, String curatePath, String metadataPath, String curationMessage) {
        String forbiddenMarkerPath = getForbiddenMarkerPath(curatePath);
        String defaultMessage = getDefaultForbiddenMessage(null, metadataPath);
        String messageToStore = StringUtils.isBlank(curationMessage) ? defaultMessage : curationMessage;

        try {
            log.debug("Saving forbidden marker for repo: {}, path: {}", repoKey, forbiddenMarkerPath);
            PackageUploadContextImpl uploadContext = new PackageUploadContextImpl(repoKey, forbiddenMarkerPath)
                    .setContent(new ByteArrayInputStream(EMPTY_MARKER_CONTENT))
                    .setSystemGenerated(true);
            packageUploadService.upload(uploadContext);

            if (!messageToStore.equals(defaultMessage)) {
                packageRepoService.addAttribute(repoKey, forbiddenMarkerPath, FORBIDDEN_MESSAGE_PROPERTY,
                        Set.of(messageToStore));
            }
        } catch (Exception ex) {
            log.warn("Failed to save forbidden marker for repo: {}, path: {} ({})", repoKey, forbiddenMarkerPath,
                    ex.getMessage());
            log.debug("", ex);
        }
    }

    private void removeForbiddenMarker(String repoKey, String curatePath) {
        String forbiddenMarkerPath = getForbiddenMarkerPath(curatePath);
        try {
            if (!packageRepoService.existLocally(repoKey, forbiddenMarkerPath)) {
                log.debug("Forbidden marker does not exist for repo: {}, path: {}", repoKey, forbiddenMarkerPath);
                return;
            }
            packageRepoService.undeployArtifact(repoKey, forbiddenMarkerPath);
            log.debug("Removed forbidden marker for repo: {}, path: {}", repoKey, forbiddenMarkerPath);
        } catch (Exception e) {
            log.debug("Forbidden marker not removed for repo: {}, path: {} ({})", repoKey, forbiddenMarkerPath,
                    e.getMessage());
        }
    }

}
