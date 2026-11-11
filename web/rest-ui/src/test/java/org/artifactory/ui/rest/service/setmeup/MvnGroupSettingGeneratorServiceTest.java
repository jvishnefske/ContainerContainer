package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MvnGroupSettingGeneratorServiceTest {

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private CentralConfigService centralConfigService;

    @Mock
    private InternalRepositoryService repositoryService;

    @InjectMocks
    private MvnGroupSettingGeneratorService<BaseModel> mvnGroupSettingGeneratorService =
            new MvnGroupSettingGeneratorService<>() {
                @Override
                public void execute(ArtifactoryRestRequest<BaseModel> request, RestResponse response) {
                    // No-op for testing
                }
            };

    @Test
    void setAuthorizationServiceSetsService() {
        mvnGroupSettingGeneratorService.setAuthorizationService(authorizationService);
        assertEquals(authorizationService, mvnGroupSettingGeneratorService.authorizationService);
    }

    @Test
    void setCentralConfigServiceSetsService() {
        mvnGroupSettingGeneratorService.setCentralConfigService(centralConfigService);
        assertEquals(centralConfigService, mvnGroupSettingGeneratorService.centralConfigService);
    }

    @Test
    void setRepositoryServiceSetsService() {
        mvnGroupSettingGeneratorService.setRepositoryService(repositoryService);
        assertEquals(repositoryService, mvnGroupSettingGeneratorService.repositoryService);
    }
}