package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.apache.http.HttpStatus;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.MetadataCachePeriodExclusionRequestModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.MetadataCachePeriodExclusionResponseModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataCachePeriodExclusionServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryRestRequest<MetadataCachePeriodExclusionRequestModel> request;
    @Mock
    private ArtifactoryRestResponse response;

    private MetadataCachePeriodExclusionService service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContext context = org.mockito.Mockito.mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        getBound().setProperty(ConstantValues.remoteRepoEnforceMinimumMetadataCachePeriod, "true");
        getBound().setProperty(ConstantValues.remoteRepoMinMetadataCachePeriodWhitelistUrls, "");
        getBound().setProperty(ConstantValues.remoteRepoCustomUpstreamMinCachePeriodRules,
                "chocolatey.org|NuGet|14400");
        service = new MetadataCachePeriodExclusionService();
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void execute_shouldReturnDenied_whenValueBelowEnforcedMinimum() {
        MetadataCachePeriodExclusionRequestModel model = new MetadataCachePeriodExclusionRequestModel();
        model.setPackageType("Maven");
        model.setUrl("https://repo1.maven.org/maven2");
        model.setRetrievalCachePeriodSecs(10L);
        when(request.getImodel()).thenReturn(model);

        service.execute(request, response);

        ArgumentCaptor<MetadataCachePeriodExclusionResponseModel> captor =
                ArgumentCaptor.forClass(MetadataCachePeriodExclusionResponseModel.class);
        verify(response).iModel(captor.capture());
        verify(response).responseCode(HttpStatus.SC_OK);

        MetadataCachePeriodExclusionResponseModel result = captor.getValue();
        assertFalse(result.isAllowed());
    }

    @Test
    void execute_shouldReturnAllowed_whenValueMeetsEnforcedMinimum() {
        MetadataCachePeriodExclusionRequestModel model = new MetadataCachePeriodExclusionRequestModel();
        model.setPackageType("Maven");
        model.setUrl("https://repo1.maven.org/maven2");
        model.setRetrievalCachePeriodSecs(30L);
        when(request.getImodel()).thenReturn(model);

        service.execute(request, response);

        ArgumentCaptor<MetadataCachePeriodExclusionResponseModel> captor =
                ArgumentCaptor.forClass(MetadataCachePeriodExclusionResponseModel.class);
        verify(response).iModel(captor.capture());

        MetadataCachePeriodExclusionResponseModel result = captor.getValue();
        assertTrue(result.isAllowed());
    }

    @Test
    void execute_shouldReturnAllowed_whenSmartRepositoryEnabledWithLowValue() {
        MetadataCachePeriodExclusionRequestModel model = new MetadataCachePeriodExclusionRequestModel();
        model.setPackageType("Maven");
        model.setUrl("https://acme.jfrog.io/artifactory/maven-remote");
        model.setRetrievalCachePeriodSecs(5L);
        model.setContentSynchronisationEnabled(true);
        when(request.getImodel()).thenReturn(model);

        service.execute(request, response);

        ArgumentCaptor<MetadataCachePeriodExclusionResponseModel> captor =
                ArgumentCaptor.forClass(MetadataCachePeriodExclusionResponseModel.class);
        verify(response).iModel(captor.capture());

        MetadataCachePeriodExclusionResponseModel result = captor.getValue();
        assertTrue(result.isAllowed());
    }

    @Test
    void execute_shouldReturnDenied_whenValueBelowCustomUpstreamRule() {
        MetadataCachePeriodExclusionRequestModel model = new MetadataCachePeriodExclusionRequestModel();
        model.setPackageType("NuGet");
        model.setUrl("https://chocolatey.org/api/v2");
        model.setRetrievalCachePeriodSecs(30L);
        when(request.getImodel()).thenReturn(model);

        service.execute(request, response);

        ArgumentCaptor<MetadataCachePeriodExclusionResponseModel> captor =
                ArgumentCaptor.forClass(MetadataCachePeriodExclusionResponseModel.class);
        verify(response).iModel(captor.capture());

        MetadataCachePeriodExclusionResponseModel result = captor.getValue();
        assertFalse(result.isAllowed());
    }

    @Test
    void execute_shouldReturnAllowed_whenPackageTypeIsGeneric() {
        MetadataCachePeriodExclusionRequestModel model = new MetadataCachePeriodExclusionRequestModel();
        model.setPackageType("Generic");
        model.setUrl("https://repo1.maven.org/maven2");
        model.setRetrievalCachePeriodSecs(10L);
        when(request.getImodel()).thenReturn(model);

        service.execute(request, response);

        ArgumentCaptor<MetadataCachePeriodExclusionResponseModel> captor =
                ArgumentCaptor.forClass(MetadataCachePeriodExclusionResponseModel.class);
        verify(response).iModel(captor.capture());

        MetadataCachePeriodExclusionResponseModel result = captor.getValue();
        assertTrue(result.isAllowed());
    }

    @Test
    void execute_shouldThrow_whenRequestBodyMissing() {
        when(request.getImodel()).thenReturn(null);

        BadRequestException exception =
                assertThrows(BadRequestException.class, () -> service.execute(request, response));

        assertEquals("Request body is required", exception.getMessage());
    }

    @Test
    void execute_shouldThrow_whenPackageTypeMissing() {
        MetadataCachePeriodExclusionRequestModel model = new MetadataCachePeriodExclusionRequestModel();
        model.setUrl("https://repo1.maven.org/maven2");
        model.setRetrievalCachePeriodSecs(30L);
        when(request.getImodel()).thenReturn(model);

        BadRequestException exception =
                assertThrows(BadRequestException.class, () -> service.execute(request, response));

        assertEquals("packageType must be provided", exception.getMessage());
    }

    @Test
    void execute_shouldThrow_whenRetrievalCachePeriodMissing() {
        MetadataCachePeriodExclusionRequestModel model = new MetadataCachePeriodExclusionRequestModel();
        model.setPackageType("Maven");
        model.setUrl("https://repo1.maven.org/maven2");
        when(request.getImodel()).thenReturn(model);

        assertThrows(BadRequestException.class, () -> service.execute(request, response));
    }

    @Test
    void execute_shouldThrow_whenUrlMissing() {
        MetadataCachePeriodExclusionRequestModel model = new MetadataCachePeriodExclusionRequestModel();
        model.setPackageType("Maven");
        when(request.getImodel()).thenReturn(model);

        assertThrows(BadRequestException.class, () -> service.execute(request, response));
    }

    @Test
    void execute_shouldThrow_whenPackageTypeUnknown() {
        MetadataCachePeriodExclusionRequestModel model = new MetadataCachePeriodExclusionRequestModel();
        model.setPackageType("not-a-real-type");
        model.setUrl("https://repo1.maven.org/maven2");
        model.setRetrievalCachePeriodSecs(30L);
        when(request.getImodel()).thenReturn(model);

        assertThrows(BadRequestException.class, () -> service.execute(request, response));
    }
}
