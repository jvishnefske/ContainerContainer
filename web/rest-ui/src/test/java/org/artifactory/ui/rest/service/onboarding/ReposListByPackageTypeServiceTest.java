package org.artifactory.ui.rest.service.onboarding;

import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.VirtualRepositoryRepoTypeConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.onboarding.ReposListByPackageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.ui.rest.service.onboarding.ReposListByPackageTypeService.EXCLUDE_NESTED_REPOS_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReposListByPackageTypeServiceTest {

    @Spy
    ReposListByPackageTypeService reposListByPackageTypeService;

    @Mock
    RepositoryService repositoryService;

    private AutoCloseable mocksClosable;

    @BeforeEach
    void setUp() {
        mocksClosable = MockitoAnnotations.openMocks(this);
        reposListByPackageTypeService.setRepositoryService(repositoryService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocksClosable.close();
    }

    @Test
    void executeWithEmptyPackageType() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn(null);
        reposListByPackageTypeService.execute(request, response);
        assertEquals(HttpStatus.SC_BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void executeWithMoreThanExcludeLimit() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn("docker");
        when(request.getQueryParamByKey("excludeReposInVirtual")).thenReturn("true");
        List<RepoConfig> repoConfigs = new ArrayList<>();
        List<String> localsKey = new ArrayList<>();
        for (int i = 0; i < EXCLUDE_NESTED_REPOS_LIMIT; i++) {
            RepoConfig repoConfigLocal = mock(LocalRepoConfig.class);
            when(repoConfigLocal.getKey()).thenReturn("docker-local-" + i);
            when(repoConfigLocal.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
            when(repoConfigLocal.isReal()).thenReturn(true);
            repoConfigs.add(repoConfigLocal);
            localsKey.add("docker-local-" + i);
        }

        Map<RepoDetailsType, List<String>> results = new HashMap<>();
        results.put(RepoDetailsType.VIRTUAL, List.of("docker-virtual"));
        results.put(RepoDetailsType.LOCAL, localsKey);

        RepoConfig repoConfigVirtual = mock(VirtualRepoConfig.class);
        VirtualRepositoryRepoTypeConfig virtualTypeRepositoryConfig = mock(VirtualRepositoryRepoTypeConfig.class);
        when(virtualTypeRepositoryConfig.getRepositoryRefs()).thenReturn(localsKey);
        when(repoConfigVirtual.getKey()).thenReturn("docker-virtual");
        when(repoConfigVirtual.getRepoType()).thenReturn(RepoDetailsType.VIRTUAL);
        when(repoConfigVirtual.getRepoTypeConfig()).thenReturn(virtualTypeRepositoryConfig);
        when(repoConfigVirtual.isReal()).thenReturn(false);
        repoConfigs.add(repoConfigVirtual);

        when(repositoryService.getRepoConfigsByPackageType(RepoType.Docker))
                .thenReturn(repoConfigs);

        reposListByPackageTypeService.execute(request, response);
        assertEquals(HttpStatus.SC_OK, response.getResponseCode());
        assertFalse(((ReposListByPackageType) response.getIModel()).isHideNestedReposSupported());
        assertEquals(results, ((ReposListByPackageType) response.getIModel()).getRepoTypeToReposKeysMap());
    }

    @ParameterizedTest(name = "excludeReposInVirtual={0}")
    @MethodSource("executeTestData")
    void execute(String excludeReposInVirtual, ReposListByPackageType results) {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();
        when(request.getImodel()).thenReturn("docker");
        when(request.getQueryParamByKey("excludeReposInVirtual")).thenReturn(excludeReposInVirtual);

        ///local
        RepoConfig repoConfigLocal = mock(LocalRepoConfig.class);
        when(repoConfigLocal.getKey()).thenReturn("docker-local");
        when(repoConfigLocal.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfigLocal.isReal()).thenReturn(true);

        RepoConfig repoConfigLocal2 = mock(LocalRepoConfig.class);
        when(repoConfigLocal2.getKey()).thenReturn("docker-local2");
        when(repoConfigLocal2.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfigLocal2.isReal()).thenReturn(true);

        //virtual
        RepoConfig repoConfigVirtual = mock(VirtualRepoConfig.class);
        VirtualRepositoryRepoTypeConfig virtualTypeRepositoryConfig = mock(VirtualRepositoryRepoTypeConfig.class);
        when(virtualTypeRepositoryConfig.getRepositoryRefs()).thenReturn(List.of("docker-local"));
        when(repoConfigVirtual.getKey()).thenReturn("docker-virtual");
        when(repoConfigVirtual.getRepoType()).thenReturn(RepoDetailsType.VIRTUAL);
        when(repoConfigVirtual.getRepoTypeConfig()).thenReturn(virtualTypeRepositoryConfig);
        when(repoConfigVirtual.isReal()).thenReturn(false);

        when(repositoryService.getRepoConfigsByPackageType(RepoType.Docker))
                .thenReturn(List.of(repoConfigLocal, repoConfigLocal2, repoConfigVirtual));

        reposListByPackageTypeService.execute(request, response);
        assertEquals(HttpStatus.SC_OK, response.getResponseCode());
        assertTrue(response.getIModel() instanceof ReposListByPackageType);
        assertEquals(results.getRepoTypeToReposKeysMap(),
                ((ReposListByPackageType) response.getIModel()).getRepoTypeToReposKeysMap());
    }

    @Test
    void executeWithoutVirtual() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = new ArtifactoryRestResponse();
        Map<RepoDetailsType, List<String>> results = new HashMap<>();
        results.put(RepoDetailsType.LOCAL, List.of("docker-local"));
        when(request.getImodel()).thenReturn("docker");
        when(request.getQueryParamByKey("excludeReposInVirtual")).thenReturn("true");

        ///local
        RepoConfig repoConfigLocal = mock(LocalRepoConfig.class);
        when(repoConfigLocal.getKey()).thenReturn("docker-local");
        when(repoConfigLocal.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfigLocal.isReal()).thenReturn(true);


        when(repositoryService.getRepoConfigsByPackageType(RepoType.Docker))
                .thenReturn(List.of(repoConfigLocal));

        reposListByPackageTypeService.execute(request, response);
        assertEquals(HttpStatus.SC_OK, response.getResponseCode());
        assertEquals(results, ((ReposListByPackageType) response.getIModel()).getRepoTypeToReposKeysMap());
    }

    private static Stream<Arguments> executeTestData() {
        Map<RepoDetailsType, List<String>> resultsWithExcludeReposInVirtual = new HashMap<>();
        resultsWithExcludeReposInVirtual.put(RepoDetailsType.VIRTUAL, List.of("docker-virtual"));
        resultsWithExcludeReposInVirtual.put(RepoDetailsType.LOCAL, List.of("docker-local2"));
        Map<RepoDetailsType, List<String>> resultsWithoutExcludeReposInVirtual = new HashMap<>();
        resultsWithoutExcludeReposInVirtual.put(RepoDetailsType.VIRTUAL, List.of("docker-virtual"));
        resultsWithoutExcludeReposInVirtual.put(RepoDetailsType.LOCAL, List.of("docker-local", "docker-local2"));
        return Stream.of(
                Arguments.of("true", new ReposListByPackageType(true, resultsWithExcludeReposInVirtual)),
                Arguments.of("false", new ReposListByPackageType(true, resultsWithoutExcludeReposInVirtual))
        );
    }
}