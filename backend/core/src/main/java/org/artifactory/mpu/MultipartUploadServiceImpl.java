package org.artifactory.mpu;

import com.google.common.annotations.VisibleForTesting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.engine.ChecksumDeployTokenExtension;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.mpu.cleanup.MultipartUploadCleanupJob;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.DownloadPerfCounters;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.jfrog.access.common.ServiceType;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.common.JsonUtils;
import org.jfrog.storage.mpu.MultipartUploadInitiateResponse;
import org.jfrog.storage.mpu.MultipartUploadParameters;
import org.jfrog.storage.mpu.entity.MultipartUploadData;
import org.jfrog.storage.mpu.entity.MultipartUploadTask;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.artifactory.common.ConstantValues.mpuChecksumDeployTokenExpirySecs;
import static org.artifactory.common.ConstantValues.mpuEnabled;
import static org.artifactory.common.ConstantValues.mpuTokenExpirySecs;
import static org.artifactory.engine.ChecksumDeployPermissionValidator.CHECKSUM_DEPLOY_SCOPE;
import static org.jfrog.storage.mpu.entity.MultipartUploadStatus.Finished;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadServiceImpl implements MultipartUploadService, ContextCreationListener {

    static final String FEATURE_DISABLED_MESSAGE = "Multipart upload unsupported";
    static final String FORBIDDEN_DEPLOY_MESSAGE = "The user is not allowed to deploy to this location";
    private static final List<String> MPU_SCOPES = List.of(MPU_SCOPE);
    private static final List<String> MPU_AUDIENCE = List.of(ServiceType.ARTIFACTORY + "@*");

    private final AuthorizationService authorizationService;
    private final SecurityService securityService;
    private final AccessService accessService;
    private final ArtifactoryBinaryService binaryService;
    private final ProjectsService projectsService;
    private final RepositoryService repositoryService;
    private final TaskService taskService;
    private final ConfigsService configsService;

    @Nonnull
    @Override
    public String generateToken(@Nonnull String repoKey, @Nonnull String path, int partSizeMB) {
        assertFeatureEnabled();
        RepoPath repoPath = getRepoPath(repoKey, path);
        if (repositoryService.repoExists(repoKey)) {
            // Use the custom repoPath to verify virtual repo will get the inline deployment repo for the canDeploy
            if (authorizationService.canDeploy(repoPath)) {
                var mpuInitiateResponse = binaryService.initiateMultipartUpload(repoKey, partSizeMB);
                log.debug("Initiate response from binary store: {}", mpuInitiateResponse);
                UserTokenSpec userTokenSpec = UserTokenSpec.create(securityService.currentUsername())
                        .scope(MPU_SCOPES)
                        .audience(MPU_AUDIENCE)
                        .expiresIn(mpuTokenExpirySecs.getLong())
                        .refreshable(false)
                        .extension(createTokenExtension(RepoPathFactory.create(repoKey, path), mpuInitiateResponse));
                try {
                    return securityService.callAsSystem(() -> accessService.createToken(userTokenSpec, true, true))
                            .getTokenValue();
                } catch (Exception e) {
                    throw new MultipartTokenCreationException(e);
                }
            }
        } else {
            log.warn("Failed to generate token: repository doesnt exists");
        }

        throw new ForbiddenException(FORBIDDEN_DEPLOY_MESSAGE);
    }

    @VisibleForTesting
    RepoPath getRepoPath(String repoKey, String path) {
        RepoConfig repoConfig = repositoryService.repoConfigByKey(repoKey);
        if (repoConfig instanceof VirtualRepoConfig virtualRepoConfig) {
            // In case of virtual repository we want to get the default deployment repo
            String defaultDeploymentRepoKey = virtualRepoConfig.getRepoTypeConfig().getDefaultDeploymentRepoRef();
            if (StringUtils.isNotBlank(defaultDeploymentRepoKey)) {
                return RepoPathFactory.create(defaultDeploymentRepoKey, path);
            }
        }
        return RepoPathFactory.create(repoKey, path);
    }

    @Nonnull
    private String createTokenExtension(RepoPath repoPath, MultipartUploadInitiateResponse mpuInitiateResponse) {
        TokenExtension extension = new TokenExtension(repoPath.getRepoKey(), repoPath.getPath(),
                mpuInitiateResponse.uploadId(), mpuInitiateResponse.path());
        return JsonUtils.getInstance().valueToString(extension);
    }

    @Nonnull
    @Override
    public String urlPart(int partNumber) {
        TokenExtension extension = extractTokenExtension();
        String traceId = MDC.get("uber-trace-id");
        String repoKey = extension.repoKey();
        String projectKey = projectsService.getProjectKeyOfRepository(repoKey);
        String artifactPath = extension.repoPath();
        String username = securityService.currentUsername();
        RepoConfig repoConfig = repositoryService.repoConfigByKey(repoKey);
        String repoType = repoConfig.getRepoType().typeNameLowercase();
        String packageType = repoConfig.getPackageType().getType();
        MultipartUploadParameters mpuParams = MultipartUploadParameters.builder()
                .uploadId(extension.uploadId())
                .partNumber(partNumber)
                .traceId(traceId)
                .repoKey(repoKey)
                .projectKey(projectKey)
                .artifactPath(artifactPath)
                .username(username)
                .repoType(repoType)
                .packageType(packageType)
                .originRepoKey(repoKey)
                .originProjectKey(projectKey)
                .originRepoType(repoType)
                .originPackageType(packageType)
                .build();
        return binaryService.generateUploadUrl(extension.tempPathKey(), mpuParams);
    }

    @Override
    public void complete(@Nonnull HttpServletResponse response, @Nonnull String sha1) throws IOException {
        TokenExtension extension = extractTokenExtension();
        binaryService.completeMultipartUpload(extension.uploadId(), extension.tempPathKey(), sha1);
    }

    @Override
    public MultipartStatusResult status() {
        TokenExtension extension = extractTokenExtension();
        Pair<MultipartUploadTask, String> multipartUploadStatus =
                binaryService.getMultipartUploadStatus(extension.uploadId());
        MultipartUploadTask task = multipartUploadStatus.getLeft();
        if (task.getStatus() == Finished) {
            MultipartUploadData data = task.getData();
            DownloadPerfCounters metrics = RequestThreadLocal.getDownloadPerfCounters(true);
            if (metrics != null) {
                log.trace("Setting multipart upload artifact size to {}", data.getSize());
                metrics.setArtifactSizeBytes(data.getSize());
            }
            String checksumDeployToken =
                    generateMultipartUploadChecksumDeployToken(extension.repoKey(), extension.repoPath(),
                            data.getSha1(),
                            data.getSha2());
            return new MultipartStatusResult(task, multipartUploadStatus.getRight(), checksumDeployToken);
        }
        return new MultipartStatusResult(task, multipartUploadStatus.getRight(), null);
    }

    @VisibleForTesting
    String generateMultipartUploadChecksumDeployToken(String repoKey, String path, String sha1, String sha2) {
        log.trace("Generating token for repo: {}, path: {}, sha1: {},  sha2: {}", repoKey, path, sha1, sha2);
        UserTokenSpec userTokenSpec = UserTokenSpec.create(securityService.currentUsername())
                .scope(List.of(CHECKSUM_DEPLOY_SCOPE))
                .audience(MPU_AUDIENCE)
                .expiresIn(mpuChecksumDeployTokenExpirySecs.getLong())
                .refreshable(false)
                .extension(JsonUtils.getInstance()
                        .valueToString(new ChecksumDeployTokenExtension(repoKey, path, sha1, sha2)));
        try {
            return securityService.callAsSystem(() -> accessService.createToken(userTokenSpec, true, true))
                    .getTokenValue();
        } catch (Exception e) {
            throw new MultipartTokenCreationException(e);
        }
    }

    @Override
    public void abort() {
        TokenExtension extension = extractTokenExtension();
        binaryService.abortMultipartUpload(extension.uploadId(), extension.tempPathKey());
    }

    private void assertFeatureEnabled() {
        if (!isFeatureEnabled()) {
            throw new NotFoundException(FEATURE_DISABLED_MESSAGE);
        }
    }

    @Override
    public boolean isFeatureEnabled() {
        return mpuEnabled.getBoolean() && binaryService.isMultipartUploadSupported();
    }

    @Nonnull
    private TokenExtension extractTokenExtension() {
        try {
            AccessTokenAuthentication accessTokenAuthentication =
                    (AccessTokenAuthentication) SecurityContextHolder.getContext().getAuthentication();
            JwtAccessToken token = accessTokenAuthentication.getAccessToken();
            return JsonUtils.getInstance().readValue(requireNonNull(token.getExtension()), TokenExtension.class);
        } catch (NullPointerException e) {
            throw new NotFoundException(FEATURE_DISABLED_MESSAGE);
        }
    }

    @Override
    public void onContextCreated() {
        activateMultipartUploadCleanupJob();
    }

    private void activateMultipartUploadCleanupJob() {
        if (mpuEnabled.getBoolean()) {
            log.debug("Activating multipart upload cleanup job");
            String serviceId = configsService.getConfig(ConfigsServiceKey.ARTIFACTORY_SERVICE_ID);
            String quartzExpression = MultipartUploadCleanupJob.buildRandomQuartzExp(serviceId);
            Task task = TaskUtils.createCronTask(MultipartUploadCleanupJob.class,
                    quartzExpression, "Cleanup multipart uploads");
            taskService.startTask(task, false);
            log.info("Scheduling MultipartUploadCleanupJob to run at '{}'", quartzExpression);
        }
    }
}
