package org.artifactory.ui.rest.model.admin.services.backups;

import jakarta.annotation.Nonnull;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Alexei Vainshtein
 */
public class BackupTest {

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testPopulateIncludeRepo() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        List<FederatedRepoConfig> federatedRepoConfigs = prepareRepositoryService(context);
        Backup backup = new Backup();
        List<String> excludeRepos = List.of("fed-docker", "fed-docker2");
        backup.setExcludeRepos(excludeRepos);
        backup.populateIncludeRepo();
        List<String> includeRepos = backup.getIncludeRepos();
        assertEquals(includeRepos.size(), 1);
        assertEquals(excludeRepos.size(), 2);
        assertEquals(federatedRepoConfigs.size() + includeRepos.size(), 3);
    }

    @Test
    public void testSetExcludeRepos() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        prepareRepositoryService(context);
        Backup backup = new Backup();
        List<String> expectedExcludedRepos = List.of("fed-docker");
        backup.setExcludeRepos(expectedExcludedRepos);
        List<String> excludedRepos = backup.getExcludeRepos();
        assertEquals(excludedRepos.size(), expectedExcludedRepos.size());
    }

    @Test(dataProvider = "actOnMissionControlProvider")
    public void testPopulatedFields(boolean shouldActOnMissionControl) {
        Backup backup = Mockito.spy(new Backup());
        doNothing().when(backup).populateExcludeRepo(any());
        doNothing().when(backup).populateIncludeRepo();
        BackupDescriptor backupDescriptor = new BackupDescriptor();
        backupDescriptor.setExportMissionControl(shouldActOnMissionControl);
        backup.populateditFields(backupDescriptor, true);
        assertEquals(backup.isExportMissionControl(), shouldActOnMissionControl);
    }

    @DataProvider
    public Object[][] actOnMissionControlProvider() {
        return new Object[][] {
                {true},
                {false}
        };
    }

    @Nonnull
    private List<FederatedRepoConfig> prepareRepositoryService(ArtifactoryContext context) {
        RepositoryService repositoryService = mock(RepositoryService.class);
        List<FederatedRepoConfig> federatedRepoConfigs = populateFederatedRepoConfigs();
        when(repositoryService.getFederatedRepoConfigs()).thenReturn(federatedRepoConfigs);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(mock(RepositoryConfigService.class));
        return federatedRepoConfigs;
    }

    @Nonnull
    private List<FederatedRepoConfig> populateFederatedRepoConfigs() {
        FederatedRepoConfig federatedRepoConfig1 = FederatedRepoConfig.create("fed-docker", RepoType.Generic);
        FederatedRepoConfig federatedRepoConfig2 = FederatedRepoConfig.create("fed-docker1", RepoType.Generic);
        return List.of(federatedRepoConfig1, federatedRepoConfig2);
    }
}