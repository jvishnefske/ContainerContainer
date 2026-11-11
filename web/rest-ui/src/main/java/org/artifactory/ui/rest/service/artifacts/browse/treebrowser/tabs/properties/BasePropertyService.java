package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.properties;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.utils.RequestUtils;

import jakarta.servlet.http.HttpServletResponse;

@Slf4j
public abstract class BasePropertyService<T> implements RestService<T> {

    protected final InternalRepositoryService repoService;
    protected final AuthorizationService authorizationService;
    protected final SecurityService securityService;
    protected final PropertiesService propsService;
    protected final MetadataServiceIndexHandler metadataServiceIndexHandler;

    protected BasePropertyService(InternalRepositoryService repoService, AuthorizationService authorizationService,
            SecurityService securityService, PropertiesService propsService,
            MetadataServiceIndexHandler metadataServiceIndexHandler) {
        this.repoService = repoService;
        this.authorizationService = authorizationService;
        this.securityService = securityService;
        this.propsService = propsService;
        this.metadataServiceIndexHandler = metadataServiceIndexHandler;
    }

    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        RepoPath repoPath = RequestUtils.getPathFromRequest(request);
        if (!authorizationService.canAnnotate(repoPath)) {
            response.responseCode(HttpServletResponse.SC_FORBIDDEN).buildResponse();
            return;
        }
        verifyRepoSupportsPropertyModification(repoPath, repoService);
        doExecute(request, response, repoPath);
    }

    protected static void verifyRepoSupportsPropertyModification(RepoPath repoPath,
            InternalRepositoryService repoService) {
        String repoKey = repoPath.getRepoKey();
        if (repoService.localFederatedOrCachedRepositoryByKey(repoKey) == null) {
            throw new BadRequestException("Operation is not supported for repo " + repoKey);
        }
    }

    protected abstract void doExecute(ArtifactoryRestRequest<T> request, RestResponse response, RepoPath repoPath);
}
