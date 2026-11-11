package org.artifactory.support.core.collectors.repo;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.jfrog.support.common.config.ConfigFilesConfiguration;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author Tamar Rosen
 */
public class RepositoriesConfigCollectorTest {

    @DataProvider
    public static Object[][] scrubProperties() {
        return new Object[][] {
                {true, false, false},
                {false, true, false},
                {false, false, true}
        };
    }

    @Test(dataProvider = "scrubProperties")
    public void testDoCollect(boolean hideUserDetails, boolean scrubUserNames, boolean scrubIPs) {
        // given - repositories with sensitive data
        RepositoryConfigService repositoryConfigService = Mockito.mock(RepositoryConfigService.class);
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote", RepoType.Generic);
        remoteRepoConfig.getRepoTypeConfig().setPassword("password");
        remoteRepoConfig.getRepoTypeConfig().setUsername("username");
        remoteRepoConfig.getRepoTypeConfig().setUrl("https://test.com");
        when(repositoryConfigService.getAllRepoConfigs()).thenReturn(List.of(remoteRepoConfig));

        // when - collect
        RepositoriesConfigCollector repositoriesConfigCollector =
                new RepositoriesConfigCollector(repositoryConfigService);
        File baseDir = Mockito.mock(File.class);
        repositoriesConfigCollector.doCollect(
                new ConfigFilesConfiguration(true, Optional.of(hideUserDetails), scrubIPs, scrubUserNames), baseDir);

        // then - relevant sensitive data was scrubbed
        ArgumentCaptor<List> remoteReposCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryConfigService).writeRepoConfigsToFile(eq(baseDir), remoteReposCaptor.capture());
        List remoteRepoConfigs = remoteReposCaptor.getValue();
        assertEquals(1, remoteRepoConfigs.size());
        RemoteRepoConfig resultRepoConfig = (RemoteRepoConfig) remoteRepoConfigs.get(0);

        assertEquals(hideUserDetails ? null : "password", resultRepoConfig.getRepoTypeConfig().getPassword());
        assertEquals(scrubUserNames ? null : "username", resultRepoConfig.getRepoTypeConfig().getUsername());
        assertEquals(scrubIPs ? null : "https://test.com", resultRepoConfig.getRepoTypeConfig().getUrl());
    }

    @Test
    void testDoCollectNoRepos() {
        // given - repositories with sensitive data
        RepositoryConfigService repositoryConfigService = Mockito.mock(RepositoryConfigService.class);
        when(repositoryConfigService.getAllRepoConfigs()).thenReturn(List.of());

        // when - collect
        RepositoriesConfigCollector repositoriesConfigCollector =
                new RepositoriesConfigCollector(repositoryConfigService);
        File baseDir = Mockito.mock(File.class);
        repositoriesConfigCollector.doCollect(
                new ConfigFilesConfiguration(true, Optional.of(true), true, true), baseDir);

        // then - relevant sensitive data was scrubbed
        verify(repositoryConfigService, times(0)).writeRepoConfigsToFile(eq(baseDir), any());
    }
}