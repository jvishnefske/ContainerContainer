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

package org.artifactory.ui.rest.service.setmeup;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.PermissionHeuristicScore;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.security.SystemAuthenticationToken;
import org.artifactory.security.access.AccessService;
import org.artifactory.ui.rest.model.setmeup.AIEditorExtensionTokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.artifactory.security.ArtifactoryPermission.DEPLOY;
import static org.artifactory.security.ArtifactoryPermission.READ;

/**
 * Handler for generating scoped reference tokens for AI Editor Extensions (VS Code) Set-Me-Up.
 *
 * @see <a href="https://jfrog-int.atlassian.net/browse/JA-19213">JA-19213</a>
 */
@Component
@Slf4j
public class AIEditorExtensionGenerateTokenHandler extends BaseSetMeUpHandler<Void> {

    private static final long TOKEN_EXPIRY_SECONDS = TimeUnit.DAYS.toSeconds(365);

    @Autowired
    SecurityService securityService;
    @Autowired
    AccessService accessService;

    public AIEditorExtensionGenerateTokenHandler(AuthorizationService authorizationService,
            InternalRepositoryConfigService repositoryConfigService, ProjectsService projectsService,
            AccessService accessService, SecurityService securityService) {
        super(authorizationService, repositoryConfigService, projectsService);
        this.accessService = accessService;
        this.securityService = securityService;
    }

    @Override
    public void execute(ArtifactoryRestRequest<Void> request, RestResponse response) {
        validateRequestContext(request, response);
        String repoKey = getRepoKeyFromRequest(request);
        validateFeatureEnabled();
        RepoConfig repoConfig = getRepoConfig(repoKey);
        validateRepo(repoConfig);
        String artifactScope = resolveArtifactScopeForCurrentUser(repoKey);
        assertUnrestrictedRepositoryRead(repoKey);
        String referenceToken = createScopedReferenceToken(repoKey, artifactScope);
        response.iModel(AIEditorExtensionTokenResponse.builder()
                .referenceToken(referenceToken).repoKey(repoKey).build());
    }

    private static void validateRequestContext(ArtifactoryRestRequest<Void> request, RestResponse response) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");
    }

    private String getRepoKeyFromRequest(ArtifactoryRestRequest<Void> request) {
        String repoKey = request.getQueryParamByKey(REPO_KEY);
        if (StringUtils.isBlank(repoKey)) {
            throw new BadRequestException("Missing repo key parameter");
        }
        return repoKey;
    }

    private void validateFeatureEnabled() {
        if (!ConstantValues.aieditorExtensionsSetMeUpRepositoryEnabled.getBoolean()) {
            throw new BadRequestException("AI Editor Extensions Set-Me-Up is not enabled");
        }
    }

    private RepoConfig getRepoConfig(String repoKey) {
        try {
            RepoConfig repoConfig =
                    ((InternalRepositoryConfigService) repositoryConfigService).getRepoConfigByKey(repoKey, true);
            if (repoConfig == null) {
                throw new ForbiddenException("Unable to generate token for the requested repository");
            }
            return repoConfig;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid repository key: '%s'".formatted(repoKey));
        }
    }

    private void validateRepo(RepoConfig repoConfig) {
        if (repoConfig.getPackageType() != RepoType.AIEditorExtensions) {
            throw new BadRequestException(
                    "Repository '%s' is not an AI Editor Extensions repository".formatted(repoConfig.getKey()));
        }
        if (repoConfig.getRepoType() != RepoDetailsType.REMOTE) {
            throw new BadRequestException(
                    "Repository '%s' must be a remote repository".formatted(repoConfig.getKey()));
        }
    }

    /**
     * These tokens grant read on every path in the repo.
     * Do not mint them for users who cannot read the whole repo.
     */
    private void assertUnrestrictedRepositoryRead(String repoKey) {
        PermissionHeuristicScore score = authService.getStrongestReadPermissionTarget(repoKey);
        if (score != PermissionHeuristicScore.readAll && score != PermissionHeuristicScore.admin) {
            throw new ForbiddenException(
                    "User must have unrestricted read permission on repository '%s'".formatted(repoKey));
        }
    }

    /**
     * Token scope must mirror the caller's effective permissions on the repository (JA-19213 / product alignment).
     */
    private String resolveArtifactScopeForCurrentUser(String repoKey) {
        boolean canRead = authService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(READ), null);
        if (!canRead) {
            throw new ForbiddenException(
                    "User must have read permission on repository '%s'".formatted(repoKey));
        }
        boolean canDeploy = authService.userHasPermissionsOnRepositoryRoot(repoKey, Set.of(DEPLOY), null);
        if (canDeploy) {
            return "artifact:%s:r,w".formatted(repoKey);
        }
        return "artifact:%s:r".formatted(repoKey);
    }

    private String createScopedReferenceToken(String repoKey, String artifactScope) {
        String username = securityService.currentUsername();
        if (StringUtils.isBlank(username)) {
            throw new ForbiddenException("User must be authenticated");
        }
        UserTokenSpec tokenSpec = UserTokenSpec.create(username)
                .scope(Lists.newArrayList(artifactScope))
                .refreshable(false)
                .reference(true)
                .expiresIn(TOKEN_EXPIRY_SECONDS);
        try {
            // Run as system to allow non-admin users to create reference tokens
            Authentication originalAuth = SecurityContextHolder.getContext().getAuthentication();
            try {
                SecurityContextHolder.getContext().setAuthentication(new SystemAuthenticationToken());
                CreatedTokenInfo tokenInfo = accessService.createToken(tokenSpec, true, false);
                String referenceToken = tokenInfo.getReferenceToken();
                if (StringUtils.isBlank(referenceToken)) {
                    throw new IllegalStateException(
                            "Failed to generate reference token: no token returned for repository %s"
                                    .formatted(repoKey));
                }
                return referenceToken;
            } finally {
                SecurityContextHolder.getContext().setAuthentication(originalAuth); // Restore caller's auth
            }
        } catch (Exception e) {
            log.error("Failed to create AI Editor Extension scoped token for repo '{}'", repoKey, e);
            throw new BadRequestException("Failed to generate token");
        }
    }
}
