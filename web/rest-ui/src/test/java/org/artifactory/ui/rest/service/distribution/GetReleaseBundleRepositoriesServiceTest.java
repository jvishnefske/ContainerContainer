package org.artifactory.ui.rest.service.distribution;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetReleaseBundleRepositoriesServiceTest {

    private GetReleaseBundleRepositoriesService service;

    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = mock(AuthorizationService.class);
        service = new GetReleaseBundleRepositoriesService(
                mock(AddonsManager.class),
                mock(RepositoryService.class),
                mock(RepositoryConfigService.class),
                authorizationService
        );
    }

    @Test
    @SuppressWarnings("rawtypes")
    void execute_Success() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);
        when(authorizationService.hasReleaseBundlePermission(any(), anyBoolean(), anyBoolean())).thenReturn(true);
        service.execute(request, response);
    }
}