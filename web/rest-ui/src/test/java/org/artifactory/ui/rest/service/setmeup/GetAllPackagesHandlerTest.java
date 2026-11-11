package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.RepoKeyAutoCompleteComparator;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.utils.RequestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetAllPackagesHandlerTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ArtifactoryRestRequest<Void> request;
    @Mock
    private RestResponse<String> response;
    private List<RepoConfig> repoConfigs;
    @InjectMocks
    private GetAllPackagesHandler getAllPackagesHandler;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        repoConfigs = createRepoConfigs();

        doAnswer(invocation -> {
            Set<RepoDetailsType> repoTypeSet = invocation.getArgument(0);
            String projectKey = invocation.getArgument(1);
            return repoConfigs.stream()
                    .filter(repoConfig -> repoTypeSet.contains(repoConfig.getRepoType()))
                    .filter(repoConfig -> ProjectsUtils.isDefaultProject(projectKey) ||
                            repoConfig.getKey().startsWith(projectKey))
                    .toList();
        }).when(repositoryConfigService).getRepoConfigsByTypesAndProject(any(), any());

        doAnswer(invocation -> {
            String repoKey = invocation.getArgument(0);
            return !repoKey.contains("not-permitted-at-all");
        }).when(authorizationService).userHasPermissionsOnRepositoryRoot(any(), any(), any());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("projectProvider")
    void testGetSetMeUpPackages(String projectKey, List<String> expectedRepoTypes) {
        when(RequestUtils.getProjectKeyFromRequest(request)).thenReturn(projectKey);

        getAllPackagesHandler.execute(request, response);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(response).iModelList(captor.capture());
        List<String> repoTypes = captor.getValue();
        assertNotNull(repoTypes);
        assertEquals(repoTypes.size(), expectedRepoTypes.size());
        assertTrue(repoTypes.containsAll(expectedRepoTypes));
    }

    @Test
    void testGetLimit() {
        when(request.getQueryParamByKey("limit")).thenReturn("10");
        long limit = getAllPackagesHandler.getLimit(request);
        assertEquals(10, limit);

        when(request.getQueryParamByKey("limit")).thenReturn("invalid");
        limit = getAllPackagesHandler.getLimit(request);
        assertEquals(ConstantValues.setMeUpDefaultLimit.getLong(), limit);

        when(request.getQueryParamByKey("limit")).thenReturn(null);
        limit = getAllPackagesHandler.getLimit(request);
        assertEquals(ConstantValues.setMeUpDefaultLimit.getLong(), limit);
    }

    @Test
    void testGetDefaultLimit() {
        getOrCreateArtifactoryHomeStub().getArtifactoryProperties()
                .setProperty(ConstantValues.setMeUpDefaultLimit.getPropertyName(), "50");

        long limit = getAllPackagesHandler.getLimit(request);
        assertEquals(50L, limit);

        getOrCreateArtifactoryHomeStub().getArtifactoryProperties()
                .setProperty(ConstantValues.setMeUpDefaultLimit.getPropertyName(), "0");
        limit = getAllPackagesHandler.getLimit(request);
        assertEquals(Long.MAX_VALUE, limit);
    }

    @Test
    void testGetComparator() {
        when(request.getQueryParamByKey("autoCompletion")).thenReturn("true");
        Comparator<RepoConfig> comparator = getAllPackagesHandler.getComparator(request, "keySearch");
        assertInstanceOf(RepoKeyAutoCompleteComparator.class, comparator);

        when(request.getQueryParamByKey("autoCompletion")).thenReturn("false");
        comparator = getAllPackagesHandler.getComparator(request, "keySearch");
        assertEquals(BaseSetMeUpHandler.COMPARATOR_LEXICOGRAPHIC, comparator);

        when(request.getQueryParamByKey("autoCompletion")).thenReturn("invalid-value");
        comparator = getAllPackagesHandler.getComparator(request, "keySearch");
        assertEquals(BaseSetMeUpHandler.COMPARATOR_LEXICOGRAPHIC, comparator);

        when(request.getQueryParamByKey("autoCompletion")).thenReturn(null);
        comparator = getAllPackagesHandler.getComparator(request, "keySearch");
        assertEquals(BaseSetMeUpHandler.COMPARATOR_LEXICOGRAPHIC, comparator);
    }

    private List<RepoConfig> createRepoConfigs() {
        return List.of(
                LocalRepoConfig.create("proj-1-local", RepoType.Maven),
                RemoteRepoConfig.create("proj-1-remote", RepoType.Maven),
                FederatedRepoConfig.create("proj-1-federated", RepoType.Maven),
                VirtualRepoConfig.create("proj-1-virtual", RepoType.Maven),
                LocalRepoConfig.create("proj-1-not-permitted-read", RepoType.Maven),
                LocalRepoConfig.create("proj-1-not-permitted-deploy", RepoType.Maven),
                LocalRepoConfig.create("proj-1-not-permitted-at-all", RepoType.Generic),

                LocalRepoConfig.create("proj-2-local", RepoType.Maven),
                RemoteRepoConfig.create("proj-2-remote", RepoType.Npm),
                FederatedRepoConfig.create("proj-2-federated", RepoType.Maven),
                VirtualRepoConfig.create("proj-2-virtual", RepoType.Maven),
                LocalRepoConfig.create("proj-2-not-permitted-read", RepoType.Maven),
                LocalRepoConfig.create("proj-2-not-permitted-deploy", RepoType.Maven),
                LocalRepoConfig.create("proj-2-not-permitted-at-all", RepoType.Generic),

                LocalRepoConfig.create("proj-3-local", RepoType.Maven),
                RemoteRepoConfig.create("proj-3-remote", RepoType.Npm),
                FederatedRepoConfig.create("proj-3-federated", RepoType.Docker),
                VirtualRepoConfig.create("proj-3-virtual", RepoType.Maven),
                LocalRepoConfig.create("proj-3-not-permitted-read", RepoType.Maven),
                LocalRepoConfig.create("proj-3-not-permitted-deploy", RepoType.Maven),
                LocalRepoConfig.create("proj-3-not-permitted-at-all", RepoType.Generic),

                LocalRepoConfig.create(IN_TRANSIT_REPO_KEY, RepoType.Generic)
        );
    }

    private static Stream<Arguments> projectProvider() {
        return Stream.of(
                Arguments.of(null, List.of("maven", "npm", "docker")),
                Arguments.of(null, List.of("maven", "npm", "docker")),
                Arguments.of("", List.of("maven", "npm", "docker")),
                Arguments.of("", List.of("maven", "npm", "docker")),
                Arguments.of("default", List.of("maven", "npm", "docker")),
                Arguments.of("default", List.of("maven", "npm", "docker")),
                Arguments.of("proj-1", List.of("maven")),
                Arguments.of("proj-2", List.of("maven", "npm")),
                Arguments.of("proj-3", List.of("maven", "npm", "docker")));
    }
}