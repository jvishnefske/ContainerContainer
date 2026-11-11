package org.artifactory.ui.rest.service.admin.configuration.layouts;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.ui.rest.model.admin.configuration.layouts.RepositoryLayoutModel;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Alexei Vainshtein
 */
public class GetLayoutInfoServiceTest {

    private static final String testLayoutName = "default";
    private static final List<String> federatedReposWithTestedLayout = List.of("test4", "test5");
    private static final List<String> localReposWithTestedLayout = List.of("test", "test2");
    private static final String fedRepoWithoutTestLayout = "test6";
    private static final String localRepoWithoutTestLayout = "test3";

    @Test
    public void testCreateAssociations() {
        RepoLayout defaultLayout = createRepoLayout(testLayoutName);
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getRepoLayouts()).thenReturn(List.of(defaultLayout));
        createLocalRepositories(defaultLayout, repositoryConfigService);
        createFederatedRepositories(defaultLayout, repositoryConfigService);

        GetLayoutInfoService service = new GetLayoutInfoService();
        service.setRepositoryConfigService(repositoryConfigService);
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAdmin()).thenReturn(true);
        RepositoryLayoutModel layoutModel = new RepositoryLayoutModel(defaultLayout);
        service.setSecurityService(securityService);
        service.createAssociations(layoutModel);
        assertEquals(layoutModel.getRepositoryAssociations().getLocalRepositories().size(), 4);
        verify(securityService, times(3)).isAdmin();
        verifyNoMoreInteractions(securityService);
    }

    @Test
    public void testCreateAssociationsWithLimitedUser() {
        RepoLayout defaultLayout = createRepoLayout(testLayoutName);
        RepositoryConfigService repositoryService = mock(RepositoryConfigService.class);
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getRepoLayouts()).thenReturn(List.of(defaultLayout));
        createLocalRepositories(defaultLayout, repositoryService);
        createFederatedRepositories(defaultLayout, repositoryService);

        GetLayoutInfoService service = new GetLayoutInfoService();
        service.setRepositoryConfigService(repositoryService);
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        when(securityService.isProjectAdmin(new RepoPathImpl(localReposWithTestedLayout.get(0), "")))
                .thenReturn(true);
        when(securityService.isProjectAdmin(new RepoPathImpl(federatedReposWithTestedLayout.get(0), "")))
                .thenReturn(true);

        RepositoryLayoutModel layoutModel = new RepositoryLayoutModel(defaultLayout);
        service.setSecurityService(securityService);
        service.createAssociations(layoutModel);
        assertEquals(layoutModel.getRepositoryAssociations().getLocalRepositories().size(), 2);
        verify(securityService, times(3)).isAdmin();
        verify(securityService, times(4)).isProjectAdmin(any(RepoPathImpl.class));
        verifyNoMoreInteractions(securityService);
    }

    private void createFederatedRepositories(RepoLayout defaultLayout,
            RepositoryConfigService repositoryconfigservice) {
        List<RepoConfig> repoConfigs = new ArrayList<>();
        repoConfigs.add(createFederatedRepo(federatedReposWithTestedLayout.get(0), RepoType.Generic, defaultLayout));
        repoConfigs.add(createFederatedRepo(federatedReposWithTestedLayout.get(1), RepoType.Npm, defaultLayout));
        repoConfigs.add(createFederatedRepo(fedRepoWithoutTestLayout, RepoType.Docker, createRepoLayout("non-def")));
        when(repositoryconfigservice.getRepoConfigsByType(RepoDetailsType.FEDERATED)).thenReturn(repoConfigs);
    }

    private void createLocalRepositories(RepoLayout defaultLayout, RepositoryConfigService repositoryConfigService) {
        List<LocalConfig> repoConfigs = new ArrayList<>();
        repoConfigs.add(createLocalRepo(localReposWithTestedLayout.get(0), RepoType.Generic, defaultLayout));
        repoConfigs.add(createLocalRepo(localReposWithTestedLayout.get(1), RepoType.Docker, defaultLayout));
        repoConfigs.add(createLocalRepo(localRepoWithoutTestLayout, RepoType.Npm, createRepoLayout("non-def")));
        when(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos()).thenReturn(repoConfigs);
    }

    private LocalConfig createLocalRepo(String key, RepoType type, RepoLayout repoLayout) {
        LocalConfig localRepoConfig = LocalRepoConfig.create(key, type);
        localRepoConfig.getBaseConfig().setRepoLayoutRef(repoLayout.getName());
        return localRepoConfig;
    }

    private LocalConfig createFederatedRepo(String key, RepoType type, RepoLayout repoLayout) {
        LocalConfig federatedRepoConfig = FederatedRepoConfig.create(key, type);
        federatedRepoConfig.getBaseConfig().setRepoLayoutRef(repoLayout.getName());
        return federatedRepoConfig;
    }

    private RepoLayout createRepoLayout(String name) {
        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName(name);
        return repoLayout;
    }
}