package org.artifactory.repo.interceptor.config;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.util.UnsupportedByLicenseException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.fail;

/**
 * @author Tamar Rosen
 */
public class ReleaseBundleReposConfigInterceptorTest {
    private ReleaseBundleReposConfigInterceptor interceptor;
    private AddonsManager addonsManager;
    private ArtifactoryServer artifactoryServer;

    @BeforeMethod
    public void setup() {
        addonsManager = mock(AddonsManager.class);
        when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.PRO);
        interceptor = new ReleaseBundleReposConfigInterceptor(addonsManager);
        ArtifactoryServersCommonService artifactoryServers = mock(ArtifactoryServersCommonService.class);
        artifactoryServer = mock(ArtifactoryServer.class);
        when(artifactoryServers.getCurrentMember()).thenReturn(artifactoryServer);
        interceptor.setArtifactoryServers(artifactoryServers);
    }

    @Test(expectedExceptions = UnsupportedByLicenseException.class)
    public void testCreateReleaseBundleReposWithoutEntitlement() {
        // given - no entitlement
        when(addonsManager.isEntitled(EntitlementType.RELEASE_BUNDLES_WRITE_MIX)).thenReturn(false);
        try {
            // given - artifactory is in starting state
            when(artifactoryServer.getServerState()).thenReturn(ArtifactoryServerState.STARTING);
            // when
            interceptor.onBeforeSave(Map.of(RepoDetailsType.RELEASE_BUNDLE,
                    List.of(new RepoConfigChange("repo", REPO_CREATE,
                            ReleaseBundleRepoConfig.create("repoKey"), null))));
            // given - artifactory is in converting state
            when(artifactoryServer.getServerState()).thenReturn(ArtifactoryServerState.CONVERTING);
            // when
            interceptor.onBeforeSave(Map.of(RepoDetailsType.RELEASE_BUNDLE,
                    List.of(new RepoConfigChange("repo", REPO_CREATE,
                            ReleaseBundleRepoConfig.create("repoKey"), null))));
        } catch (Exception e) {
            // then
            fail("Should not have thrown any exception");
        }

        // given - artifactory is in starting state
        when(artifactoryServer.getServerState()).thenReturn(ArtifactoryServerState.RUNNING);
        // when
        interceptor.onBeforeSave(Map.of(RepoDetailsType.RELEASE_BUNDLE,
                List.of(new RepoConfigChange("repo", REPO_CREATE,
                        ReleaseBundleRepoConfig.create("repoKey"), null))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testCreateLocalRepoWithReleaseBundleKey() {
        interceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange(RELEASE_BUNDLE_REPO_KEY, REPO_CREATE,
                        LocalRepoConfig.create("repoKey", RepoType.Generic), null))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testCreateFederatedRepoWithReleaseBundleKey() {
        interceptor.onBeforeSave(Map.of(RepoDetailsType.FEDERATED,
                List.of(new RepoConfigChange(RELEASE_BUNDLE_REPO_KEY, REPO_CREATE,
                        FederatedRepoConfig.create("repoKey", RepoType.Generic), null))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testCreateRemoteRepoWithReleaseBundleKey() {
        interceptor.onBeforeSave(Map.of(RepoDetailsType.REMOTE,
                List.of(new RepoConfigChange(RELEASE_BUNDLE_REPO_KEY, REPO_CREATE,
                        RemoteRepoConfig.create("repoKey", RepoType.Generic), null))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testCreateVirtualRepoWithReleaseBundleKey() {
        interceptor.onBeforeSave(Map.of(RepoDetailsType.VIRTUAL,
                List.of(new RepoConfigChange(RELEASE_BUNDLE_REPO_KEY, REPO_CREATE,
                        VirtualRepoConfig.create("repoKey", RepoType.Generic), null))));
    }

    @Test
    public void testUpdateForbiddenRepoWithReleaseBundleKey() {
        interceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange(RELEASE_BUNDLE_REPO_KEY, REPO_UPDATE,
                        LocalRepoConfig.create("repoKey", RepoType.Generic), null))));
    }
}