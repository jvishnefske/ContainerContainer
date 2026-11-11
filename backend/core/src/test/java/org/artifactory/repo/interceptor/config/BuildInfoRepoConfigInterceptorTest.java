package org.artifactory.repo.interceptor.config;

import com.google.common.collect.Lists;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_REPO_LAYOUT;
import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;

/**
 * @author Tamar Rosen
 */
public class BuildInfoRepoConfigInterceptorTest {
    private BuildInfoRepoConfigInterceptor interceptor;
    private RepositoryConfigService repositoryConfigService;
    private CentralConfigService centralConfigService;

    @BeforeMethod
    public void setup() {
        repositoryConfigService = mock(RepositoryConfigService.class);
        centralConfigService = mock(CentralConfigService.class);
        interceptor = new BuildInfoRepoConfigInterceptor();
        interceptor.setRepositoryConfigService(repositoryConfigService);
        interceptor.setCentralConfigService(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testNoMoreThanOneBuildInfoRepo() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(BUILDINFO_REPO_KEY, RepoType.BuildInfo);
        interceptor.onBeforeSave(
                Map.of(LOCAL, List.of(new RepoConfigChange(BUILDINFO_REPO_KEY, REPO_CREATE, localRepoConfig, null),
                        new RepoConfigChange(BUILDINFO_REPO_KEY, REPO_CREATE, localRepoConfig, null))));
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testNoBuildInfoInRemote() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create(BUILDINFO_REPO_KEY, RepoType.BuildInfo);
        interceptor.onBeforeSave(
                Map.of(REMOTE, List.of(new RepoConfigChange(BUILDINFO_REPO_KEY, REPO_CREATE, remoteRepoConfig, null)))
        );
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testNoBuildInfoInVirtual() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create(BUILDINFO_REPO_KEY, RepoType.BuildInfo);
        interceptor.onBeforeSave(
                Map.of(VIRTUAL,
                        List.of(new RepoConfigChange(BUILDINFO_REPO_KEY, REPO_CREATE, virtualRepoConfig, null))));
    }

    @Test
    public void testCreateDefaultBuildInfoRepoIfNoneExistAndNoneBeingCreated() {
        when(repositoryConfigService.getRepoConfigsByPackageType(RepoType.BuildInfo)).thenReturn(List.of());
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges =
                new HashMap<>(Map.of(LOCAL, Lists.newArrayList(
                        new RepoConfigChange("repoKey", REPO_DELETE, null, null))));
        interceptor.onBeforeSave(repoConfigChanges);
        List<RepoConfigChange> localRepoConfigChanges = repoConfigChanges.get(LOCAL);
        assertEquals(2, localRepoConfigChanges.size());
        assertEquals(REPO_DELETE, localRepoConfigChanges.get(0).getConfigAction());
        assertEquals(REPO_CREATE, localRepoConfigChanges.get(1).getConfigAction());
        assertEquals("repoKey", localRepoConfigChanges.get(0).getRepoKey());
        assertEquals(BUILDINFO_REPO_KEY, localRepoConfigChanges.get(1).getRepoKey());
    }

    @Test
    public void testNoSpecialOperationIfNoBuildInfoExistButOneIsBeingCreated() {
        when(repositoryConfigService.getRepoConfigsByPackageType(RepoType.BuildInfo)).thenReturn(List.of());
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges =
                new HashMap<>(Map.of(LOCAL, Lists.newArrayList(
                        new RepoConfigChange("repoKey", REPO_CREATE,
                                LocalRepoConfig.create("repoKey", RepoType.BuildInfo), null))));
        interceptor.onBeforeSave(repoConfigChanges);
        List<RepoConfigChange> localRepoConfigChanges = repoConfigChanges.get(LOCAL);
        assertEquals(1, localRepoConfigChanges.size());
        assertEquals(REPO_CREATE, localRepoConfigChanges.get(0).getConfigAction());
        assertEquals("repoKey", localRepoConfigChanges.get(0).getRepoKey());
    }

    @Test(expectedExceptions = ConfigurationException.class)
    public void testFailToCreateBuildInfoRepoIfFallbackNamesAreTaken() {
        // given - no build info repo exists in storage
        when(repositoryConfigService.getRepoConfigsByPackageType(RepoType.BuildInfo)).thenReturn(List.of());
        // given - all fallback build info repo names are already taken
        when(repositoryConfigService.existsByRepoKey(BUILDINFO_REPO_KEY)).thenReturn(true);
        when(repositoryConfigService.existsByRepoKey(BUILDINFO_REPO_KEY + "-1")).thenReturn(true);
        when(repositoryConfigService.existsByRepoKey(BUILDINFO_REPO_KEY + "-2")).thenReturn(true);
        when(repositoryConfigService.existsByRepoKey(BUILDINFO_REPO_KEY + "-3")).thenReturn(true);
        when(repositoryConfigService.existsByRepoKey(BUILDINFO_REPO_KEY + "-4")).thenReturn(true);
        when(repositoryConfigService.existsByRepoKey(BUILDINFO_REPO_KEY + "-5")).thenReturn(true);
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges = new HashMap<>();
        interceptor.onBeforeSave(repoConfigChanges);
    }

    @Test
    public void testCreateBuildInfoRepoIfDoesNotExist() {
        // given - no build info repo exists in storage
        when(repositoryConfigService.getRepoConfigsByPackageType(RepoType.BuildInfo)).thenReturn(List.of());
        // given - build info repo name is already taken
        when(repositoryConfigService.existsByRepoKey(BUILDINFO_REPO_KEY)).thenReturn(true);
        when(repositoryConfigService.existsByRepoKey(BUILDINFO_REPO_KEY + "-1")).thenReturn(false);
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges = new HashMap<>();
        interceptor.onBeforeSave(repoConfigChanges);
        assertEquals(1, repoConfigChanges.getOrDefault(LOCAL, List.of()).size());
        RepoConfigChange buildInfoCreation = repoConfigChanges.get(LOCAL).get(0);
        assertEquals(buildInfoCreation.getConfigAction(), REPO_CREATE);
        assertNotNull(buildInfoCreation.getNewRepoConfig());
        assertEquals(BUILDINFO_REPO_KEY + "-" + 1, buildInfoCreation.getNewRepoConfig().getKey());
        assertEquals(RepoType.BuildInfo, buildInfoCreation.getNewRepoConfig().getPackageType());
        assertNull(buildInfoCreation.getNewRepoConfig().getBaseConfig().getRepoLayoutRef());
    }

    @Test
    public void testCreateBuildInfoRepoIfDoesNotExistWithRepoLayoutRef() {
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigDescriptor.getRepoLayout(DEFAULT_REPO_LAYOUT)).thenReturn(mock(RepoLayout.class));
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);

        // given - no build info repo exists in storage
        when(repositoryConfigService.getRepoConfigsByPackageType(RepoType.BuildInfo)).thenReturn(List.of());
        // given - build info repo name is already taken
        when(repositoryConfigService.existsByRepoKey(BUILDINFO_REPO_KEY)).thenReturn(true);
        when(repositoryConfigService.existsByRepoKey(BUILDINFO_REPO_KEY + "-1")).thenReturn(false);
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges = new HashMap<>();
        interceptor.onBeforeSave(repoConfigChanges);
        assertEquals(1, repoConfigChanges.getOrDefault(LOCAL, List.of()).size());
        RepoConfigChange buildInfoCreation = repoConfigChanges.get(LOCAL).get(0);
        assertEquals(buildInfoCreation.getConfigAction(), REPO_CREATE);
        RepoConfig newRepoConfig = buildInfoCreation.getNewRepoConfig();
        assertNotNull(newRepoConfig);
        assertEquals(newRepoConfig.getKey(), BUILDINFO_REPO_KEY + "-" + 1);
        assertEquals(newRepoConfig.getPackageType(), RepoType.BuildInfo);
        assertEquals(newRepoConfig.getBaseConfig().getRepoLayoutRef(), DEFAULT_REPO_LAYOUT);
    }
}