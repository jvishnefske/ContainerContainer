package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import org.apache.catalina.connector.Response;
import org.apache.http.HttpStatus;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.repo.PackageType;
import org.artifactory.rest.common.model.restore.RestoreLayoutRepo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Shahafg
 */
@SuppressWarnings("rawtypes")
class RestoreLayoutServiceTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private SecurityService securityService;
    @Mock
    private ArtifactoryRestRequest<RestoreLayoutRepo> request;
    @Mock
    private RestoreHFRepositoryLayoutService restoreLayoutService;
    @Mock
    private ArtifactoryContext context;
    private AutoCloseable mocks;
    private RestoreLayoutService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        homeStub.setProperty(ConstantValues.huggingFaceMLEnableFrogMLLayout, "true");
        service = Mockito.spy(new RestoreLayoutService());
        service.setSecurityService(securityService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void executeWithNonAdminUser() {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        RestoreLayoutRepo restoreLayoutRepo = new RestoreLayoutRepo("unauthorized-repo", PackageType.HUGGINGFACEML);
        when(request.getImodel()).thenReturn(restoreLayoutRepo);
        when(securityService.isAdmin()).thenReturn(false);

        service.execute(request, response);

        verify(restoreLayoutService, times(0)).execute(request, response);
        assertEquals(HttpStatus.SC_FORBIDDEN, response.getResponseCode());
    }

    @Test
    void executeWithNonHuggingFacePackageType() {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        RestoreLayoutRepo restoreLayoutRepo = new RestoreLayoutRepo("some-repo", PackageType.GENERIC);
        when(request.getImodel()).thenReturn(restoreLayoutRepo);
        when(securityService.isAdmin()).thenReturn(true);

        service.execute(request, response);

        verify(restoreLayoutService, times(0)).execute(request, response);
        assertEquals("Allowed for HUGGING_FACE_ML only", response.getFeedbackMsg().getError());
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void executeWithHuggingFacePackageType() {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        RestoreLayoutRepo restoreLayoutRepo = new RestoreLayoutRepo("huggingface-repo", PackageType.HUGGINGFACEML);
        when(request.getImodel()).thenReturn(restoreLayoutRepo);
        when(securityService.isAdmin()).thenReturn(true);

        when(service.createRestoreService()).thenReturn(restoreLayoutService);
        service.execute(request, response);

        verify(restoreLayoutService, times(1)).execute(request, response);
    }

    @Test
    void migrationForbiddenWhenHuggingFaceMLEnableFrogMLLayoutIsFalse() {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        homeStub.setProperty(ConstantValues.huggingFaceMLEnableFrogMLLayout, "false");
        RestoreLayoutRepo restoreLayoutRepo = new RestoreLayoutRepo("repo", PackageType.HUGGINGFACEML);
        when(request.getImodel()).thenReturn(restoreLayoutRepo);

        service.execute(request, response);
        assertEquals(Response.SC_METHOD_NOT_ALLOWED, response.getResponseCode());
        assertEquals("Hugging Face ML layout is disabled", response.getFeedbackMsg().getError());
    }
}