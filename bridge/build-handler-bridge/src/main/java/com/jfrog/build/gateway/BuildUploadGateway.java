package com.jfrog.build.gateway;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.request.UploadService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.deploy.ArtifactoryDeployRequest;
import org.artifactory.repo.service.deploy.ArtifactoryDeployRequestBuilder;
import org.artifactory.request.InternalCapturingResponse;
import org.artifactory.rest.exception.RestException;
import org.jfrog.common.iostreams.in.StringInputStream;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.conflictException;
import static org.artifactory.exception.factory.ExceptionFactory.forbiddenException;

@Slf4j
@RequiredArgsConstructor
public class BuildUploadGateway {

    private final UploadService uploadService;

    @NonNull
    public String upload(
            @NonNull String repoPath,
            @NonNull String payload) {
        int size = payload.getBytes().length;
        InternalCapturingResponse response = new InternalCapturingResponse();
        try (StringInputStream input = new StringInputStream(payload, UTF_8)) {
            ArtifactoryDeployRequest deployRequest = createDeployRequest(repoPath, input, size);
            uploadService.upload(deployRequest, response);
        } catch (BinaryRejectedException | RepoRejectException rej) {
            log.debug("Rejected error occurred", rej);
            throw conflictException(rej.getMessage());
        } catch (Exception e) {
            throw badRequestException("Error during build json deploy: %s", e, e.getMessage());
        }
        return parseUploadResponse(response);
    }

    @NonNull
    private String parseUploadResponse(@NonNull InternalCapturingResponse response) {
        if (response.getStatus() == SC_FORBIDDEN) {
            throw forbiddenException(response.getStatusMessage());
        }
        if (response.getStatus() >= SC_BAD_REQUEST) {
            throw new RestException(response.getStatus(), response.getStatusMessage());
        }
        return response.getResultAsString();
    }

    @NonNull
    private ArtifactoryDeployRequest createDeployRequest(
            @NonNull String rawRepoPath,
            @NonNull StringInputStream inputStream,
            @NonNull Integer contentLength) {
        RepoPath repoPath = RepoPathFactory.create(rawRepoPath);
        return new ArtifactoryDeployRequestBuilder(repoPath)
                .inputStream(inputStream)
                .contentLength(contentLength)
                .trustServerChecksums(true)
                .build();
    }
}
