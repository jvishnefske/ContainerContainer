package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import io.micrometer.core.instrument.MeterRegistry;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.huggingfaceml.HuggingFaceLayoutConverter;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.common.repo.PackageType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.FederatedRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.rest.common.model.migrate.MigrateRepo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.concurrency.ConflictsGuard;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Volodymyr Shulga
 */
@SuppressWarnings("rawtypes")
class MigrateHFRepositoryServiceTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private SecurityService securityService;
    @Mock
    private HuggingFaceLayoutConverter converter;
    @Mock
    private AqlService aqlService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HaAddon haAddon;
    @Mock
    private ConflictsGuard<Object> conflictsGuard;
    @Mock
    private ArtifactoryRestRequest<MigrateRepo> request;
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
    @Mock
    private MeterRegistry meterRegistry;
    private AutoCloseable mocks;

    private MigrateHFRepositoryService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.getConflictsGuard(any())).thenReturn(conflictsGuard);
        service = Mockito.spy(new MigrateHFRepositoryService(repositoryService, aqlService,
                addonsManager, securityService, meterRegistry));
        when(service.createHuggingFaceLayoutConverter()).thenReturn(converter);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void executeWithNonExistentRepo() {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        final String nonExistingRepoKey = "non-existent-repo";
        MigrateRepo migrateRepo = new MigrateRepo(nonExistingRepoKey, PackageType.GENERIC);
        when(request.getImodel()).thenReturn(migrateRepo);
        when(repositoryService.getRepoConfigByKey(nonExistingRepoKey)).thenReturn(null);

        service.execute(request, response);

        assertEquals("Repository non-existent-repo descriptor not found", response.getFeedbackMsg().getError());
    }

    @Test
    void executeWithVirtualRepo() {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        MigrateRepo migrateRepo = new MigrateRepo("virtual-repo", PackageType.GENERIC);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.VIRTUAL);
        when(repoConfig.getKey()).thenReturn("virtual-repo");
        when(request.getImodel()).thenReturn(migrateRepo);
        when(repositoryService.getRepoConfigByKey("virtual-repo")).thenReturn(repoConfig);

        service.execute(request, response);

        assertEquals("Repository virtual-repo is virtual. Not supported.", response.getFeedbackMsg().getError());
    }

    @Test
    void executeWithLocalRepo() {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        MigrateRepo migrateRepo = new MigrateRepo("local-repo", PackageType.GENERIC);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(request.getImodel()).thenReturn(migrateRepo);
        when(repositoryService.getRepoConfigByKey("local-repo")).thenReturn(repoConfig);

        service.execute(request, response);

        assertTrue(response.getFeedbackMsg().getInfo()
                .contains("Layout migration for HuggingFaceML repository local-repo completed"));
    }

    @Test
    void executeWithFederatedRepoWithActiveMembers() {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        String repoKey = "federated-repo-active";
        MigrateRepo migrateRepo = new MigrateRepo(repoKey, PackageType.GENERIC);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(request.getImodel()).thenReturn(migrateRepo);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repositoryService.getFederatedRepoConfigByKey(repoKey))
                .thenReturn(federatedRepoConfig);

        when(activeMember1.isEnabled()).thenReturn(true);
        String url1 = "http://member1.example.com";
        String url2 = "http://member2.example.com";
        when(activeMember1.getUrlOrTemplate()).thenReturn(url1);
        when(activeMember2.isEnabled()).thenReturn(true);
        when(activeMember2.getUrlOrTemplate()).thenReturn(url2);
        when(federatedRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(federatedRepoConfig.getRepoTypeConfig().getFederatedMembers())
                .thenReturn(List.of(activeMember1, activeMember2));

        service.execute(request, response);

        assertEquals("Federated repository federated-repo-active cannot be used in migration while it has " +
                        "active member(s): http://member1.example.com, http://member2.example.com",
                response.getFeedbackMsg().getError());
    }

    @Test
    void executeWithFederatedRepoWithoutDescriptor() {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        String repoKey = "federated-repo-no-descriptor";
        MigrateRepo migrateRepo = new MigrateRepo(repoKey, PackageType.GENERIC);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(request.getImodel()).thenReturn(migrateRepo);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repositoryService.federatedRepoDescriptorByKey(repoKey)).thenReturn(null);

        service.execute(request, response);

        assertEquals("Failed to get federated descriptor for repository federated-repo-no-descriptor",
                response.getFeedbackMsg().getError());
    }

    @Test
    void executeWithFederatedRepoWithInactiveMembers() {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        String repoKey = "federated-repo-inactive";
        MigrateRepo migrateRepo = new MigrateRepo(repoKey, PackageType.GENERIC);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(request.getImodel()).thenReturn(migrateRepo);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(federatedRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repositoryService.getFederatedRepoConfigByKey(repoKey))
                .thenReturn(federatedRepoConfig);

        when(inactiveMember.isEnabled()).thenReturn(false);
        when(inactiveMember.getUrlOrTemplate()).thenReturn("http://member2.example.com");
        when(federatedRepoConfig.getRepoTypeConfig().getFederatedMembers()).thenReturn(List.of(inactiveMember));

        service.execute(request, response);

        assertTrue(response.getFeedbackMsg().getInfo()
                .contains("Layout migration for HuggingFaceML repository federated-repo-inactive completed"));
    }

    @Test
    void executeWithException() {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        MigrateRepo migrateRepo = new MigrateRepo("repo-with-exception", PackageType.GENERIC);
        when(request.getImodel()).thenReturn(migrateRepo);
        when(repositoryService.getRepoConfigByKey("repo-with-exception"))
                .thenThrow(new RuntimeException("Test exception"));

        service.execute(request, response);

        assertEquals("Failed to run migration for HuggingFace repo repo-with-exception",
                response.getFeedbackMsg().getError());
    }
}