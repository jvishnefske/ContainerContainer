package org.artifactory.ui.rest.model.admin.services.indexer;

import jakarta.annotation.Nonnull;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.index.IndexerDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RepoConfig;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
public class IndexerTest {

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testPopulateRepos() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        List<RepoConfig> federatedRepoConfigs = prepareRepositoryService(context);
        Indexer indexer = new Indexer();
        indexer.populateRepos(null);
        Assert.assertEquals(indexer.getExcludedRepos().size(), federatedRepoConfigs.size());
    }

    @Test
    public void testToDescriptor() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        List<RepoConfig> federatedRepoConfigs = prepareRepositoryService(context);

        Indexer indexer = new Indexer();
        indexer.setIncludedRepos(List.of("fed-maven", "fed-gradle"));
        IndexerDescriptor indexerDescriptor = indexer.toDescriptor();
        assert indexerDescriptor.getIncludedRepositories() != null;
        Assert.assertEquals(indexerDescriptor.getIncludedRepositories().size(), federatedRepoConfigs.size());

    }

    private List<RepoConfig> prepareRepositoryService(ArtifactoryContext context) {
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        List<RepoConfig> federatedRepoConfigs = populateFederatedRepoConfigs();
        when(repositoryConfigService.getRepoConfigsByTypes(any())).thenReturn(federatedRepoConfigs);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        return federatedRepoConfigs;
    }

    @Nonnull
    private List<RepoConfig> populateFederatedRepoConfigs() {
        FederatedRepoConfig federatedRepoConfig1 = FederatedRepoConfig.create("fed-maven", RepoType.Maven);
        FederatedRepoConfig federatedRepoConfig2 = FederatedRepoConfig.create("fed-gradle", RepoType.Gradle);
        return List.of(federatedRepoConfig1, federatedRepoConfig2);
    }
}