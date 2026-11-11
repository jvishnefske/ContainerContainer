package org.artifactory.metrics.providers.features;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.mockito.Mockito.when;

public class RepositoriesFeatureTest {

    @Mock
    private CentralConfigService centralConfigService;

    @Mock
    private InternalRepositoryService repositoryService;

    @Mock
    private StorageSummaryInfo storageSummary;
    @Mock
    private StorageSummaryService storageSummaryService;

    @Mock
    private CentralConfigDescriptor descriptor;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getFederatedRepoUrlBaseOrDefault()).thenReturn("https://local");
        when(descriptor.getTrashcanConfig()).thenReturn(new TrashcanConfigDescriptor());
        when(descriptor.getFolderDownloadConfig()).thenReturn(new FolderDownloadConfigDescriptor());

        Map<String, FederatedRepoConfig> federatedRepoConfigs = new HashMap<>();
        federatedRepoConfigs.put("fed1",
                fedRepo("fed1", true, descriptor.getFederatedRepoUrlBaseOrDefault(), RepoType.Debian));
        federatedRepoConfigs.put("fed2",
                fedRepo("fed2", false, descriptor.getFederatedRepoUrlBaseOrDefault(), RepoType.Docker));

        when(repositoryService.getLocalFederatedAndRemoteRepoConfigs()).thenReturn(
                List.copyOf(federatedRepoConfigs.values()));

        when(storageSummaryService.get()).thenReturn(storageSummary);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    private FederatedRepoConfig fedRepo(String key, boolean enabled, String urlBase, RepoType type) {
        FederatedRepoConfig descriptor = FederatedRepoConfig.create(key, type);

        FederatedMember local = new FederatedMember();
        local.setUrl(urlBase + "/artifactory/" + key);
        local.setEnabled(enabled);

        FederatedMember remote1 = new FederatedMember();
        remote1.setUrl(urlBase + "/artifactory/" + key + "1");

        FederatedMember remote2 = new FederatedMember();
        remote2.setUrl("https://remote/artifactory/" + key + "2");

        descriptor.getRepoTypeConfig().getFederatedMembers().add(local);
        descriptor.getRepoTypeConfig().getFederatedMembers().add(remote1);
        descriptor.getRepoTypeConfig().getFederatedMembers().add(remote2);

        return descriptor;
    }

    @Test
    public void testFederatedRepositoriesFeature() {
        RepositoriesFeature repositoriesFeature =
                new RepositoriesFeature(centralConfigService, repositoryService, storageSummaryService);
        JsonNode featureGroup = new ObjectMapper().valueToTree(repositoriesFeature.getFeature());

        Iterable<JsonNode> iterable = () -> featureGroup.get("features").elements();
        Optional<JsonNode> fedRepos = StreamSupport.stream(iterable.spliterator(), false)
                .filter(node -> node.get("name").asText().equals("federated repositories"))
                .findFirst();

        Assert.assertTrue(fedRepos.isPresent());
        Assert.assertEquals(fedRepos.get().get("attributes").get("number_of_repositories").asInt(), 2);

        Optional<JsonNode> fed1 = StreamSupport.stream(fedRepos.get().get("features").spliterator(), false)
                .filter(node -> node.get("name").asText().equals("fed1"))
                .findFirst();
        Assert.assertTrue(fed1.isPresent());
        Assert.assertEquals(fed1.get().get("attributes").get("enabled").asBoolean(), true);
        Assert.assertEquals(fed1.get().get("attributes").get("package_type").asText(), RepoType.Debian.name());
        Assert.assertEquals(fed1.get().get("attributes").get("number_of_mirrors").asInt(), 2);

        Optional<JsonNode> fed2 = StreamSupport.stream(fedRepos.get().get("features").spliterator(), false)
                .filter(node -> node.get("name").asText().equals("fed2"))
                .findFirst();
        Assert.assertTrue(fed2.isPresent());
        Assert.assertEquals(fed2.get().get("attributes").get("enabled").asBoolean(), false);
        Assert.assertEquals(fed2.get().get("attributes").get("package_type").asText(), RepoType.Docker.name());
        Assert.assertEquals(fed2.get().get("attributes").get("number_of_mirrors").asInt(), 2);

    }
}
