package com.jfrog.build.gateway;

import com.jfrog.build.acl.model.BuildArtifactChecksums;
import com.jfrog.build.acl.model.BuildArtifactMetadata;
import com.jfrog.build.acl.model.BuildRepoDescriptor;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoBaseDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.io.NullResourceStreamHandle;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.MetadataEntityFacade;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.util.DoesNotExistException;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.isNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toSet;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildRepoGateway {

    private final RepositoryConfigService repositoryConfigService;
    private final InternalRepositoryService repositoryService;
    private final MetadataEntityFacade metadataEntityFacade;

    @NonNull
    public Optional<BuildArtifactMetadata> getArtifactMetadata(@NonNull String repoPath) {
        return metadataEntityFacade.getMetadataBom(repoPath)
                .map(bom -> BuildArtifactMetadata.builder()
                        .packageType(bom.getPackageType())
                        .name(bom.getName())
                        .version(bom.getVersion())
                        .packageId(bom.getPkgid())
                        .build());
    }

    /**
     * Resolves the SHA1/MD5 checksums of the artifact at the given repo path (resolving virtual repositories to
     * their backing item). Returns empty when the path resolves to a folder rather than a file.
     */
    @NonNull
    public Optional<BuildArtifactChecksums> getArtifactChecksums(
            @NonNull String repoKey,
            @NonNull String path) {
        RepoPath repoPath = InternalRepoPathFactory.create(repoKey, path);
        try {
            ItemInfo itemInfo = repositoryService.isVirtualRepoExist(repoPath.getRepoKey())
                    ? repositoryService.getVirtualItemInfo(repoPath)
                    : repositoryService.getItemInfo(repoPath);
            if (itemInfo instanceof FileInfo fileInfo) {
                return of(new BuildArtifactChecksums(fileInfo.getSha1(), fileInfo.getMd5()));
            }
            return empty();
        } catch (IllegalArgumentException | ItemNotFoundRuntimeException e) {
            throw badRequestException("Failed to retrieve build information for the specified artifact.", e);
        } catch (Exception e) {
            throw internalServerException("The system was unable to fetch build details for the artifact.", e);
        }
    }

    public boolean repoPathExists(@NonNull String rawRepoPath) {
        RepoPath repoPath = RepoPathFactory.create(rawRepoPath);
        return repositoryService.exists(repoPath);
    }

    @NonNull
    public Optional<String> getDefaultDeploymentRepoKey(@Nullable String repoKey) {
        try {
            return ofNullable(repositoryService.getDefaultDeploymentRepoKey(repoKey));
        } catch (DoesNotExistException e) {
            return empty();
        }
    }

    @NonNull
    public Optional<BuildRepoDescriptor> getRepoDescriptor(@NonNull String repoKey) {
        RepoDescriptor repoDescriptor = repositoryService.repoDescriptorByKey(repoKey);
        return isNull(repoDescriptor) ? empty() : of(createRepoDescriptor(repoDescriptor));
    }

    @NonNull
    private BuildRepoDescriptor createRepoDescriptor(@NonNull RepoDescriptor repoDescriptor) {
        return BuildRepoDescriptor.builder()
                .key(repoDescriptor.getKey())
                .type(repoDescriptor.getType().getType())
                .localOrFederated(repoDescriptor instanceof LocalRepoDescriptor)
                .cache(repoDescriptor instanceof LocalCacheRepoDescriptor)
                .build();
    }

    @NonNull
    public List<String> getVirtualRepoKeys() {
        return repositoryService.getVirtualRepoDescriptors().stream()
                .map(RepoBaseDescriptor::getKey)
                .toList();
    }

    @NonNull
    public Set<String> getRepoKeys(
            @NonNull Set<String> rawRepoTypes,
            @NonNull String rawPackageType) {
        RepoType repoType = RepoType.fromType(rawPackageType);
        Set<RepoDetailsType> repoTypes = rawRepoTypes.stream().map(RepoDetailsType::valueOf).collect(toSet());
        return repositoryConfigService.getRepoConfigKeysByTypeAndPackageType(repoTypes, repoType);
    }

    @NonNull
    public InputStream getResourceStream(@NonNull String rawRepoPath) {
        RepoPath repoPath = RepoPathFactory.create(rawRepoPath);
        ResourceStreamHandle streamHandle = repositoryService.getResourceStreamHandle(repoPath);
        if (streamHandle instanceof NullResourceStreamHandle) {
            throw notFoundException("Artifact does not exist: %s", rawRepoPath);
        }
        return streamHandle.getInputStream();
    }

    public boolean assertStorageQuota(
            @NonNull String repoKey,
            @NonNull Long size) {
        BasicStatusHolder status = new BasicStatusHolder();
        repositoryService.assertStorageQuota(repoKey, status, size);
        return !status.isError();
    }

    public void undeploy(
            @NonNull String rawRepoPath,
            @NonNull MutableStatusHolder status,
            boolean triggerMetadataCalculation,
            boolean pruneEmptyFolders,
            boolean skipTrashcan,
            boolean skipBuildDeleteInterceptor,
            boolean idempotentDelete) {
        RepoPath repoPath = RepoPathFactory.create(rawRepoPath);
        DeleteContext ctx = new DeleteContext(repoPath)
                .triggerMetadataCalculation(triggerMetadataCalculation)
                .skipTrashcan(skipTrashcan)
                // when idempotentDelete is set, a concurrent republish (overwrite) of the same build
                // coordinates may have already removed the artifact, and a missing artifact here should be treated
                // as a no-op success instead of failing the operation with a not-found error.
                .idempotentDelete(idempotentDelete);
        if (skipBuildDeleteInterceptor) {
            ctx.avoidBuildDeleteInterceptor();
        }
        BasicStatusHolder undeployStatus = repositoryService.undeploy(repoPath, pruneEmptyFolders, ctx);
        status.merge(undeployStatus);
    }

    public void copy(
            @NonNull Set<String> repoPathsToCopy,
            @NonNull String targetRepoKey,
            @NonNull MutableStatusHolder status,
            boolean failFast,
            boolean dryRun) {
        Properties properties = buildEmptyProperties();
        Set<RepoPath> repoPaths = convertRepoPaths(repoPathsToCopy);
        BasicStatusHolder copyStatus = repositoryService.copy(repoPaths, targetRepoKey, properties, dryRun, failFast);
        status.merge(copyStatus);
    }

    public void move(
            @NonNull Set<String> repoPathsToMove,
            @NonNull String targetRepoKey,
            @NonNull MutableStatusHolder status,
            boolean failFast,
            boolean dryRun) {
        Properties properties = buildEmptyProperties();
        Set<RepoPath> repoPaths = convertRepoPaths(repoPathsToMove);
        BasicStatusHolder moveStatus = repositoryService.move(repoPaths, targetRepoKey, properties, dryRun, failFast);
        status.merge(moveStatus);
    }

    @NonNull
    private Set<RepoPath> convertRepoPaths(@NonNull Set<String> repoPathsToCopy) {
        return repoPathsToCopy.stream().map(RepoPathFactory::create).collect(toSet());
    }

    @NonNull
    private Properties buildEmptyProperties() {
        return (Properties) InfoFactoryHolder.get().createProperties();
    }
}