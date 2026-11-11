package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.setmeup.IvySettingModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.ui.rest.service.utils.setMeUp.SettingsHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IvySettingGeneratorServiceTest {

    @Mock
    private ArtifactoryRestRequest<BaseModel> request;

    @Mock
    private InternalRepositoryService repositoryService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private CentralConfigService centralConfigService;

    @InjectMocks
    private IvySettingGeneratorService ivySettingGeneratorService;
    private AutoCloseable mocks;
    private RestResponse response;


    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        response = new ArtifactoryRestResponse();
    }

    @AfterEach
    public void teardown() throws Exception {
        mocks.close();
    }

    @Test
    void testExecute() {
        Set<RepoKeyType> repoKeyTypes = createRepoKeyTypes();
        List<String> repoLayouts = List.of("maven-2-default");
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setType(RepoType.Maven);
        virtualRepoDescriptor.setKey("libs-release");

        try (MockedStatic<SettingsHelper> mockedSettingsHelper = Mockito.mockStatic(SettingsHelper.class)) {
            mockedSettingsHelper.when(
                            () -> SettingsHelper.getReadableVirtualRepoDescriptors(repositoryService,
                                    authorizationService))
                    .thenReturn(List.of(virtualRepoDescriptor));

            RepoLayout layout = new RepoLayout();
            layout.setName("maven-2-default");
            CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
            when(centralConfigService.getDescriptor()).thenReturn(descriptor);
            when(descriptor.getRepoLayouts()).thenReturn(List.of(layout));

            ivySettingGeneratorService.execute(request, response);
            IvySettingModel ivySettingModel = (IvySettingModel) response.getIModel();
            Set<RepoKeyType> actualRepoKeyTypes = ivySettingModel.getLibsRepository();
            List<String> actualRepoLayouts = ivySettingModel.getLibsRepositoryLayout();
            assertEquals(repoLayouts, actualRepoLayouts);
            assertEquals(actualRepoKeyTypes.size(), repoKeyTypes.size());
            repoKeyTypes.forEach(repoKeyType -> assertTrue(actualRepoKeyTypes.contains(repoKeyType)));
        }
    }

    private Set<RepoKeyType> createRepoKeyTypes() {
        RepoKeyType repoKeyType = new RepoKeyType(RepoType.Maven, "libs-release");
        repoKeyType.setIsVirtual(true);
        return Set.of(repoKeyType);
    }
}