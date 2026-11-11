package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.properties;

import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.property.Property;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.properties.PropertiesArtifactInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.artifactory.addon.NuGetAddon.PATH_PARAM;
import static org.artifactory.addon.NuGetAddon.REPO_KEY_PARAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PropertyServiceLimitationsTest {

    private InternalRepositoryService repoService;
    private PropertiesService propsService;
    private AuthorizationService authorizationService;
    private SecurityService securityService;
    private MetadataServiceIndexHandler metadataServiceIndexHandler;
    private ArtifactoryRestRequest request;
    private RestResponse response;

    @BeforeEach
    void setUp() {
        repoService = mock(InternalRepositoryService.class);
        propsService = mock(PropertiesService.class);
        authorizationService = mock(AuthorizationService.class);
        securityService = mock(SecurityService.class);
        metadataServiceIndexHandler = mock(MetadataServiceIndexHandler.class);
        request = mock(ArtifactoryRestRequest.class);
        when(request.getQueryParamByKey(REPO_KEY_PARAM)).thenReturn("repokey");
        when(request.getQueryParamByKey(PATH_PARAM)).thenReturn("path");
        response = new ArtifactoryRestResponse();
    }

    @Test
    void whenUserCantAnnotateThenForbidden() {
        whenUserCantAnnotateThenForbidden(
                new CreatePropertyService(repoService, propsService, authorizationService, securityService,
                        metadataServiceIndexHandler));
        whenUserCantAnnotateThenForbidden(
                new UpdatePropertyService(repoService, propsService, authorizationService, securityService,
                        metadataServiceIndexHandler));
    }

    @Test
    void whenNonLocalRepoThenBadRequest() {
        whenNonLocalRepoThenBadRequest(
                new CreatePropertyService(repoService, propsService, authorizationService, securityService,
                        metadataServiceIndexHandler));
        whenNonLocalRepoThenBadRequest(
                new UpdatePropertyService(repoService, propsService, authorizationService, securityService,
                        metadataServiceIndexHandler));
    }

    @Test
    void whenAllCheckPassedThenOk() {
        whenAllCheckPassedThenOk(
                new CreatePropertyService(repoService, propsService, authorizationService, securityService,
                        metadataServiceIndexHandler));
        whenAllCheckPassedThenOk(
                new UpdatePropertyService(repoService, propsService, authorizationService, securityService,
                        metadataServiceIndexHandler));
    }

    private void whenAllCheckPassedThenOk(RestService service) {
        when(authorizationService.canAnnotate(any())).thenReturn(true);
        LocalRepo localRepo = mock(LocalRepo.class);
        when(repoService.localFederatedOrCachedRepositoryByKey(any())).thenReturn(localRepo);
        PropertiesArtifactInfo info = new PropertiesArtifactInfo();
        Property property = new Property();
        property.setName("goodName");
        info.setProperty(property);
        when(request.getImodel()).thenReturn(info);
        service.execute(request, response);

        assertEquals(201, response.getResponseCode());
    }

    void whenUserCantAnnotateThenForbidden(RestService service) {
        when(authorizationService.canAnnotate(any())).thenReturn(false);

        service.execute(request, response);

        assertEquals(403, response.getResponseCode());
    }

    void whenNonLocalRepoThenBadRequest(RestService service) {
        when(authorizationService.canAnnotate(any())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.execute(request, response));
    }
}