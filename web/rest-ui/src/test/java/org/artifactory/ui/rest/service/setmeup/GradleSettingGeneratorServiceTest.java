package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.setmeup.GradleSettingModel;
import org.artifactory.ui.rest.service.utils.setMeUp.SettingsHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GradleSettingGeneratorServiceTest {

    @Mock
    private RepositoryConfigService repositoryConfigService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private InternalRepositoryService repositoryService;

    @Mock
    private CentralConfigService centralConfigService;

    @Mock
    CentralConfigDescriptor descriptor;

    MockedStatic<SettingsHelper> settingsHelper;

    AutoCloseable closeable;

    private GradleSettingGeneratorService gradleSettingGeneratorService;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        gradleSettingGeneratorService = new GradleSettingGeneratorService();
        gradleSettingGeneratorService.setAuthorizationService(authorizationService);
        gradleSettingGeneratorService.setCentralConfigService(centralConfigService);
        gradleSettingGeneratorService.setRepositoryService(repositoryService);
        gradleSettingGeneratorService.setRepositoryConfigService(repositoryConfigService);
        settingsHelper = mockStatic(SettingsHelper.class);
    }

    @AfterEach
    protected void tearDown() throws Exception {
        closeable.close();
        settingsHelper.close();
    }

    @Test
    void execute() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", RepoType.Maven);
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtual-repo", RepoType.Maven);
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("federated-repo", RepoType.Maven);
        List<RepoConfig> repoConfigs = List.of(localRepoConfig, virtualRepoConfig, federatedRepoConfig);
        RepoLayout layout = mock(RepoLayout.class);
        when(layout.getName()).thenReturn("layout");
        settingsHelper.when(
                        () -> SettingsHelper.getReadableRepoConfigs(
                                Set.of(RepoDetailsType.LOCAL, RepoDetailsType.FEDERATED, RepoDetailsType.VIRTUAL),
                                repositoryConfigService,
                                authorizationService))
                .thenReturn(repoConfigs);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getRepoLayouts()).thenReturn(List.of(layout));

        ArtifactoryRestRequest<BaseModel> request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();

        // Execute the method
        gradleSettingGeneratorService.execute(request, response);


        // Verify the interactions and state
        GradleSettingModel capturedModel = (GradleSettingModel) response.getIModel();
        assertEquals(1, capturedModel.getLayouts().size());
        // real
        assertEquals(2, capturedModel.getLibsPublisher().size());
        // virtual
        assertEquals(1, capturedModel.getPluginResolver().size());
        assertEquals(1, capturedModel.getLibsResolver().size());
        assertEquals(1, capturedModel.getLayouts().size());
    }
}