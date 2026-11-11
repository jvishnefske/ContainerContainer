package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateDefaultJcenterRepoService.DEFAULT_JCENTER_NAME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
class CreateDefaultJcenterRepoServiceTest {

    @Test
    void testGuessNameForJcenter() {
        CentralConfigService configService = mock(CentralConfigService.class);
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create(DEFAULT_JCENTER_NAME, RepoType.Generic);
        when(repositoryConfigService.getRepoConfigsByTypes(Set.of(LOCAL, FEDERATED))).thenReturn(
                List.of(federatedRepoConfig));
        CreateDefaultJcenterRepoService repoService =
                new CreateDefaultJcenterRepoService(configService, null, repositoryConfigService, null);
        Assertions.assertEquals(DEFAULT_JCENTER_NAME + "-default", repoService.guessNameForJcenter());
    }
}