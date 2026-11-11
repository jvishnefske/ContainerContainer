package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.setmeup.MavenSettingModel;
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

class MavenSettingGeneratorServiceTest {
    @Mock
    private ArtifactoryRestRequest<BaseModel> request;

    @Mock
    private InternalRepositoryService repositoryService;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private MavenSettingGeneratorService mavenSettingGeneratorService;
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
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setType(RepoType.Maven);
        virtualRepoDescriptor.setKey("libs-release");

        try (MockedStatic<SettingsHelper> mockedSettingsHelper = Mockito.mockStatic(SettingsHelper.class)) {
            mockedSettingsHelper.when(
                            () -> SettingsHelper.getReadableVirtualRepoDescriptors(repositoryService,
                                    authorizationService))
                    .thenReturn(List.of(virtualRepoDescriptor));

            mavenSettingGeneratorService.execute(request, response);
            MavenSettingModel mavenSettingModel = (MavenSettingModel) response.getIModel();
            assertTrue(mavenSettingModel.getSnapshots().containsAll(repoKeyTypes));
            assertTrue(mavenSettingModel.getPluginReleases().containsAll(repoKeyTypes));
            assertTrue(mavenSettingModel.getPluginSnapshots().containsAll(repoKeyTypes));
            assertTrue(mavenSettingModel.getAnyMirror().containsAll(repoKeyTypes));
            assertTrue(mavenSettingModel.getReleases().containsAll(repoKeyTypes));
            assertEquals(mavenSettingModel.getSnapshots().size(), repoKeyTypes.size());
            assertEquals(mavenSettingModel.getPluginReleases().size(), repoKeyTypes.size());
            assertEquals(mavenSettingModel.getPluginSnapshots().size(), repoKeyTypes.size());
            assertEquals(mavenSettingModel.getAnyMirror().size(), repoKeyTypes.size());
            assertEquals(mavenSettingModel.getReleases().size(), repoKeyTypes.size());
        }
    }

    private Set<RepoKeyType> createRepoKeyTypes() {
        RepoKeyType repoKeyType = new RepoKeyType(RepoType.Maven, "libs-release");
        repoKeyType.setIsVirtual(true);
        return Set.of(repoKeyType);
    }
}