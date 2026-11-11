package org.artifactory.repo.system;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.repo.config.RepoConfigDefaultValues.EXAMPLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.BILLING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Mor Merhav
 */
public class FirstStartupHelperTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryApplicationContext context;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    private AutoCloseable closeable;

    @BeforeEach
    public void setupMethod() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(List.of());
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED)).thenReturn(List.of());
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL)).thenReturn(List.of());
    }

    @AfterEach
    public void afterMethod() throws Exception {
        unbindArtifactoryHome();
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    public void testOnlyDefaultReposExistFilterPipelineRepoReturnTrue() {
        LocalRepoConfig exampleRepo = LocalRepoConfig.create(EXAMPLE_REPO_KEY, RepoType.Generic);
        LocalRepoConfig buildInfoRepo = LocalRepoConfig.create(BUILDINFO_REPO_KEY, RepoType.Generic);
        LocalRepoConfig billingRepo = LocalRepoConfig.create(BILLING_REPO_KEY, RepoType.Generic);
        LocalRepoConfig pipelinesRepo =
                LocalRepoConfig.create(ConstantValues.defaultPipelinesRepoKey.getString(), RepoType.Generic);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                List.of(exampleRepo, buildInfoRepo, pipelinesRepo, billingRepo));

        when(repositoryService.isRepoContainsFiles(any())).thenReturn(false);

        boolean onlyDefaultReposExist = FirstStartupHelper.onlyDefaultReposExist();
        assertTrue(onlyDefaultReposExist);
    }

    @Test
    public void testNotOnlyDefaultReposExistReturnFalse() {
        LocalRepoConfig exampleRepo = LocalRepoConfig.create(EXAMPLE_REPO_KEY, RepoType.Generic);
        LocalRepoConfig buildInfoRepo = LocalRepoConfig.create(BUILDINFO_REPO_KEY, RepoType.Generic);
        LocalRepoConfig billingRepo = LocalRepoConfig.create(BILLING_REPO_KEY, RepoType.Generic);
        LocalRepoConfig nonDefaultRepo = LocalRepoConfig.create("nonDefaultRepo", RepoType.Generic);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                List.of(exampleRepo, buildInfoRepo, nonDefaultRepo, billingRepo));
        when(repositoryService.getArtifactCount(any())).thenReturn(0L);

        when(repositoryService.isRepoContainsFiles(any())).thenReturn(false);

        boolean onlyDefaultReposExist = FirstStartupHelper.onlyDefaultReposExist();
        assertFalse(onlyDefaultReposExist);
    }

    @Test
    public void testExternalSystemRepos() throws NoSuchFieldException, IllegalAccessException {
        String externalRepo = "external-jas-repo";
        setExternalRepoProp(externalRepo);
        LocalRepoConfig exampleRepo = LocalRepoConfig.create(EXAMPLE_REPO_KEY, RepoType.Generic);
        LocalRepoConfig buildInfoRepo = LocalRepoConfig.create(BUILDINFO_REPO_KEY, RepoType.Generic);
        LocalRepoConfig nonDefaultRepo = LocalRepoConfig.create(externalRepo, RepoType.Generic);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                List.of(exampleRepo, buildInfoRepo, nonDefaultRepo));
        when(repositoryService.getArtifactCount(any())).thenReturn(0L);

        when(repositoryService.isRepoContainsFiles(any())).thenReturn(false);

        boolean onlyDefaultReposExist = FirstStartupHelper.onlyDefaultReposExist();
        assertTrue(onlyDefaultReposExist);
    }

    @Test
    public void testDefaultReposNotEmptyReturnFalse() {
        LocalRepoConfig exampleRepo = LocalRepoConfig.create(EXAMPLE_REPO_KEY, RepoType.Generic);
        LocalRepoConfig buildInfoRepo = LocalRepoConfig.create(BUILDINFO_REPO_KEY, RepoType.Generic);
        LocalRepoConfig billingRepo = LocalRepoConfig.create(BILLING_REPO_KEY, RepoType.Generic);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                List.of(exampleRepo, buildInfoRepo, billingRepo));
        when(repositoryService.getArtifactCount(any())).thenReturn(1L);

        when(repositoryService.isRepoContainsFiles(any())).thenReturn(true);

        boolean onlyDefaultReposExist = FirstStartupHelper.onlyDefaultReposExist();
        assertFalse(onlyDefaultReposExist);
    }

    @Test
    public void testDefaultReposNotEmptyWithFederationReturnFalse() {
        LocalRepoConfig exampleRepo = LocalRepoConfig.create(EXAMPLE_REPO_KEY, RepoType.Generic);
        LocalRepoConfig buildInfoRepo = LocalRepoConfig.create(BUILDINFO_REPO_KEY, RepoType.Generic);
        LocalRepoConfig billingRepo = LocalRepoConfig.create(BILLING_REPO_KEY, RepoType.Generic);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(
                List.of(exampleRepo, buildInfoRepo, billingRepo));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED)).thenReturn(
                List.of(FederatedRepoConfig.create("repoKey", RepoType.Generic)));

        boolean onlyDefaultReposExist = FirstStartupHelper.onlyDefaultReposExist();
        assertFalse(onlyDefaultReposExist);
    }

    @Test
    public void testLocalReposThreshold() {
        List<RepoConfig> localRepoConfigs = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            localRepoConfigs.add(LocalRepoConfig.create("repo" + i, RepoType.Generic));
        }
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)).thenReturn(localRepoConfigs);

        boolean onlyDefaultReposExist = FirstStartupHelper.onlyDefaultReposExist();
        assertFalse(onlyDefaultReposExist);
        verify(repositoryConfigService).getRepoConfigsByType(RepoDetailsType.LOCAL);
        verifyNoMoreInteractions(repositoryConfigService);
    }

    private void setExternalRepoProp(String externalRepo) {
        when(repositoryService.isExternalSystemRepo(externalRepo)).thenReturn(true);

    }
}