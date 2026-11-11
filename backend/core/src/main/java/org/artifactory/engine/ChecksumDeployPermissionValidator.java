package org.artifactory.engine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.search.artifact.ChecksumSearchControls;
import org.artifactory.api.search.exception.InvalidChecksumException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.search.InternalSearchService;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.common.ExceptionLogUtils;
import org.jfrog.common.JsonUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.artifactory.api.request.ArtifactoryHeaders.X_CHECKSUM_DEPLOY_TOKEN;
import static org.artifactory.util.HttpUtils.getSha1Checksum;
import static org.artifactory.util.HttpUtils.getSha256Checksum;

/**
 * @author Lior Gur
 */
@Component
@Slf4j
@AllArgsConstructor
public class ChecksumDeployPermissionValidator {

    public static final String CHECKSUM_DEPLOY_SCOPE = "internal:checksum-deploy:w";
    private InternalSearchService searchService;
    private AuthorizationService authService;
    private SecurityService securityService;
    private AccessService accessService;

    public boolean isChecksumDeployRequest(ArtifactoryRequest request) {
        return Boolean.parseBoolean(request.getHeader(ArtifactoryRequest.CHECKSUM_DEPLOY));
    }

    public boolean hasReadPermission(ChecksumInfo checksumInfo, String repoKey)
            throws InvalidChecksumException {
        if (skipValidation()) {
            return true;
        }
        return hasReadPermissionToChecksumFile(checksumInfo.getOriginal(), checksumInfo.getType(), repoKey);
    }

    protected boolean hasReadPermission(ArtifactoryRequest request, ArtifactoryResponse response)
            throws IOException {
        if (skipValidation()) {
            return true;
        }
        String sha1 = getSha1Checksum(request);
        String sha2 = getSha256Checksum(request);
        String checksum;
        ChecksumType checksumType;
        if (StringUtils.isNotBlank(sha1)) {
            checksum = sha1;
            checksumType = ChecksumType.sha1;
        } else if (StringUtils.isNotBlank(sha2)) {
            checksum = sha2;
            checksumType = ChecksumType.sha256;
        } else {
            response.sendError(SC_BAD_REQUEST, "Checksum values not provided", log);
            return false;
        }
        if (isAllowedByChecksumDeployToken(request, checksumType, checksum)) {
            return true;
        }
        try {
            if (hasReadPermissionToChecksumFile(checksum, checksumType, request.getRepoKey())) {
                return true;
            } else {
                response.sendError(SC_NOT_FOUND, "Checksum " + checksumType + ":" + checksum + " not found", log);
            }
        } catch (InvalidChecksumException e) {
            log.warn("{}", e.getMessage(), e);
            response.sendError(SC_NOT_FOUND, e.getMessage(), log);
        }
        return false;
    }

    @VisibleForTesting
    boolean isAllowedByChecksumDeployToken(ArtifactoryRequest request, ChecksumType checksumType,
            String checksum) {
        try {
            String checksumDeployToken = request.getHeader(X_CHECKSUM_DEPLOY_TOKEN);
            if (StringUtils.isNotBlank(checksumDeployToken)) {
                TokenVerifyResult verify = accessService.getAccessClient().token().verify(checksumDeployToken);
                if (verify != null && verify.isSuccessful() &&
                        verify.getAccessToken().hasScopeMatching(CHECKSUM_DEPLOY_SCOPE::equals)) {
                    ChecksumDeployTokenExtension tokenExtension = JsonUtils.getInstance()
                            .readValue(verify.getAccessToken().getExtension(),
                                    ChecksumDeployTokenExtension.class);
                    if (tokenExtension != null && request.getPath().equals(tokenExtension.repoPath()) &&
                            request.getRepoKey().equals(tokenExtension.repoKey())) {
                        return (checksumType == ChecksumType.sha1 && checksum.equals(tokenExtension.sha1())) ||
                                (checksumType == ChecksumType.sha256 && checksum.equals(tokenExtension.sha2()));
                    }
                }
            }
        } catch (Exception e) {
            ExceptionLogUtils.debugOrWarn(log,
                    "Failed to validate checksum deploy permission using multipart upload token", e);
        }
        return false;
    }

    private boolean hasReadPermissionToChecksumFile(String checksum, ChecksumType checksumType, String repoKey)
            throws InvalidChecksumException {
        int limit = ConstantValues.maxArtifactsPermissionTestOnChecksumDeploy.getInt();
        ChecksumSearchControls csControls = new ChecksumSearchControls();
        csControls.addChecksum(checksumType, checksum);
        csControls.setLimit(limit);

        //Get list of artifacts using Aql with parameters checksum & checksumType
        Set<RepoPath> repoPaths = searchService
                .searchArtifactsByChecksum(csControls);
        if (repoPaths.isEmpty()) {
            return false;
        }
        for (RepoPath repoPath : repoPaths) {
            if (authService.canRead(repoPath)) {
                return true;
            }
        }
        // Getting here means that no permission found for an artifact with this checksum
        // In this case we will search using Aql with parameters checksum, checksumType & Repo
        // And then check permission on the results
        if (repoPaths.size() == limit) {
            csControls.setSelectedRepoForSearch(Lists.newArrayList(repoKey));
            repoPaths = searchService
                    .searchArtifactsByChecksum(csControls);
            for (RepoPath repoPath : repoPaths) {
                if (authService.canRead(repoPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean skipValidation() {
        return ConstantValues.ignoreChecksumDeployPermissionCheck.getBoolean() || securityService.isAdmin();
    }
}
