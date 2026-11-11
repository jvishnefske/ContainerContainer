package org.artifactory.repo.migration;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.converter.RepositoriesConfigSubConverter;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapper;
import org.artifactory.version.ArtifactoryConfigVersion;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigDescriptorRepoUtilsTest {

    @BeforeMethod
    public void setUp() {
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        SysConfig sysConfig = mock(SysConfig.class);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test
    public void testGetRepoDescriptorTotalCount() {
        MutableCentralConfigDescriptor centralConfigDescriptor = new CentralConfigDescriptorImpl();
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        FederatedRepoDescriptor federatedRepoDescriptor = new FederatedRepoDescriptor();
        ReleaseBundlesRepoDescriptor releaseBundlesRepoDescriptor = new ReleaseBundlesRepoDescriptor();

        centralConfigDescriptor.setRemoteRepositoriesMap(Map.of("remote-repo", new HttpRepoDescriptor()));
        centralConfigDescriptor.setLocalRepositoriesMap(generateDummyRepos(localRepoDescriptor, 10));
        centralConfigDescriptor.setVirtualRepositoriesMap(generateDummyRepos(virtualRepoDescriptor, 15));
        centralConfigDescriptor.setFederatedRepositoriesMap(
                generateDummyRepos(federatedRepoDescriptor, 55));
        centralConfigDescriptor.setReleaseBundlesRepositoriesMap(
                generateDummyRepos(releaseBundlesRepoDescriptor, 99));

        assertEquals(180, ConfigDescriptorRepoUtils.getRepoDescriptorTotalCount(centralConfigDescriptor));
    }

    @Test(dataProvider = "sizeDataProvider")
    public void testGetSize(Map<String, ?> map, int expectedSize) {
        assertEquals(expectedSize, ConfigDescriptorRepoUtils.getSize(map));
    }

    @Test
    public void cleanupConfigDescriptorRepositories() {
        MutableCentralConfigDescriptor centralConfigDescriptor = new CentralConfigDescriptorImpl();
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        FederatedRepoDescriptor federatedRepoDescriptor = new FederatedRepoDescriptor();
        ReleaseBundlesRepoDescriptor releaseBundlesRepoDescriptor = new ReleaseBundlesRepoDescriptor();

        centralConfigDescriptor.setRemoteRepositoriesMap(Map.of("remote-repo", new HttpRepoDescriptor()));
        centralConfigDescriptor.setLocalRepositoriesMap(generateDummyRepos(localRepoDescriptor, 10));
        centralConfigDescriptor.setVirtualRepositoriesMap(generateDummyRepos(virtualRepoDescriptor, 15));
        centralConfigDescriptor.setFederatedRepositoriesMap(
                generateDummyRepos(federatedRepoDescriptor, 55));
        centralConfigDescriptor.setReleaseBundlesRepositoriesMap(
                generateDummyRepos(releaseBundlesRepoDescriptor, 99));

        assertEquals(180, ConfigDescriptorRepoUtils.getRepoDescriptorTotalCount(centralConfigDescriptor));

        ConfigDescriptorRepoUtils.cleanupConfigDescriptorRepositories(centralConfigDescriptor);

        assertEquals(0, ConfigDescriptorRepoUtils.getRepoDescriptorTotalCount(centralConfigDescriptor));
    }

    @Test(dataProvider = "versionsDataProvider")
    public void testVersionForUpgrade(ArtifactoryConfigVersion currentVersion, boolean expected) {
        assertEquals(ConfigDescriptorRepoUtils.isRepoUpgradeShouldBeUpdated(currentVersion), expected);
    }

    @Test
    public void mapRepoDescriptorsToRepoConfigsSkipsNullMappedRepos() {
        LocalRepoDescriptor keepDescriptor = new LocalRepoDescriptor();
        keepDescriptor.setKey("keep-repo");
        keepDescriptor.setType(RepoType.Generic);
        LocalRepoDescriptor skipDescriptor = new LocalRepoDescriptor();
        skipDescriptor.setKey("skip-repo");
        skipDescriptor.setType(RepoType.Generic);

        MutableCentralConfigDescriptor centralConfig = new CentralConfigDescriptorImpl();
        Map<String, LocalRepoDescriptor> localRepos = new HashMap<>();
        localRepos.put("keep-repo", keepDescriptor);
        localRepos.put("skip-repo", skipDescriptor);
        centralConfig.setLocalRepositoriesMap(localRepos);

        RepositoryConfigMapper repositoryConfigMapper = mock(RepositoryConfigMapper.class);
        when(repositoryConfigMapper.dbInfoBlobToRepositoryConfig(any())).thenAnswer(invocation -> {
            DbRepositoryConfig dbRepo = invocation.getArgument(0);
            // null models a repo skipped by the unknown-package-type policy; it must be filtered out
            return "skip-repo".equals(dbRepo.getKey())
                    ? null
                    : LocalRepoConfig.create(dbRepo.getKey(), RepoType.Generic);
        });

        List<RepoConfig> result = ConfigDescriptorRepoUtils.mapRepoDescriptorsToRepoConfigs(
                centralConfig, Set.<RepositoriesConfigSubConverter>of(), repositoryConfigMapper);

        assertEquals(1, result.size(), "the repo mapped to null must be filtered out");
        assertEquals("keep-repo", result.get(0).getKey());
    }

    @DataProvider
    private static Object[][] sizeDataProvider() throws ClassNotFoundException {
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        return new Object[][] {
                {null, 0},
                {Map.of(), 0},
                {generateDummyRepos(localRepoDescriptor, 10), 10},
        };
    }

    @DataProvider
    private static Object[][] versionsDataProvider() {
        return new Object[][] {
                {null, true},
                {ArtifactoryConfigVersion.v321, false},
                {ArtifactoryConfigVersion.v3137, true},
                {ArtifactoryConfigVersion.v221, true},
                {ArtifactoryConfigVersion.v100, true},
        };
    }

    private static <E extends RepoDescriptor> Map<String, E> generateDummyRepos(E repoDescriptor, int count) {
        Map<String, E> repos = new HashMap<>();
        for (int i = 0; i < count; i++) {
            repos.put(repoDescriptor.getClass().getSimpleName() + "_" + i, repoDescriptor);
        }
        return repos;
    }
}