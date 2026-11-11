package org.artifactory.rest.resource.repositories;

import com.google.common.collect.Lists;
import com.google.gson.JsonParser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Providers;
import lombok.NonNull;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.metrics.cuj.RepoCreationMetricsWrapper;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetails;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.model.repositories.RepositoryExistenceModel;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.StringList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;
import org.testng.Assert;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class RepositoriesResourceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ArtifactoryApplicationContext applicationContext;
    @Mock
    private AddonsManager addonsManagerMock;
    @Mock
    private RestAddon restAddonMock;
    @Mock
    private Providers providersMock;
    @Mock
    private HttpHeaders requestHeadersMock;
    @InjectMocks
    private RepositoriesResource repositoriesResource;

    private AutoCloseable mockitoAutoCloseable;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
        when(httpRequest.getHeader("x-forwarded-host")).thenReturn("localhost:8082");
        when(httpRequest.getHeader("x-forwarded-proto")).thenReturn("http");
        CentralConfigService centralConfigService = mock(CentralConfigService.class, RETURNS_DEEP_STUBS);
        when(centralConfigService.getDescriptor().getUrlBase()).thenReturn(null);
        when(applicationContext.getCentralConfig()).thenReturn(centralConfigService);
        when(httpRequest.getContextPath()).thenReturn("/artifactory");
        when(addonsManagerMock.addonByType(RestAddon.class)).thenReturn(restAddonMock);

        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    private void setFederatedAsLocalEnabled(boolean enabled) {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.federatedAsLocalEnabled.getPropertyName(), String.valueOf(enabled));
    }

    @Test
    void getAllRepoDetailsShouldBeSortedByTypeAndNameAlphabetically() {
        setFederatedAsLocalEnabled(true); // Enable federated-as-local for this test
        when(authorizationService.userHasPermissionsOnRepositoryRoot(any())).thenReturn(true);
        List<LocalConfig> localRepoConfigs =
                List.of(
                        LocalRepoConfig.create("c-maven-local", RepoType.Maven),
                        LocalRepoConfig.create("c-generic-local", RepoType.Generic),
                        LocalRepoConfig.create("a-maven-local", RepoType.Maven),
                        LocalRepoConfig.create("a-generic-local", RepoType.Generic),
                        LocalRepoConfig.create("b-maven-local", RepoType.Maven),
                        LocalRepoConfig.create("b-generic-local", RepoType.Generic));
        List<RepoConfig> remoteRepoConfigs =
                List.of(RemoteRepoConfig.create("c-maven-remote", RepoType.Maven),
                        RemoteRepoConfig.create("c-generic-remote", RepoType.Generic),
                        RemoteRepoConfig.create("a-maven-remote", RepoType.Maven),
                        RemoteRepoConfig.create("a-generic-remote", RepoType.Generic),
                        RemoteRepoConfig.create("b-maven-remote", RepoType.Maven),
                        RemoteRepoConfig.create("b-generic-remote", RepoType.Generic));
        List<RepoConfig> federatedRepoConfigs =
                List.of(FederatedRepoConfig.create("c-maven-federated", RepoType.Maven),
                        FederatedRepoConfig.create("c-generic-federated", RepoType.Generic),
                        FederatedRepoConfig.create("a-maven-federated", RepoType.Maven),
                        FederatedRepoConfig.create("a-generic-federated", RepoType.Generic),
                        FederatedRepoConfig.create("b-maven-federated", RepoType.Maven),
                        FederatedRepoConfig.create("b-generic-federated", RepoType.Generic));
        List<RepoConfig> virtualRepoConfigs =
                List.of(VirtualRepoConfig.create("c-maven-virtual", RepoType.Maven),
                        VirtualRepoConfig.create("c-generic-virtual", RepoType.Generic),
                        VirtualRepoConfig.create("a-maven-virtual", RepoType.Maven),
                        VirtualRepoConfig.create("a-generic-virtual", RepoType.Generic),
                        VirtualRepoConfig.create("b-maven-virtual", RepoType.Maven),
                        VirtualRepoConfig.create("b-generic-virtual", RepoType.Generic));
        when(repositoryService.getLocalRepoConfigsExcludingInfoReposByProject(any())).thenReturn(
                localRepoConfigs);
        when(repositoryService.getRepoConfigsByProjectAndType(any(), eq(Set.of(REMOTE))))
                .thenReturn(remoteRepoConfigs);
        when(repositoryService.getRepoConfigsByProjectAndType(any(), eq(Set.of(RepoDetailsType.FEDERATED))))
                .thenReturn(federatedRepoConfigs);
        when(repositoryService.getRepoConfigsByProjectAndType(any(), eq(Set.of(RepoDetailsType.VIRTUAL))))
                .thenReturn(virtualRepoConfigs);

        Response response = repositoriesResource.getAllRepoDetails(null, null, null);

        assertEquals(HttpStatus.SC_OK, response.getStatus());
        List<RepoDetails> actualRepoDetails = (List<RepoDetails>) response.getEntity();
        assertNotNull(actualRepoDetails);

        List<RepoConfig> allRepoConfigs = Lists.newArrayList();
        allRepoConfigs.addAll(localRepoConfigs);
        allRepoConfigs.addAll(remoteRepoConfigs);
        allRepoConfigs.addAll(federatedRepoConfigs);
        allRepoConfigs.addAll(virtualRepoConfigs);

        List<RepoDetails> expectedRepoDetails = allRepoConfigs.stream().map(this::toRepoDetails)
                .sorted(Comparator.comparing(RepoDetails::getType).thenComparing(RepoDetails::getKey)).toList();

        assertEquals(expectedRepoDetails.size(), actualRepoDetails.size());

        // For this test only repo type, package type and key ordering is important
        normalizeActualRepoDetails(actualRepoDetails);

        String expectedRepoDetailsJson = JsonUtils.getInstance().writeValueAsString(expectedRepoDetails);
        String actualRepoDetailsJson = JsonUtils.getInstance().writeValueAsString(actualRepoDetails);

        assertEquals(JsonParser.parseString(expectedRepoDetailsJson),
                JsonParser.parseString(actualRepoDetailsJson));
    }

    @Test
    void testHasAnyRepositories() {
        Field addonsManagerField = ReflectionUtils.findField(RepositoriesResource.class, "addonsManager");
        Assert.assertNotNull(addonsManagerField);
        ReflectionUtils.makeAccessible(addonsManagerField);
        RestAddon restAddon = mock(RestAddon.class);
        AddonsManager addonsManager = mock(AddonsManager.class);
        ReflectionUtils.setField(addonsManagerField, repositoriesResource, addonsManager);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        String projectKey = "project1";
        Set<String> types = Set.of("LOCAL", "REMOTE");
        when(restAddon.hasRepositoriesByProjectAndTypes(projectKey, types))
                .thenReturn(Response.ok(new RepositoryExistenceModel(true, Set.of(LOCAL, REMOTE), projectKey)).build());

        Response response = repositoriesResource.hasAnyRepositories(projectKey, types);
        RepositoryExistenceModel repositoryExistence = (RepositoryExistenceModel) response.getEntity();

        assertNotNull(response);
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        assertTrue(repositoryExistence.isExists());
    }

    @Test
    void testCreateRepository() {
        when(requestHeadersMock.getMediaType()).thenReturn(MediaType.APPLICATION_JSON_TYPE);
        // Dummy implementation of wrapper that should assert that passed in lambdas provide correct values
        var dummyRepoMetricsWrapper = new RepoCreationMetricsWrapper(null) {
            @Override
            public Response createRepoAndCollectMetrics(@NonNull Supplier<String> repoTypeSupplier,
                    @NonNull Supplier<String> repoPackageTypeSupplier, @NonNull Callable<Response> createRepoMethod,
                    Providers providers) {
                assertEquals("local", repoTypeSupplier.get());
                assertEquals("docker", repoPackageTypeSupplier.get());
                assertEquals(providersMock, providers);
                try {
                    createRepoMethod.call();
                } catch (Exception e) {
                    fail("Unexpected exception: " + e.getMessage());
                }
                return Response.ok().entity("test").build();
            }
        };
        ReflectionTestUtils.setField(repositoriesResource, "repoCreationMetricsWrapper", dummyRepoMetricsWrapper);
        Map<String, Object> repoConfigMap = new HashMap<>();
        repoConfigMap.put("rclass", "local");
        repoConfigMap.put("packageType", "docker");
        var response = repositoriesResource.createRepository("repoKey1", repoConfigMap);
        verify(restAddonMock, times(1)).createRepository("repoKey1", repoConfigMap,
                MediaType.APPLICATION_JSON_TYPE);
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        assertEquals("test", response.getEntity());
    }

    @Test
    void testGetAllRepositoryConfigurations() {
        repositoriesResource.getAllRepositoryConfigurations(null, null);
        verify(restAddonMock, times(1)).getAllRepositoryConfigurations(null, null);
    }

    @Test
    void testGetAllRepositoryConfigurationsWithPackageTypes() {
        StringList packageTypes = new StringList("maven, npm");
        repositoriesResource.getAllRepositoryConfigurations(packageTypes, null);
        verify(restAddonMock, times(1)).getAllRepositoryConfigurations(packageTypes, null);
    }

    @Test
    void testGetAllRepositoryConfigurationsWithRepoTypes() {
        StringList repoTypes = new StringList("local,remote");
        repositoriesResource.getAllRepositoryConfigurations(null, repoTypes);
        verify(restAddonMock, times(1)).getAllRepositoryConfigurations(null, repoTypes);
    }

    @Test
    void testGetAllRepositoryConfigurationsWithBothFilters() {
        StringList packageTypes = new StringList("maven");
        StringList repoTypes = new StringList("local");
        repositoriesResource.getAllRepositoryConfigurations(packageTypes, repoTypes);
        verify(restAddonMock, times(1)).getAllRepositoryConfigurations(packageTypes, repoTypes);
    }

    @Test
    void testGetAllRepositoryConfigurationsWithEmptyFilters() {
        StringList emptyPackageTypes = new StringList("");
        StringList emptyRepoTypes = new StringList("");
        repositoriesResource.getAllRepositoryConfigurations(emptyPackageTypes, emptyRepoTypes);
        verify(restAddonMock, times(1)).getAllRepositoryConfigurations(emptyPackageTypes, emptyRepoTypes);
    }

    @Test
    void testGetAllRepositoryConfigurationsWithLargeFilters() {
        StringList packageTypes = new StringList("maven,npm,gradle,ivy,sbt");
        StringList repoTypes = new StringList("local,remote,virtual,federated");
        repositoriesResource.getAllRepositoryConfigurations(packageTypes, repoTypes);
        verify(restAddonMock, times(1)).getAllRepositoryConfigurations(packageTypes, repoTypes);
    }

    @Test
    void testGetAllRepositoryConfigurationsDelegatesToRestAddon() {
        StringList packageTypes = new StringList("maven");
        StringList repoTypes = new StringList("local");
        Response mockResponse = mock(Response.class);
        when(restAddonMock.getAllRepositoryConfigurations(packageTypes, repoTypes)).thenReturn(mockResponse);
        Response response = repositoriesResource.getAllRepositoryConfigurations(packageTypes, repoTypes);
        verify(restAddonMock, times(1)).getAllRepositoryConfigurations(packageTypes, repoTypes);
        assertEquals(mockResponse, response);
    }

    @Test
    void testGetAllRepositoryConfigurationsWithCaseSensitiveFilters() {
        StringList packageTypes = new StringList("MAVEN,Npm,GRADLE");
        StringList repoTypes = new StringList("LOCAL,Remote");
        repositoriesResource.getAllRepositoryConfigurations(packageTypes, repoTypes);
        verify(restAddonMock, times(1)).getAllRepositoryConfigurations(packageTypes, repoTypes);
    }

    @ParameterizedTest(name = "getAllRepoDetails type=local: flagEnabled={0} → expectedCount={1}")
    @CsvSource({
            // Flag enabled: returns local + federated (as local)
            "true, 2",
            // Flag disabled: returns only local repos
            "false, 1"
    })
    void getAllRepoDetails_shouldReturnFederatedAsLocal_basedOnFlag(boolean flagEnabled, int expectedCount) {
        setFederatedAsLocalEnabled(flagEnabled);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(any())).thenReturn(true);

        List<LocalConfig> localRepoConfigs = List.of(
                LocalRepoConfig.create("local-repo", RepoType.Generic));
        List<RepoConfig> federatedRepoConfigs = List.of(
                FederatedRepoConfig.create("federated-repo", RepoType.Generic));

        when(repositoryService.getLocalRepoConfigsExcludingInfoReposByProject(any()))
                .thenReturn(localRepoConfigs);
        when(repositoryService.getRepoConfigsByProjectAndType(any(), eq(Set.of(RepoDetailsType.FEDERATED))))
                .thenReturn(federatedRepoConfigs);

        Response response = repositoriesResource.getAllRepoDetails("local", null, null);

        assertEquals(HttpStatus.SC_OK, response.getStatus());
        List<RepoDetails> actualRepoDetails = (List<RepoDetails>) response.getEntity();
        assertEquals(expectedCount, actualRepoDetails.size());

        if (flagEnabled) {
            // Verify federated repo is returned with LOCAL type
            RepoDetails federatedAsLocal = actualRepoDetails.stream()
                    .filter(r -> "federated-repo".equals(r.getKey()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(LOCAL, federatedAsLocal.getType());
        }
    }

    @Test
    void getAllRepoDetails_shouldReturnFederatedAsFederated_whenTypeIsFederated() {
        when(authorizationService.userHasPermissionsOnRepositoryRoot(any())).thenReturn(true);

        List<RepoConfig> federatedRepoConfigs = List.of(
                FederatedRepoConfig.create("federated-repo", RepoType.Generic));

        when(repositoryService.getRepoConfigsByProjectAndType(any(), eq(Set.of(RepoDetailsType.FEDERATED))))
                .thenReturn(federatedRepoConfigs);

        Response response = repositoriesResource.getAllRepoDetails("federated", null, null);

        assertEquals(HttpStatus.SC_OK, response.getStatus());
        List<RepoDetails> actualRepoDetails = (List<RepoDetails>) response.getEntity();

        assertEquals(1, actualRepoDetails.size());
        // Federated repo should have FEDERATED type when explicitly requested
        assertEquals(FEDERATED, actualRepoDetails.get(0).getType());
    }

    private void normalizeActualRepoDetails(List<RepoDetails> actualRepoDetails) {
        for (RepoDetails repoDetail : actualRepoDetails) {
            repoDetail.setUrl(null);
        }
    }

    private RepoDetails toRepoDetails(RepoConfig repoConfig) {
        RepoDetails repoDetails = new RepoDetails();
        repoDetails.setKey(repoConfig.getKey());
        // Federated repos are returned as FEDERATED type when no type filter is applied
        RepoDetailsType type = repoConfig instanceof FederatedRepoConfig ? FEDERATED : repoConfig.getRepoType();
        repoDetails.setType(type);
        repoDetails.setDescription(repoConfig.getBaseConfig().getDescription());
        repoDetails.setPackageType(repoConfig.getPackageType());
        if (repoConfig instanceof RemoteRepoConfig remoteRepoConfig) {
            repoDetails.setUrl(remoteRepoConfig.getRepoTypeConfig().getUrl());
        }
        return repoDetails;
    }
}