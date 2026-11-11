package org.artifactory.ui.rest.service.admin.security.keys.keypairs;

import org.artifactory.addon.common.keys.KeyPairService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.security.keys.KeyPairIModel;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * @author Alexei Vainshtein
 */
public class GetAllKeyPairsServiceTest {

    @Test
    public void testGetListOfReposPerKeyPair() {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        List<RepoConfig> repoConfigs = new ArrayList<>();
        FederatedRepoConfig federatedRepo = FederatedRepoConfig.create("repoKey", RepoType.Generic);
        federatedRepo.getSecurityConfig().setPrimaryKeyPairRef("key");
        repoConfigs.add(federatedRepo);
        when(repositoryConfigService.getRepoConfigsByTypes(Set.of(LOCAL, FEDERATED, VIRTUAL))).thenReturn(repoConfigs);
        GetAllKeyPairsService service = new GetAllKeyPairsService(repositoryConfigService, mock(KeyPairService.class));

        List<String> keys = service.getListOfReposPerKeyPair("key");

        assertEquals(keys.size(), repoConfigs.size());
    }

    @Test
    public void testExecute() {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        KeyPairDescriptor keyPair = new KeyPairDescriptor();
        keyPair.setKey("key");
        keyPair.setAlias("alias");
        keyPair.setKeyType(KeyPairDescriptor.RSA_TYPE);
        keyPair.setVaultType(KeyPairDescriptor.VAULT);
        keyPair.setVaultKey(KeyPairDescriptor.VAULT);

        KeyPairService keyPairService = mock(KeyPairService.class);
        when(keyPairService.getKeyPairs(anyCollection())).thenReturn(List.of(keyPair));
        GetAllKeyPairsService service = new GetAllKeyPairsService(repositoryConfigService, keyPairService);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();

        service.execute(request, response);

        assertNotNull(response.getiModelList());
        KeyPairIModel keyPairIModel = (KeyPairIModel) response.getiModelList().iterator().next();
        assertEquals(keyPairIModel.getPairName(), keyPair.getKey());
        assertEquals(keyPairIModel.getAlias(), keyPair.getAlias());
        assertEquals(keyPairIModel.getSource(), keyPair.getVaultType());
        assertEquals(keyPairIModel.getPairType(), keyPair.getKeyType());
        assertEquals(keyPairIModel.getVaultKey(), keyPair.getVaultKey());
    }
}