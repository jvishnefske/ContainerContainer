package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.FederatedRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.rest.common.model.restore.RestoreLayoutRepo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestoreHFRepositoryLayoutServiceTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private HaAddon haAddon;

    @Mock
    private RepositoryConfigService configService;

    @Mock
    private SecurityService securityService;

    @Mock
    private ConflictsGuard<Object> conflictsGuard;

    @Mock
    private ArtifactoryRestRequest<RestoreLayoutRepo> request;

    @Mock
    private RestResponse response;

    @Mock
    private RestoreLayoutRepo restoreLayoutRepo;

    @Mock
    private RepoConfig repoConfig;

    @Mock
    private FederatedRepoConfig federatedRepoConfig;
    @Mock
    private FederatedRepositoryRepoTypeConfig repoTypeConfig;

    @Mock
    private FederatedMember activeMember1;

    @Mock
    private FederatedMember activeMember2;

    @Mock
    private FederatedMember inactiveMember;

    @InjectMocks
    private RestoreHFRepositoryLayoutService service;

    @BeforeEach
    void setUp() {
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.getConflictsGuard("hf_restore_layout_guard")).thenReturn(conflictsGuard);
    }

    @Test
    void shouldFailWhenRepositoryConfigNotFound() {
        when(request.getImodel()).thenReturn(restoreLayoutRepo);
        when(restoreLayoutRepo.repoKey()).thenReturn("test-repo");
        when(repositoryService.getRepoConfigByKey("test-repo")).thenReturn(null);

        service.execute(request, response);

        verify(response).error("Repository test-repo descriptor not found");
    }

    @Test
    void shouldFailWhenVirtualRepositoryNotSupported() {
        when(request.getImodel()).thenReturn(restoreLayoutRepo);
        String repoKey = "test-repo";
        when(restoreLayoutRepo.repoKey()).thenReturn(repoKey);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.VIRTUAL);
        when(repoConfig.getKey()).thenReturn(repoKey);

        service.execute(request, response);

        verify(response).error("Repository test-repo is virtual. Not supported.");
    }

    @Test
    void shouldFailWhenFederatedRepositoryDescriptorNotFound() {
        when(request.getImodel()).thenReturn(restoreLayoutRepo);
        String repoKey = "test-repo";
        when(restoreLayoutRepo.repoKey()).thenReturn(repoKey);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        when(repoConfig.getKey()).thenReturn(repoKey);

        service.execute(request, response);

        verify(response).error("Failed to get federated descriptor for repository test-repo");
    }

    @Test
    void shouldFailMigrationWhenFederatedRepositoryWithActiveMembers() {
        when(request.getImodel()).thenReturn(restoreLayoutRepo);
        String repoKey = "federated-repo-active";
        when(restoreLayoutRepo.repoKey()).thenReturn(repoKey);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(federatedRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repositoryService.getFederatedRepoConfigByKey(repoKey)).thenReturn(federatedRepoConfig);

        when(activeMember1.isEnabled()).thenReturn(true);
        String url1 = "http://member1.example.com";
        String url2 = "http://member2.example.com";
        when(activeMember1.getUrlOrTemplate()).thenReturn(url1);
        when(activeMember2.isEnabled()).thenReturn(true);
        when(activeMember2.getUrlOrTemplate()).thenReturn(url2);
        when(federatedRepoConfig.getRepoTypeConfig().getFederatedMembers())
                .thenReturn(List.of(activeMember1, activeMember2));

        service.execute(request, response);

        verify(response).error("Federated repository federated-repo-active cannot be used in restore while it has " +
                "active member(s): http://member1.example.com, http://member2.example.com");
    }

    @Test
    void shouldSucceedWhenFederatedRepositoryWithInactiveMembers() {
        when(request.getImodel()).thenReturn(restoreLayoutRepo);
        String repoKey = "federated-repo-inactive";
        when(restoreLayoutRepo.repoKey()).thenReturn(repoKey);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(federatedRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repositoryService.getFederatedRepoConfigByKey(repoKey)).thenReturn(federatedRepoConfig);

        when(inactiveMember.isEnabled()).thenReturn(false);
        when(federatedRepoConfig.getRepoTypeConfig().getFederatedMembers()).thenReturn(List.of(inactiveMember));

        service.execute(request, response);

        verify(response).info("Layout restore for HuggingFaceML repository federated-repo-inactive completed");
    }

    @Test
    void localRepositoryProcessed() throws Exception {
        when(request.getImodel()).thenReturn(restoreLayoutRepo);
        when(restoreLayoutRepo.repoKey()).thenReturn("test-repo");
        when(repositoryService.getRepoConfigByKey("test-repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(conflictsGuard.tryToLock(anyString(), anyLong(), any())).thenReturn(true);
        doNothing().when(conflictsGuard).unlock(anyString());

        service.execute(request, response);

        verify(response).info(contains("Layout restore for HuggingFaceML repository test-repo completed"));
    }

    @Test
    void remoteRepositoryProcessed() throws Exception {
        when(request.getImodel()).thenReturn(restoreLayoutRepo);
        when(restoreLayoutRepo.repoKey()).thenReturn("test-remote-repo");
        when(repositoryService.getRepoConfigByKey("test-remote-repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(conflictsGuard.tryToLock(anyString(), anyLong(), any())).thenReturn(true);
        doNothing().when(conflictsGuard).unlock(anyString());

        service.execute(request, response);

        verify(response).info(contains("Layout restore for HuggingFaceML repository test-remote-repo completed"));
    }
}