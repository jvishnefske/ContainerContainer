package org.artifactory.ui.rest.service.admin.configuration.repositories;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.DiffResult;
import org.apache.http.HttpStatus;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.metrics.FeatureAdoptionAction;
import org.artifactory.api.metrics.FeatureAdoptionFeature;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.config.NegotiationException;
import org.artifactory.concurrent.LockingException;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.federation.exceptions.FederatedRemoteMembersUpdateException;
import org.artifactory.metrics.framework.adoption.CommonAdoptionLabels;
import org.artifactory.metrics.framework.adoption.FeatureAdoptionService;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.validation.RepoConfigValidationException;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.CommonLocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.jfrog.security.crypto.exception.CryptoRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.artifactory.common.ConstantValues.repositoriesFederationsAttributeEnabled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author AndreiK.
 * @author Shahaf Golan
 */
class CreateRepositoryConfigServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private RepoConfigValidator repoConfigValidator;
    @Mock
    private CreateRepoConfigHelper createRepoConfigHelper;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private FederatedRepoService federatedRepoService;
    @Mock
    private FeatureAdoptionService featureAdoptionService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private RepositoryService repositoryService;

    private AutoCloseable mockitoAutoCloseable;

    private CreateRepositoryConfigService service;

    @BeforeEach
    public void setup() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        getOrCreateArtifactoryHomeStub().setProperty(repositoriesFederationsAttributeEnabled, "false");
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(FederatedRepoService.class)).thenReturn(federatedRepoService);
        ArtifactoryContextThreadBinder.bind(context);
        service = new CreateRepositoryConfigService(centralConfigService, repoConfigValidator, createRepoConfigHelper,
                projectsService, repositoryService, repositoryConfigService);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @MethodSource("repoTypesProvider")
    void testCreateRepo(String modelClass, String repoType) throws RepoConfigException {
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();
        CentralConfigDescriptorImpl descriptor = new CentralConfigDescriptorImpl();
        CommonLocalRepositoryConfigModel model = prepareModel(modelClass, repoType);

        service.createRepoConfig(response, model, descriptor, repositoryService, false);

        ArgumentCaptor<String> repoTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(repoConfigValidator).validateRepoNameForCreate(eq(repoType), repoTypeCaptor.capture());
        Assertions.assertEquals(repoType, repoTypeCaptor.getValue());
        verify(model).createRepoConfig(createRepoConfigHelper, descriptor, repositoryService, false);
    }

    @ParameterizedTest
    @MethodSource("repoModelWithNameContainingDashCacheProvider")
    void testCreateRepoFailOnDashCash(String modelClass, String repoKey) throws RepoConfigException {
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();
        CentralConfigDescriptorImpl descriptor = new CentralConfigDescriptorImpl();
        CommonLocalRepositoryConfigModel model = prepareModel(modelClass, repoKey);

        try {
            service.createRepoConfig(response, model, descriptor, repositoryService, false);
            fail("Expected exception to be thrown");
        } catch (RepoConfigException e) {
            ArgumentCaptor<String> repoTypeCaptor = ArgumentCaptor.forClass(String.class);
            verify(repoConfigValidator).validateRepoNameForCreate(eq(repoKey), repoTypeCaptor.capture());
            assertEquals(e.getMessage(),
                    StringUtils.capitalize(repoTypeCaptor.getValue()) + " repository '" + repoKey + "' with " +
                            "'-cache' suffix is not allowed");
            Assertions.assertEquals(model.getRepoType(), repoTypeCaptor.getValue());
            verify(model, never()).createRepoConfig(createRepoConfigHelper, descriptor, repositoryService, false);
        }
    }

    @Test
    void testAddMetricAddingRepoNullRepoType() {
        Assertions.assertDoesNotThrow(() -> service.addMetricRepoCreated(null));
    }

    @ParameterizedTest
    @MethodSource("testAddMetricAddingRepoTypeProvider")
    void testAddMetricAddingRepoType(RepoType repoType, FeatureAdoptionFeature featureAdoptionFeatures) {
        service.setFeatureAdoptionService(featureAdoptionService);
        service.addMetricRepoCreated(repoType);
        verify(featureAdoptionService)
                .trackAdoption(featureAdoptionFeatures, FeatureAdoptionAction.UPDATED, true,
                        CommonAdoptionLabels.EMPTY);
    }

    @Test
    void testFailOnValidateLicense() throws RepoConfigException {
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();
        CentralConfigDescriptorImpl descriptor = new CentralConfigDescriptorImpl();
        CommonLocalRepositoryConfigModel model =
                prepareModel(FederatedRepositoryConfigModel.class.getName(), RepoDetailsType.FEDERATED_REPO);

        String message = "Can't go through with this license";
        doThrow(new ForbiddenException(message)).when(federatedRepoService).validateFederationLicenseInstalled();
        try {
            service.createRepoConfig(response, model, descriptor, repositoryService, false);
            fail("Expected to get exception");
        } catch (ForbiddenException e) {
            Assertions.assertEquals(e.getMessage(), message);
        }
    }

    @Test
    void testFailOnRepoConfigValidation() throws RepoConfigException {
        CreateRepositoryConfigService serviceSpy = spy(service);
        var repoConfigMock = mock(RepoConfig.class);
        doReturn(repoConfigMock).when(serviceSpy).createRepoConfig(any(), any(), any(), any(), anyBoolean());
        doNothing().when(projectsService).validateRepoConfigIsMatchingToProjectSpec(any(), any(), any());
        var diffResultMock = mock(DiffResult.class);
        when(diffResultMock.getNumberOfDiffs()).thenReturn(1);
        when(centralConfigService.findDiff(any(), any())).thenReturn(diffResultMock);
        doNothing().when(centralConfigService).saveEditedDescriptorAndReload(any());
        doThrow(new RepoConfigValidationException("TEST")).when(repositoryConfigService)
                .upsertRepoConfig(repoConfigMock);
        doNothing().when(projectsService).removeRepositoryFromCurrentProject(any());

        var requestMock = mock(ArtifactoryRestRequest.class);
        var iModelMock = mock(RepositoryConfigModel.class);
        when(requestMock.getImodel()).thenReturn(iModelMock);
        var generalMock = mock(GeneralRepositoryConfigModel.class);
        when(generalMock.getRepoKey()).thenReturn("repo1");
        when(iModelMock.getGeneral()).thenReturn(generalMock);

        var responseMock = mock(ArtifactoryRestResponse.class);
        when(responseMock.responseCode(anyInt())).thenReturn(responseMock);
        when(responseMock.error(any())).thenReturn(responseMock);

        serviceSpy.execute(requestMock, responseMock);

        verify(responseMock, times(1)).responseCode(HttpStatus.SC_BAD_REQUEST);
        verify(responseMock, times(1)).error("Failed to create repository: TEST");
        verify(projectsService, times(1)).removeRepositoryFromCurrentProject("repo1");
    }

    @Test
    void testHandleRepoConfigException_shouldRemoveFromProjectAndSetResponse() throws RepoConfigException {
        // A RepoConfigException from env validation is handled by its dedicated catch: the response uses the
        // exception's own status code, the standard "Failed to create repository <key>: <message>" wrapping, and
        // still cleans up the project assignment.
        CreateRepositoryConfigService serviceSpy = spy(service);
        doThrow(new RepoConfigException("Assigning environments is not allowed for this repository type",
                HttpStatus.SC_BAD_REQUEST)).when(repoConfigValidator).validateEnvironmentsForRepoType(any());
        doNothing().when(projectsService).removeRepositoryFromCurrentProject(any());

        var requestMock = mock(ArtifactoryRestRequest.class);
        var iModelMock = mock(RepositoryConfigModel.class);
        when(requestMock.getImodel()).thenReturn(iModelMock);
        var generalMock = mock(GeneralRepositoryConfigModel.class);
        when(generalMock.getRepoKey()).thenReturn("repo1");
        when(iModelMock.getGeneral()).thenReturn(generalMock);

        var responseMock = mock(ArtifactoryRestResponse.class);
        when(responseMock.responseCode(anyInt())).thenReturn(responseMock);
        when(responseMock.error(any())).thenReturn(responseMock);

        serviceSpy.execute(requestMock, responseMock);

        verify(responseMock, times(1)).responseCode(HttpStatus.SC_BAD_REQUEST);
        verify(responseMock, times(1))
                .error("Failed to create repository repo1: Assigning environments is not allowed for this "
                        + "repository type");
        verify(projectsService, times(1)).removeRepositoryFromCurrentProject("repo1");
    }

    @Test
    void testHandleCryptoRuntimeException() throws RepoConfigException {
        CreateRepositoryConfigService serviceSpy = spy(service);
        var repoConfigMock = mock(RepoConfig.class);
        doReturn(repoConfigMock).when(serviceSpy).createRepoConfig(any(), any(), any(), any(), anyBoolean());
        doThrow(new CryptoRuntimeException("Decryption error")).when(serviceSpy)
                .createRepoConfig(any(), any(), any(), any(), anyBoolean());

        var requestMock = mock(ArtifactoryRestRequest.class);
        var iModelMock = mock(RepositoryConfigModel.class);
        when(requestMock.getImodel()).thenReturn(iModelMock);
        var generalMock = mock(GeneralRepositoryConfigModel.class);
        when(generalMock.getRepoKey()).thenReturn("repo1");
        when(iModelMock.getGeneral()).thenReturn(generalMock);

        var responseMock = mock(ArtifactoryRestResponse.class);
        when(responseMock.responseCode(anyInt())).thenReturn(responseMock);
        when(responseMock.error(any())).thenReturn(responseMock);

        serviceSpy.execute(requestMock, responseMock);

        verify(responseMock, times(1)).responseCode(HttpStatus.SC_BAD_REQUEST);
        verify(responseMock, times(1)).error("The password could not be decrypted");
        verify(projectsService, times(1)).removeRepositoryFromCurrentProject("repo1");
    }

    @Test
    void testSuccessExecuteFlow() throws RepoConfigException {
        CreateRepositoryConfigService serviceSpy = spy(service);
        var repoConfigMock = mock(RepoConfig.class);
        doReturn(repoConfigMock).when(serviceSpy).createRepoConfig(any(), any(), any(), any(), anyBoolean());
        doNothing().when(projectsService).validateRepoConfigIsMatchingToProjectSpec(any(), any(), any());
        var diffResultMock = mock(DiffResult.class);
        when(diffResultMock.getNumberOfDiffs()).thenReturn(1);
        when(centralConfigService.findDiff(any(), any())).thenReturn(diffResultMock);
        doNothing().when(centralConfigService).saveEditedDescriptorAndReload(any());
        doNothing().when(repositoryConfigService).upsertRepoConfig(repoConfigMock);
        doNothing().when(projectsService).removeRepositoryFromCurrentProject(any());

        var requestMock = mock(ArtifactoryRestRequest.class);
        var iModelMock = mock(RepositoryConfigModel.class);
        when(requestMock.getImodel()).thenReturn(iModelMock);
        var generalMock = mock(GeneralRepositoryConfigModel.class);
        when(generalMock.getRepoKey()).thenReturn("repo1");
        when(iModelMock.getGeneral()).thenReturn(generalMock);
        var typeSpecificMock = mock(TypeSpecificConfigModel.class);
        when(iModelMock.getTypeSpecific()).thenReturn(typeSpecificMock);
        when(typeSpecificMock.getRepoType()).thenReturn(RepoType.Docker);

        var responseMock = mock(ArtifactoryRestResponse.class);
        when(responseMock.responseCode(anyInt())).thenReturn(responseMock);
        when(responseMock.error(any())).thenReturn(responseMock);

        serviceSpy.execute(requestMock, responseMock);

        verify(responseMock, times(1)).info("Successfully added repository 'repo1'");
    }

    @Test
    void testHandleLockingException_shouldRemoveFromProjectAndRethrow() throws RepoConfigException {
        CreateRepositoryConfigService serviceSpy = spy(service);
        doThrow(new LockingException("Lock failed")).when(serviceSpy).createRepoConfig(any(), any(), any(), any(),
                anyBoolean());

        var requestMock = mock(ArtifactoryRestRequest.class);
        var iModelMock = mock(RepositoryConfigModel.class);
        when(requestMock.getImodel()).thenReturn(iModelMock);
        var generalMock = mock(GeneralRepositoryConfigModel.class);
        when(generalMock.getRepoKey()).thenReturn("repo1");
        when(iModelMock.getGeneral()).thenReturn(generalMock);

        var responseMock = mock(ArtifactoryRestResponse.class);

        Assertions.assertThrows(LockingException.class, () -> serviceSpy.execute(requestMock, responseMock));
        verify(projectsService, times(1)).removeRepositoryFromCurrentProject("repo1");
    }

    @Test
    void testHandleNegotiationException_shouldRemoveFromProjectAndSetResponse() throws RepoConfigException {
        CreateRepositoryConfigService serviceSpy = spy(service);
        var repoConfigMock = mock(RepoConfig.class);
        doReturn(repoConfigMock).when(serviceSpy).createRepoConfig(any(), any(), any(), any(), anyBoolean());
        doNothing().when(projectsService).validateRepoConfigIsMatchingToProjectSpec(any(), any(), any());
        var diffResultMock = mock(DiffResult.class);
        when(diffResultMock.getNumberOfDiffs()).thenReturn(1);
        when(centralConfigService.findDiff(any(), any())).thenReturn(diffResultMock);
        doNothing().when(centralConfigService).saveEditedDescriptorAndReload(any());
        doThrow(new NegotiationException("Negotiation failed")).when(repositoryConfigService)
                .upsertRepoConfig(repoConfigMock);

        var requestMock = mock(ArtifactoryRestRequest.class);
        var iModelMock = mock(RepositoryConfigModel.class);
        when(requestMock.getImodel()).thenReturn(iModelMock);
        var generalMock = mock(GeneralRepositoryConfigModel.class);
        when(generalMock.getRepoKey()).thenReturn("repo1");
        when(iModelMock.getGeneral()).thenReturn(generalMock);

        var responseMock = mock(ArtifactoryRestResponse.class);
        when(responseMock.responseCode(anyInt())).thenReturn(responseMock);
        when(responseMock.entity(any())).thenReturn(responseMock);

        serviceSpy.execute(requestMock, responseMock);

        verify(responseMock, times(1)).responseCode(HttpStatus.SC_BAD_REQUEST);
        verify(responseMock, times(1)).entity(any());
        verify(projectsService, times(1)).removeRepositoryFromCurrentProject("repo1");
    }

    @Test
    void testHandleFederatedRemoteMembersUpdateException_shouldRemoveFromProject() throws RepoConfigException {
        CreateRepositoryConfigService serviceSpy = spy(service);
        var repoConfigMock = mock(RepoConfig.class);
        doReturn(repoConfigMock).when(serviceSpy).createRepoConfig(any(), any(), any(), any(), anyBoolean());
        doNothing().when(projectsService).validateRepoConfigIsMatchingToProjectSpec(any(), any(), any());
        var diffResultMock = mock(DiffResult.class);
        when(diffResultMock.getNumberOfDiffs()).thenReturn(1);
        when(centralConfigService.findDiff(any(), any())).thenReturn(diffResultMock);
        doNothing().when(centralConfigService).saveEditedDescriptorAndReload(any());
        doThrow(new FederatedRemoteMembersUpdateException(List.of("url1"), "Federated update failed"))
                .when(repositoryConfigService)
                .upsertRepoConfig(repoConfigMock);

        var requestMock = mock(ArtifactoryRestRequest.class);
        var iModelMock = mock(RepositoryConfigModel.class);
        when(requestMock.getImodel()).thenReturn(iModelMock);
        var generalMock = mock(GeneralRepositoryConfigModel.class);
        when(generalMock.getRepoKey()).thenReturn("repo1");
        when(iModelMock.getGeneral()).thenReturn(generalMock);

        var responseMock = mock(ArtifactoryRestResponse.class);
        when(responseMock.error(any(String.class))).thenReturn(responseMock);

        serviceSpy.execute(requestMock, responseMock);

        verify(responseMock, times(1)).error("Federated update failed");
        verify(projectsService, times(1)).removeRepositoryFromCurrentProject("repo1");
    }

    @Nonnull
    private CommonLocalRepositoryConfigModel prepareModel(String modelClass, String repoKey) {
        CommonLocalRepositoryConfigModel model = null;
        if (LocalRepositoryConfigModel.class.getName().equals(modelClass)) {
            model = spy(new LocalRepositoryConfigModel());
        } else if (FederatedRepositoryConfigModel.class.getName().equals(modelClass)) {
            model = spy(new FederatedRepositoryConfigModel());
        }
        GeneralRepositoryConfigModel generalModel = new GeneralRepositoryConfigModel();
        generalModel.setRepoKey(repoKey);
        Assertions.assertNotNull(model, "Unrecognized class name was provided");
        model.setGeneral(generalModel);
        return model;
    }

    static Object[][] repoTypesProvider() {
        return new Object[][] {
                {LocalRepositoryConfigModel.class.getName(), RepoDetailsType.LOCAL_REPO},
                {FederatedRepositoryConfigModel.class.getName(), RepoDetailsType.FEDERATED_REPO},
        };
    }

    static Object[][] repoModelWithNameContainingDashCacheProvider() {
        return new Object[][] {
                {LocalRepositoryConfigModel.class.getName(), RepoDetailsType.LOCAL_REPO + "-cache"},
                {FederatedRepositoryConfigModel.class.getName(), RepoDetailsType.FEDERATED_REPO + "-cache"},
        };
    }

    static Object[][] testAddMetricAddingRepoTypeProvider() {
        return new Object[][] {
                {RepoType.Pub, FeatureAdoptionFeature.PUB_REPOSITORY},
                {RepoType.Swift, FeatureAdoptionFeature.SWIFT_REPOSITORY},
                {RepoType.Terraform, FeatureAdoptionFeature.TERRAFORM_REPOSITORY},
                {RepoType.TerraformBackend, FeatureAdoptionFeature.TERRAFORM_BACKEND_REPOSITORY},
        };
    }
}