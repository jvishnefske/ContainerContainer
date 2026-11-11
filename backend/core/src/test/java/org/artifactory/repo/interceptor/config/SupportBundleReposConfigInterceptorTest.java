package org.artifactory.repo.interceptor.config;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.exception.http.BadRequestException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;

/**
 * @author Tamar Rosen
 */
public class SupportBundleReposConfigInterceptorTest {
    private SupportBundleReposConfigInterceptor interceptor;

    @BeforeMethod
    public void setup() {
        interceptor = new SupportBundleReposConfigInterceptor();
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testCreateLocalRepoWithSupportBundleKey() {
        LocalRepoConfig repoConfig = LocalRepoConfig.create(SUPPORT_BUNDLE_REPO_KEY, RepoType.Generic);
        interceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange(SUPPORT_BUNDLE_REPO_KEY, REPO_CREATE, repoConfig, null))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testCreateFederatedRepoWithSupportBundleKey() {
        FederatedRepoConfig repoConfig = FederatedRepoConfig.create(SUPPORT_BUNDLE_REPO_KEY, RepoType.Generic);
        interceptor.onBeforeSave(Map.of(RepoDetailsType.FEDERATED,
                List.of(new RepoConfigChange(SUPPORT_BUNDLE_REPO_KEY, REPO_CREATE, repoConfig, null))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testCreateRemoteRepoWithSupportBundleKey() {
        RemoteRepoConfig repoConfig = RemoteRepoConfig.create(SUPPORT_BUNDLE_REPO_KEY, RepoType.Generic);
        interceptor.onBeforeSave(Map.of(RepoDetailsType.REMOTE,
                List.of(new RepoConfigChange(SUPPORT_BUNDLE_REPO_KEY, REPO_CREATE, repoConfig, null))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testCreateVirtualRepoWithSupportBundleKey() {
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create(SUPPORT_BUNDLE_REPO_KEY, RepoType.Generic);
        interceptor.onBeforeSave(Map.of(RepoDetailsType.VIRTUAL,
                List.of(new RepoConfigChange(SUPPORT_BUNDLE_REPO_KEY, REPO_CREATE, repoConfig, null))));
    }

    @Test
    public void testUpdateForbiddenRepoWithSupportBundleKey() {
        interceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange(SUPPORT_BUNDLE_REPO_KEY, REPO_UPDATE,
                        LocalRepoConfig.create("repoKey", RepoType.Generic), null))));
    }

    @Test
    public void testCreateLocalRepoWithSupportBundleKeyAndSupportType() {
        interceptor.onBeforeSave(Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange(SUPPORT_BUNDLE_REPO_KEY, REPO_CREATE,
                        LocalRepoConfig.create("repoKey", RepoType.Generic), null))));
    }
}