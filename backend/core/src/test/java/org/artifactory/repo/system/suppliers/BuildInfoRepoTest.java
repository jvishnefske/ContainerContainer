package org.artifactory.repo.system.suppliers;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.util.RepoLayoutUtils;
import org.testng.annotations.Test;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_REPO_LAYOUT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.AssertJUnit.assertNull;

public class BuildInfoRepoTest {

    @Test
    public void testBuildInfoBuildRepoConfig() {
        CentralConfigDescriptorImpl descriptor = new CentralConfigDescriptorImpl();
        descriptor.addRepoLayout(RepoLayoutUtils.SIMPLE_DEFAULT);
        LocalRepoConfig localRepoConfig = new BuildInfoRepo().buildRepoConfig(descriptor);
        assertEquals(localRepoConfig.getPackageType(), RepoType.BuildInfo);
        assertEquals(localRepoConfig.getBaseConfig().getRepoLayoutRef(), DEFAULT_REPO_LAYOUT);
    }

    @Test
    public void testBuildInfoBuildRepoConfigWithNullRepoLayout() {
        LocalRepoConfig localRepoConfig = new BuildInfoRepo().buildRepoConfig(new CentralConfigDescriptorImpl());
        assertEquals(localRepoConfig.getPackageType(), RepoType.BuildInfo);
        assertNull(localRepoConfig.getBaseConfig().getRepoLayoutRef());
    }


    @Test
    public void testBuildInfoBuildDescriptorWithRepoNameWithoutLayout() {
        BuildInfoRepo buildInfoRepo = new BuildInfoRepo();
        LocalRepoConfig localRepoConfig = buildInfoRepo.buildRepoConfig(mock(CentralConfigDescriptor.class), "repo");
        assertEquals(localRepoConfig.getPackageType(), RepoType.BuildInfo);
        assertEquals(localRepoConfig.getKey(), "repo");
        assertNull(localRepoConfig.getBaseConfig().getRepoLayoutRef());
    }

    @Test
    public void testBuildInfoBuildDescriptorWithRepoName() {
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(descriptor.getRepoLayout(DEFAULT_REPO_LAYOUT)).thenReturn(mock(RepoLayout.class));
        BuildInfoRepo buildInfoRepo = new BuildInfoRepo();
        LocalRepoConfig localRepoConfig = buildInfoRepo.buildRepoConfig(descriptor, "repo");
        assertEquals(localRepoConfig.getPackageType(), RepoType.BuildInfo);
        assertEquals(localRepoConfig.getKey(), "repo");
        assertEquals(localRepoConfig.getBaseConfig().getRepoLayoutRef(), DEFAULT_REPO_LAYOUT);
    }
}