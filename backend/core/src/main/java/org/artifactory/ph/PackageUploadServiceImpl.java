package org.artifactory.ph;

import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageRepoNotFoundException;
import com.jfrog.ph.acl.exception.PackageRuntimeException;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.service.PackageUploadService;
import com.jfrog.ph.acl.virtual.resolution.PackageUploadContext;
import jakarta.annotation.Nonnull;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.JerseyArtifactoryResponse;
import org.artifactory.api.request.UploadService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.repo.PackageType;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.SaveResourceContext;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.deploy.ArtifactoryDeployRequest;
import org.artifactory.repo.service.deploy.ArtifactoryDeployRequestBuilder;
import org.artifactory.resource.FileResource;
import org.artifactory.rest.exception.RestException;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.traffic.TrafficService;
import org.artifactory.util.HttpUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.artifactory.api.request.ArtifactoryHeaders.CHECKSUM_MD5;
import static org.artifactory.api.request.ArtifactoryHeaders.CHECKSUM_SHA1;
import static org.artifactory.api.request.ArtifactoryHeaders.CHECKSUM_SHA256;
import static org.artifactory.ph.mapper.PhExceptionMapper.throwPackageException;

@Slf4j
public class PackageUploadServiceImpl implements PackageUploadService {

    private final InternalRepositoryService internalRepositoryService;
    private final UploadService uploadService;
    private final SecurityService securityService;
    private final TrafficService trafficService;

    public PackageUploadServiceImpl(InternalRepositoryService internalRepositoryService,
            UploadService uploadService, SecurityService securityService, TrafficService trafficService) {
        this.internalRepositoryService = internalRepositoryService;
        this.uploadService = uploadService;
        this.securityService = securityService;
        this.trafficService = trafficService;
    }

    public void upload(PackageUploadContext uploadContext) throws PackageException {
        upload(uploadContext, false);
    }

    public void upload(PackageUploadContext uploadContext, boolean logTraffic) throws PackageException {
        String repoKey = uploadContext.getRepoKey();
        String path = uploadContext.getPath();
        InputStream inputStream = uploadContext.getContent();
        boolean isSystemGenerated = uploadContext.isSystemGenerated();
        LocalRepo<?, ?> localRepo = internalRepositoryService.localFederatedOrCachedRepositoryByKey(repoKey);
        if (localRepo != null && localRepo.isCache()) {
            try {
                uploadToVirtualOrRemoteCache(repoKey, path, inputStream, logTraffic);
                return;
            } catch (PackageException e) {
                throw new PackageRuntimeException(e.getStatus(), e.getPublicReason(), e.getInternalReason(), e);
            }
        }
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        if (isInternalUpload(isSystemGenerated, path)) {
            try {
                log.debug("Uploading internal artifact to repoKey: {}, path: {}", repoKey, path);
                internalRepositoryService.saveFileInternal(repoPath, inputStream);
            } catch (RepoRejectException | IOException e) {
                throw new PackageRuntimeException(SC_BAD_REQUEST, e.getMessage(), e.getMessage(),
                        new PackageException(SC_BAD_REQUEST, e.getMessage(), e.getMessage(), e));
            }
        } else {
            deploy(inputStream, repoPath);
        }
    }

    private boolean isInternalUpload(boolean isSystemGenerated, String path) {
        log.debug("Checking if path {} is internal upload", path);
        return InternalMetadataChecker.isInternalMetadata(path) ||
                (ConstantValues.packageHandlerInternalFilesChargeFix.getBoolean() && isSystemGenerated &&
                        securityService.isAuthenticatedAsSystem());
    }

    private void deploy(InputStream inputStream, RepoPath repoPath) throws PackageException {
        StatusHolder deploy = internalRepositoryService.deploy(repoPath, inputStream);
        if (deploy != null && deploy.isError()) {
            throwPackageException(deploy.getStatusCode(), deploy.getStatusMsg());
        }
    }

    @Override
    public RepoResource uploadToVirtualOrRemoteCache(String repoKey, String path, InputStream inputStream)
            throws PackageException {
        return uploadToVirtualOrRemoteCache(repoKey, path, inputStream, false);
    }

    public RepoResource uploadToVirtualOrRemoteCache(String repoKey, String path, InputStream inputStream,
            boolean logTraffic) throws PackageException {
        StoringRepo<?, ?> storingRepo = getRemoteOrVirtualStoringRepo(repoKey);
        try {
            RepoResource savedResource = saveResourceLocally(repoKey, path, inputStream, storingRepo);
            if (logTraffic) {
                logRemoteCacheUploadTrafficIfNeeded(savedResource, storingRepo);
            }
            return savedResource;
        } catch (Exception e) {
            throw new PackageException(SC_INTERNAL_SERVER_ERROR, "internal server error", e.getMessage(), e);
        }
    }

    public PackageArtifact uploadToVirtualOrRemoteCacheV2(String repoKey, String path, InputStream inputStream)
            throws PackageException {
        return uploadToVirtualOrRemoteCacheV2(repoKey, path, inputStream, false);
    }

    public PackageArtifact uploadToVirtualOrRemoteCacheV2(String repoKey, String path, InputStream inputStream,
            boolean logTraffic) throws PackageException {
        StoringRepo<?, ?> storingRepo = getRemoteOrVirtualStoringRepo(repoKey);
        try {
            RepoResource uploadRes = saveResourceLocally(repoKey, path, inputStream, storingRepo);
            if (logTraffic) {
                logRemoteCacheUploadTrafficIfNeeded(uploadRes, storingRepo);
            }
            return PackageArtifactMapper.artifact(uploadRes, PackageType.valueOf(
                    storingRepo.getRepoConfig().getPackageType().getType().toUpperCase()));
        } catch (Exception e) {
            throw new PackageException(SC_INTERNAL_SERVER_ERROR, "internal server error", e.getMessage(), e);
        }
    }

    private RepoResource saveResourceLocally(String repoKey, String path, InputStream inputStream,
                                         StoringRepo<?, ?> storingRepo) throws IOException, RepoRejectException {
        MutableFileInfo fileInfo = InfoFactoryHolder.get().createFileInfo(RepoPathFactory.create(repoKey, path));
        fileInfo.createTrustedChecksums();
        FileResource repoResource = new FileResource(fileInfo);
        SaveResourceContext saveContext = new SaveResourceContext.Builder(repoResource, inputStream).build();
        return internalRepositoryService.saveResource(storingRepo, saveContext);
    }

    private void logRemoteCacheUploadTrafficIfNeeded(RepoResource savedResource, StoringRepo<?, ?> storingRepo) {
        if (!storingRepo.isCache()) {
            return;
        }
        long size = savedResource.getSize();
        TrafficEntry trafficEntry = TrafficEntry.builder()
                .action(TrafficEntry.TrafficAction.UPLOAD)
                .actor(TrafficEntry.TrafficActor.USER)
                .redirectTarget(TrafficEntry.TrafficTarget.DIRECT)
                .userAddress(HttpUtils.getRemoteClientAddress())
                .repoPath(savedResource.getRepoPath().getId())
                .size(size)
                .requestedSize(size)
                .transferredSize(size)
                .build();
        trafficService.handleTrafficEntry(trafficEntry);
    }

    private StoringRepo<?, ?> getRemoteOrVirtualStoringRepo(String repoKey) throws PackageException {
        StoringRepo<?, ?> storingRepo = internalRepositoryService.storingRepositoryByKey(repoKey);
        if (storingRepo == null) {
            throw new PackageRepoNotFoundException("Storing repository " + repoKey + " not found");
        }
        if (!storingRepo.isCache() && storingRepo.isReal()) {
            throw new PackageException(SC_BAD_REQUEST, "Bad request",
                                       "Bad request: internal uploads allowed only to remote-cache and virtual repos");
        }
        return storingRepo;
    }

    @Override
    public Response uploadWithResponse(PackageUploadContext context) throws PackageException {
        JerseyArtifactoryResponse res = new JerseyArtifactoryResponse();
        ArtifactoryDeployRequest req = buildArtifactoryRequest(context);
        try {
            if (CurrentActivity.GROUP.get() == null) {
                CurrentActivity.GROUP.put("upload");
            }
            uploadService.upload(req, res);
            return res.build();
        } catch (RestException e) {
            throw new PackageException(e.getStatusCode(), e.getMessage(), e.getMessage());
        } catch (IOException | RepoRejectException e) {
            log.error(e.getMessage(), e);
            throw new PackageException(SC_BAD_REQUEST, e.getMessage(), e.getMessage());
        }
    }

    @Nonnull
    @Override
    public File getTempUploadDir() {
        return ArtifactoryHome.get().getTempUploadDir();
    }

    @Nonnull
    private ArtifactoryDeployRequest buildArtifactoryRequest(PackageUploadContext context) {
        Properties properties = parseUploadProperties(context);
        ArtifactoryDeployRequest req =
                new ArtifactoryDeployRequestBuilder(RepoPathFactory.create(context.getRepoKey(), context.getPath()))
                        .inputStream(context.getContent())
                        .properties(properties)
                        .contentLength(contentLength(context))
                        .build();

        Map<String, String> headers = Maps.newHashMap();
        headers.putAll(context.getRequestHeaders());
        if (StringUtils.isNotBlank(context.getSha1())) {
            headers.put(CHECKSUM_SHA1, context.getSha1());
        }
        if (StringUtils.isNotBlank(context.getSha256())) {
            headers.put(CHECKSUM_SHA256, context.getSha256());
        }
        if (StringUtils.isNotBlank(context.getMd5())) {
            headers.put(CHECKSUM_MD5, context.getMd5());
        }
        req.addHeaders(headers);
        req.setSkipPackageUsageTrack(context.isSkipPackageUsageTrack());

        return req;
    }

    private long contentLength(PackageUploadContext context) {
        if (context.getContentLength() > 0) {
            return context.getContentLength();
        }
        Map<String, String> headers = context.getRequestHeaders();
        String headerValue = headers.entrySet()
                .stream()
                .filter(entry -> HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(entry.getKey()))
                .findFirst().map(Map.Entry::getValue)
                .orElse("-1");
        try {
            return Long.parseLong(headerValue);
        } catch (NumberFormatException e) {
            log.warn("Content-Length header value is not a number: '{}', path: {}", headerValue, context.getPath());
        }
        return -1; //unspecified
    }


    private Properties parseUploadProperties(PackageUploadContext context) {
        SetMultimap<String, String> contextAttributes = context.getAttributes();
        Properties properties = (Properties) InfoFactoryHolder.get().createProperties();
        contextAttributes.forEach(properties::put);
        return properties;
    }


}
