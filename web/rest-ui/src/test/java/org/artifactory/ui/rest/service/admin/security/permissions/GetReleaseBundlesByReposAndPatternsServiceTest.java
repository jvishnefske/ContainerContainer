package org.artifactory.ui.rest.service.admin.security.permissions;

import com.jfrog.bh.acl.producer.bridge.BundleArtifactoryProducerBridge;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.api.rest.distribution.bundle.models.BundlesNamesResponse;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.ui.rest.model.distribution.ReleaseBundlePermissionIModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.artifactory.security.ArtifactoryPermission.MANAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

class GetReleaseBundlesByReposAndPatternsServiceTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private BundleArtifactoryProducerBridge bundleProducerBridge;
    @Mock
    private ReleaseBundleAddon releaseBundleAddon;

    @InjectMocks
    private GetReleaseBundlesByReposAndPatternsService service;


    private AutoCloseable mockitoAutoCloseable;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
    }

    @Test
    void getExecute_noPermissions() {
        ArtifactoryRestRequest<ReleaseBundlePermissionIModel> request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);

        when(authorizationService.hasReleaseBundlePermission(MANAGE, false, false)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> service.execute(request, response));
    }

    @Test
    void getExecute() {
        ReleaseBundlePermissionIModel model = new ReleaseBundlePermissionIModel();
        BundlesNamesResponse bundlesNamesResponse = new BundlesNamesResponse(List.of("bundle1", "bundle2"));

        ArtifactoryRestRequest<ReleaseBundlePermissionIModel> request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();

        when(request.getImodel()).thenReturn(model);
        when(authorizationService.hasReleaseBundlePermission(MANAGE, false, false)).thenReturn(true);
        when(addonsManager.addonByType(ReleaseBundleAddon.class)).thenReturn(releaseBundleAddon);
        when(releaseBundleAddon.getBundlesByReposAndPatterns(model.getRepositories(), model.getIncludePatterns(),
                model.getExcludePatterns(), model.getBundleType())).thenReturn(bundlesNamesResponse);

        service.execute(request, response);

        assertNotNull(response.getIModel());
        assertEquals("{\"bundles\":[\"bundle1\",\"bundle2\"]}", response.getIModel());
    }
}