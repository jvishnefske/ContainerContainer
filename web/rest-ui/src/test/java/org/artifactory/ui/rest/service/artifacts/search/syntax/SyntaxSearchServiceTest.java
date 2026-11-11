package org.artifactory.ui.rest.service.artifacts.search.syntax;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Response;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Search;
import org.artifactory.ui.rest.model.artifacts.search.syntax.State;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Type;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.QueryControls;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.FILE;
import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.FOLDER;
import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.REPO;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_NAVIGATION_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyntaxSearchServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private RepoConfig repoConfig;
    @Mock
    private SecurityService securityService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private SearchFunctionProvider searchFunctionProvider;
    @Mock
    private JdbcHelper jdbcHelper;
    @Mock
    private ProjectsService projectsService;

    private SyntaxSearchServiceForTest service;

    @BeforeEach
    void setUp() {
        service = new SyntaxSearchServiceForTest(
                jdbcHelper, repositoryService,
                securityService, authorizationService, searchFunctionProvider,
                projectsService);
    }

    @Test
    void syntaxError() {
        MemoryAppender memoryAppender = MemoryAppenderUtil
                .createMemoryAppender(SyntaxSearchServiceForTest.class, Level.WARN);
        try {
            SearchFunctionProvider testSearchFunctionProvider = new FailedTestSearchFunctions();

            SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                    securityService, authorizationService, testSearchFunctionProvider, projectsService);

            Query query = new Query("a", null, null, null);
            Search search = new Search(query, 5, null, null, false);

            ArtifactoryRestRequest request = mockArtifactoryRestRequest(search);
            ArtifactoryRestResponse response = new ArtifactoryRestResponse();

            testService.execute(request, response);

            assertEquals(200, response.getResponseCode());

            List<ILoggingEvent> messages = memoryAppender.search("SyntaxSearch SQL syntax error", Level.WARN);
        } finally {
            memoryAppender.stop();
            ((Logger) LoggerFactory.getLogger(SyntaxSearchServiceForTest.class)).detachAppender(memoryAppender);
        }
    }

    @Test
    void doSearch_shouldThrowSyntaxSearchServiceException_whenExecuteSearchStagesThrows() {
        SearchFunctionProvider throwingProvider = new ThrowingSearchFunctionProvider();
        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, throwingProvider, projectsService);

        Query query = new Query("a", null, null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mockArtifactoryRestRequest(search);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);

        assertTrue(response.getFeedbackMsg().hasError(),
                "Response should contain error when doSearch catch block wraps exception");
        assertEquals("Failed to execute syntax search", response.getFeedbackMsg().getError(),
                "Error message should match SyntaxSearchServiceException thrown from doSearch catch block");
    }

    @Test
    void virtualRepositorySearch() {
        Repository repo = new Repository("local-repo", "local", "generic", 48L, 0L);
        Node file = new Node(43L, "local-repo", "path", "file", 0, 48L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(repo), List.of(), List.of(file));

        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn("local-repo");
        when(localRepo.isLocal()).thenReturn(true);
        when(localRepo.isReal()).thenReturn(true);

        RemoteRepo<?, ?> remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.getKey()).thenReturn("remote-repo");
        when(remoteRepo.isLocal()).thenReturn(false);
        when(remoteRepo.isReal()).thenReturn(true);

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getKey()).thenReturn("virtual-repo");
        when(virtualRepo.getLocalRepositories()).thenReturn(List.of(localRepo));
        when(virtualRepo.getRemoteRepositories()).thenReturn(List.of(remoteRepo));
        when(virtualRepo.getVirtualRepositories()).thenReturn(List.of());
        when(virtualRepo.isPathPatternValid(any(RepoPath.class), anyString())).thenReturn(true);
        when(repositoryService.virtualRepositoryByKey("virtual-repo")).thenReturn(virtualRepo);

        when(securityService.isAdmin()).thenReturn(true);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("a", "virtual-repo", null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest<?> request = mockArtifactoryRestRequest(search);
        ArtifactoryRestResponse<?> response = new ArtifactoryRestResponse<>();

        testService.execute(request, response);

        assertEquals(200, response.getResponseCode());
        verify(repositoryService).virtualRepositoryByKey("virtual-repo");
        verify(virtualRepo).getLocalRepositories();
        verify(virtualRepo).getRemoteRepositories();
    }

    @Test
    void smokeAutocomplete() {
        Repository repo = new Repository("repo", "type", "packageType",
                48L, 0L);
        Node path = new Node(42L, "repo", "path", "path", 0,
                48L, 0L);
        Node file = new Node(43L, "repo", "path", "file", 0,
                48L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(repo), List.of(path), List.of(file));

        when(securityService.isAdmin()).thenReturn(true);
        when(repositoryService.getRepoConfigByKey("repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("a", null, null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mockArtifactoryRestRequest(search);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);

        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertEquals(3, iModel.results().size());
        assertEquals(Type.REPO, iModel.results().get(0).itemType());
        assertEquals(Type.FOLDER, iModel.results().get(1).itemType());
        assertEquals(Type.FILE, iModel.results().get(2).itemType());
        assertEquals(false, iModel.isPartialResponse());
    }

    @Test
    void smokeSearchRepo() {
        Repository repo = new Repository("repo", "type", "packageType",
                48L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(repo), List.of(), List.of(), false);

        ArtifactoryRestResponse response = getArtifactoryRestResponse(testSearchFunctionProvider, "repo");

        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertEquals(15, iModel.results().size());
        assertEquals(Type.REPO, iModel.results().getFirst().itemType());
        assertEquals(false, iModel.isPartialResponse());

        new SyntaxSearchServiceException(new RuntimeException());
        new SyntaxSearchServiceException("message");
    }

    @Test
    void smokeSearchFolder() {
        Node path = new Node(42L, "repo", "path", "path", 0,
                48L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(), List.of(path), List.of(), false);
        when(repositoryService.getRepoConfigByKey("repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        ArtifactoryRestResponse response = getArtifactoryRestResponse(testSearchFunctionProvider, "path");

        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertEquals(15, iModel.results().size());
        assertEquals(Type.FOLDER, iModel.results().getFirst().itemType());
        assertEquals(false, iModel.isPartialResponse());
    }

    @Test
    void smokeSearchFile() {
        Node file = new Node(43L, "repo", "path", "file", 0,
                48L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(), List.of(), List.of(file), false);

        when(repositoryService.getRepoConfigByKey("repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        ArtifactoryRestResponse response = getArtifactoryRestResponse(testSearchFunctionProvider, "path");

        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertEquals(15, iModel.results().size());
        assertEquals(Type.FILE, iModel.results().getFirst().itemType());
        assertEquals(false, iModel.isPartialResponse());
    }

    private @NonNull ArtifactoryRestResponse getArtifactoryRestResponse(
            SearchFunctionProvider testSearchFunctionProvider,
            String queryText) {
        when(securityService.isAdmin()).thenReturn(true);

        ItemActionsPopulator itemActionsPopulator = mock(ItemActionsPopulator.class);
        JdbcHelper jdbcHelper = mock(JdbcHelper.class);
        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(jdbcHelper, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService,
                itemActionsPopulator);

        Query query = new Query(queryText, null, null, null);
        Search search = new Search(query, 15, null, null, false);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(search);
        when(request.getQueryParamByKey("projectKey")).thenReturn(null);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);
        return response;
    }

    @Test
    void whenStopState() {
        SyntaxSearchServiceForTest testService = getSyntaxSearchServiceForTest();

        Query query = new Query("a", null, null, null);
        Search search = new Search(query, 10, State.STOP_STATE, null, false);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(search);
        when(request.getQueryParamByKey("projectKey")).thenReturn(null);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);

        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertTrue(iModel.results().isEmpty());
        assertEquals(State.STOP_STATE, iModel.state());
    }

    @Test
    void whenEmptyQuery() {
        SyntaxSearchServiceForTest testService = getSyntaxSearchServiceForTest();

        Query query = new Query(null, null, null, null);
        Search search = new Search(query, 10, null, null, false);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(search);
        when(request.getQueryParamByKey("projectKey")).thenReturn(null);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> testService.execute(request, response),
                "Should throw IllegalArgumentException when query results in empty search types"
        );

        assertEquals("Unable to determine search types from query", exception.getMessage());
    }

    @Test
    void whenNullSearch() {
        SyntaxSearchServiceForTest testService = getSyntaxSearchServiceForTest();

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(null);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> testService.execute(request, response)
        );

        assertEquals("Search request can't be empty", exception.getMessage());
    }

    @Test
    void whenNullQuery() {
        SyntaxSearchServiceForTest testService = getSyntaxSearchServiceForTest();

        Search search = new Search(null, 10, null, null, false);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(search);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> testService.execute(request, response)
        );

        assertEquals("Search query can't be empty", exception.getMessage());
    }

    @Test
    void whenFiltersHasItemTypes_ShouldUseFilterItemTypes() {
        Node file = new Node(43L, "repo", "path", "file", 0,
                48L, 0L);
        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(), List.of(), List.of(file));

        when(securityService.isAdmin()).thenReturn(true);
        when(repositoryService.getRepoConfigByKey("repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("a", null, null, null);
        Filters filters = new Filters(Set.of(Type.FILE), null, null, null, null);
        Search search = new Search(query, 5, null, filters, false);

        ArtifactoryRestRequest request = mockArtifactoryRestRequest(search);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);

        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertEquals(1, iModel.results().size());
        assertEquals(Type.FILE, iModel.results().getFirst().itemType());
    }

    @ParameterizedTest
    @MethodSource("observabilityFilteringTestCases")
    void searchResults_ShouldFilterBasedOnObservabilityVisibility(String description, String repoKey,
            String itemName, boolean isRepository, int expectedCount) {
        SearchFunctionProvider testSearchFunctionProvider;
        if (isRepository) {
            Repository repo = new Repository(repoKey, "type", "Generic", 48L, 0L);
            testSearchFunctionProvider = new TestSearchFunctions(List.of(repo), List.of(), List.of());
        } else {
            Node node = new Node(43L, repoKey, ".", itemName, 0, 48L, 0L);
            testSearchFunctionProvider = new TestSearchFunctions(List.of(), List.of(), List.of(node));
        }

        lenient().when(securityService.isAdmin()).thenReturn(true);

        RepoConfig repoConfig = mock(org.artifactory.model.RepoConfig.class);
        lenient().when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        lenient().when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);
        lenient().when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query(itemName, null, null, null);
        // Use limit 5 to force autocomplete mode (single-threaded) and avoid race conditions with mocks
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mockArtifactoryRestRequest(search);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);

        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertEquals(expectedCount, iModel.results().size(), description);
    }

    private static Stream<Arguments> observabilityFilteringTestCases() {
        return Stream.of(
                Arguments.of("Hidden observability repo (jfrog-usage-logs)", "jfrog-usage-logs", "jfrog", true, 0),
                Arguments.of("Hidden observability repo (jfrog-full-sync-info)", "jfrog-full-sync-info", "jfrog",
                        true, 0),
                Arguments.of("Visible observability repo (jfrog-logs)", "jfrog-logs", "jfrog", true, 1),
                Arguments.of("Visible observability repo (jfrog-billing-logs)", "jfrog-billing-logs", "jfrog", true, 1),
                Arguments.of("Node in hidden observability repo (jfrog-usage-logs)", "jfrog-usage-logs", "test.txt",
                        false, 0),
                Arguments.of("Node in hidden observability repo (jfrog-full-sync-info)", "jfrog-full-sync-info",
                        "test.txt", false, 0),
                Arguments.of("Node in visible observability repo (jfrog-logs)", "jfrog-logs", "log.txt", true, 1),
                Arguments.of("Node in visible observability repo (jfrog-billing-logs)", "jfrog-billing-logs",
                        "billing.log", true, 1)
        );
    }

    private SyntaxSearchServiceForTest getSyntaxSearchServiceForTest() {
        Repository repo = new Repository("repo", "type", "packageType",
                48L, 0L);
        Node path = new Node(42L, "repo", "path", "path", 0,
                48L, 0L);
        Node file = new Node(43L, "repo", "path", "file", 0,
                48L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(repo), List.of(path), List.of(file));

        return new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService,
                testSearchFunctionProvider, projectsService);
    }

    @Test
    void isVisible_AdminUser_Repository_ReturnsTrue() {
        Repository repository = new Repository("test-repo", "local", "maven",
                1234567890L, 0L);
        when(securityService.isAdmin()).thenReturn(true);

        boolean result = service.isVisible(repository);

        assertTrue(result, "Admin user should see all repositories");
        verify(securityService).isAdmin();
        verify(repositoryService, never()).isRepoPathVisible(any());
    }

    @Test
    void isVisible_AdminUser_Node_ReturnsTrue() {
        Node node = new Node(1L, "test-repo", "some/path", "file.txt", 1024L,
                1234567890L, 0L);
        when(securityService.isAdmin()).thenReturn(true);

        boolean result = service.isVisible(node);

        assertTrue(result, "Admin user should see all nodes");
        verify(securityService).isAdmin();
        verify(repositoryService, never()).isRepoPathVisible(any());
    }

    @Test
    void isVisible_NonAdminUser_RepositoryVisible_ReturnsTrue() {
        Repository repository = new Repository("test-repo", "local", "maven",
                1234567890L, 0L);
        when(securityService.isAdmin()).thenReturn(false);
        when(repositoryService.isRepoPathVisible(argThat(repoPath ->
                "test-repo".equals(repoPath.getRepoKey()) && repoPath.getPath().isEmpty()
        ))).thenReturn(true);

        boolean result = service.isVisible(repository);

        assertTrue(result, "Non-admin user should see visible repository");
        verify(securityService).isAdmin();
        verify(repositoryService).isRepoPathVisible(any(RepoPath.class));
    }

    @Test
    void isVisible_NonAdminUser_RepositoryNotVisible_ReturnsFalse() {
        Repository repository = new Repository("private-repo", "local", "maven",
                1234567890L, 0L);
        when(securityService.isAdmin()).thenReturn(false);
        when(repositoryService.isRepoPathVisible(argThat(repoPath ->
                "private-repo".equals(repoPath.getRepoKey()) && repoPath.getPath().isEmpty()
        ))).thenReturn(false);

        boolean result = service.isVisible(repository);

        assertFalse(result, "Non-admin user should not see invisible repository");
        verify(securityService).isAdmin();
        verify(repositoryService).isRepoPathVisible(any(RepoPath.class));
    }

    @Test
    void isVisible_NonAdminUser_NodeVisible_ReturnsTrue() {
        Node node = new Node(1L, "test-repo", "some/path", "file.txt", 1024L,
                1234567890L, 0L);
        when(securityService.isAdmin()).thenReturn(false);
        when(repositoryService.isRepoPathVisible(argThat(repoPath ->
                "test-repo".equals(repoPath.getRepoKey()) && "some/path/file.txt".equals(repoPath.getPath())
        ))).thenReturn(true);

        boolean result = service.isVisible(node);

        assertTrue(result, "Non-admin user should see visible node");
        verify(securityService).isAdmin();
        verify(repositoryService).isRepoPathVisible(any(RepoPath.class));
    }

    @Test
    void isVisible_NonAdminUser_NodeNotVisible_ReturnsFalse() {
        Node node = new Node(2L, "private-repo", "secret/path", "private.txt", 2048L,
                1234567890L, 0L);
        when(securityService.isAdmin()).thenReturn(false);
        when(repositoryService.isRepoPathVisible(argThat(repoPath ->
                "private-repo".equals(repoPath.getRepoKey()) && "secret/path/private.txt".equals(repoPath.getPath())
        ))).thenReturn(false);

        boolean result = service.isVisible(node);

        assertFalse(result, "Non-admin user should not see invisible node");
        verify(securityService).isAdmin();
        verify(repositoryService).isRepoPathVisible(any(RepoPath.class));
    }

    @Test
    void isVisible_UnsupportedItemType_ThrowsException() {
        String unsupportedItem = "not a Repository or Node";
        when(securityService.isAdmin()).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.isVisible(unsupportedItem),
                "Should throw IllegalArgumentException for unsupported item type"
        );

        assertTrue(
                exception.getMessage().contains("Unsupported item type"),
                "Exception message should indicate unsupported item type"
        );
        verify(securityService).isAdmin();
        verify(repositoryService, never()).isRepoPathVisible(any());
    }

    @Test
    void isVisible_AdminUser_BypassesVisibilityCheck() {
        Repository repository = new Repository("private-repo", "local", "maven",
                1234567890L, 0L);
        Node node = new Node(5L, "private-repo", "secret/path", "file.txt", 1024L,
                1234567890L, 0L);
        when(securityService.isAdmin()).thenReturn(true);

        boolean repoResult = service.isVisible(repository);
        boolean nodeResult = service.isVisible(node);

        assertTrue(repoResult, "Admin should see repository regardless of visibility");
        assertTrue(nodeResult, "Admin should see node regardless of visibility");
        verify(securityService, times(2)).isAdmin();
        verify(repositoryService, never()).isRepoPathVisible(any());
    }

    @Test
    void getRepositoryTypes_RepoNotFound_ReturnsEmpty() {
        String repoKey = "non-existent-repo";
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(null);

        var result = service.getRepositoryTypes(repoKey);

        verify(repositoryService).getRepoConfigByKey(repoKey);
        assertEquals(Optional.empty(), result);
    }

    @Test
    void getRepositoryTypes_LocalMavenRepo_ReturnsCorrectTypes() {
        String repoKey = "maven-local";
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);

        var maybeResult = service.getRepositoryTypes(repoKey);
        assertTrue(maybeResult.isPresent(), "Repository types should be present");

        var result = maybeResult.get();
        assertEquals(RepoDetailsType.LOCAL, result.repoType());
        assertEquals("maven", result.repoPkgType());
        verify(repositoryService).getRepoConfigByKey(repoKey);
        verify(repoConfig).getRepoType();
        verify(repoConfig).getPackageType();
    }

    @Test
    void getRepositoryTypes_CacheRepoKey_RemovesCacheSuffixAndGetsBaseRepo() {
        String cacheRepoKey = "maven-remote-cache";
        String baseRepoKey = "maven-remote";
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repositoryService.getRepoConfigByKey(baseRepoKey)).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);

        var maybeResult = service.getRepositoryTypes(cacheRepoKey);
        assertTrue(maybeResult.isPresent(), "Repository types should be present");

        var result = maybeResult.get();

        assertEquals(RepoDetailsType.REMOTE, result.repoType());
        assertEquals("maven", result.repoPkgType());
        verify(repositoryService).getRepoConfigByKey(baseRepoKey);
        verify(repoConfig).getRepoType();
        verify(repoConfig).getPackageType();
    }

    @Test
    void getRepositoryTypes_CacheRepoWithoutBaseRepo_ReturnsGenericLocal() {
        String cacheRepoKey = "nonexistent-cache";
        String baseRepoKey = "nonexistent";
        when(repositoryService.getRepoConfigByKey(baseRepoKey)).thenReturn(null);

        var result = service.getRepositoryTypes(cacheRepoKey);

        verify(repositoryService).getRepoConfigByKey(baseRepoKey);
        assertEquals(Optional.empty(), result);
    }

    @Test
    void getRepositoryTypes_NpmCacheRepo_RemovesCacheSuffix() {
        String cacheRepoKey = "npm-cache";
        String baseRepoKey = "npm";
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repositoryService.getRepoConfigByKey(baseRepoKey)).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Npm);

        var maybeResult = service.getRepositoryTypes(cacheRepoKey);
        assertTrue(maybeResult.isPresent(), "Repository types should be present");

        var result = maybeResult.get();

        assertEquals(RepoDetailsType.REMOTE, result.repoType());
        assertEquals("npm", result.repoPkgType());
        verify(repositoryService).getRepoConfigByKey(baseRepoKey);
    }

    @Test
    void matchesProject_WithEmptyProjectRepos_ShouldReturnTrue() {
        Repository repository = new Repository("maven-local", "local", "maven",
                123L, 0L);
        Set<String> projectRepos = Set.of();

        boolean result = service.matchesProject(repository, projectRepos);

        assertTrue(result);
    }

    @Test
    void matchesProject_WithRepository_WhenRepoInProject_ShouldReturnTrue() {
        Repository repository = new Repository("maven-local", "local", "maven",
                123L, 0L);
        Set<String> projectRepos = Set.of("maven-local", "npm-local");

        boolean result = service.matchesProject(repository, projectRepos);

        assertTrue(result);
    }

    @Test
    void matchesProject_WithRepository_WhenRepoNotInProject_ShouldReturnFalse() {
        Repository repository = new Repository("docker-local", "local", "docker",
                123L, 0L);
        Set<String> projectRepos = Set.of("maven-local", "npm-local");

        boolean result = service.matchesProject(repository, projectRepos);

        assertFalse(result);
    }

    @Test
    void matchesProject_WithNode_WhenRepoInProject_ShouldReturnTrue() {
        Node node = new Node(42L, "maven-local", "com/example", "artifact.jar", 1024L,
                123L, 0L);
        Set<String> projectRepos = Set.of("maven-local", "npm-local");

        boolean result = service.matchesProject(node, projectRepos);

        assertTrue(result);
    }

    @Test
    void matchesProject_WithNode_WhenRepoNotInProject_ShouldReturnFalse() {
        Node node = new Node(42L, "docker-local", "myimage", "latest", 2048L,
                123L, 0L);
        Set<String> projectRepos = Set.of("maven-local", "npm-local");

        boolean result = service.matchesProject(node, projectRepos);

        assertFalse(result);
    }

    @Test
    void matchesProject_WithUnsupportedType_ShouldThrowException() {
        String unsupportedItem = "unsupported";
        Set<String> projectRepos = Set.of("maven-local");

        assertThrows(IllegalArgumentException.class, () -> service.matchesProject(unsupportedItem, projectRepos));
    }

    @Test
    void execute_WithProjectKey_ShouldCallProjectsServiceWithCorrectKey() {
        Repository repo = new Repository("proj-repo", "local", "maven", 123L, 0L);
        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(repo), List.of(), List.of());

        when(securityService.isAdmin()).thenReturn(true);
        when(projectsService.getProjectRepositoriesKeys("test-project", false))
                .thenReturn(Set.of("proj-repo"));

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("proj", null, null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(search);
        when(request.getQueryParamByKey("projectKey")).thenReturn("test-project");

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);

        verify(projectsService).getProjectRepositoriesKeys("test-project", false);
        assertEquals(200, response.getResponseCode());
    }

    @ParameterizedTest(name = "projectKey={0}, expectedResults={2}")
    @MethodSource("projectFilteringTestCases")
    void execute_WithProjectKey_ShouldHandleProjectFiltering(
            String projectKey, Set<String> projectRepos, int expectedResultCount) {
        SearchFunctionProvider provider = new TestSearchFunctions(
                List.of(new Repository("proj-repo", "local", "maven", 123L, 0L),
                        new Repository("other-repo", "local", "maven", 124L, 0L)),
                List.of(), List.of());
        when(securityService.isAdmin()).thenReturn(true);
        if (!"default".equals(projectKey)) {
            when(projectsService.getProjectRepositoriesKeys(projectKey, false)).thenReturn(projectRepos);
        }

        SyntaxSearchServiceForTest testService = createTestService(provider);
        Search search = new Search(new Query("repo", null, null, null), 5, null, null, false);

        Response iModel = executeAndGetResponse(testService, mockArtifactoryRestRequest(search, projectKey));

        assertEquals(expectedResultCount, iModel.results().size());
        if ("default".equals(projectKey)) {
            verify(projectsService, never()).getProjectRepositoriesKeys(any(), anyBoolean());
        }
    }

    private static Stream<Arguments> projectFilteringTestCases() {
        return Stream.of(
                Arguments.of("default", Set.of(), 2),
                Arguments.of("my-project", Set.of("proj-repo"), 1)
        );
    }

    @Test
    void execute_WithNonExistentProject_ShouldReturnEmptyResults() {
        when(projectsService.getProjectRepositoriesKeys("non-existent-project", false))
                .thenReturn(Set.of());

        SyntaxSearchServiceForTest testService = createTestService(
                new TestSearchFunctions(List.of(), List.of(), List.of()));
        Search search = new Search(new Query("repo", null, null, null), 5, null, null, false);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(search);
        when(request.getQueryParamByKey("projectKey")).thenReturn("non-existent-project");

        Response iModel = executeAndGetResponse(testService, request);

        verify(projectsService).getProjectRepositoriesKeys("non-existent-project", false);
        assertTrue(iModel.results().isEmpty());
        assertEquals(Type.STOP, iModel.state().type());
    }

    @Test
    void searchShouldReturnFederatedRepository() {
        // Arrange
        Repository federatedRepo = new Repository("maven-federated", "federated", "maven",
                123L, 0L);
        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(federatedRepo), List.of(), List.of());

        when(securityService.isAdmin()).thenReturn(true);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("maven", null, null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(search);
        when(request.getQueryParamByKey("projectKey")).thenReturn(null);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        // Act
        testService.execute(request, response);

        // Assert
        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertEquals(1, iModel.results().size());
        assertEquals(Type.REPO, iModel.results().getFirst().itemType());
        assertEquals("maven-federated", iModel.results().getFirst().repoKey());
        assertEquals("federated", iModel.results().getFirst().repoType());
    }

    @Test
    void searchShouldReturnLocalRepositoryWithCorrectRepoType() {
        // Arrange
        Repository localRepo = new Repository("maven-local", "local", "maven",
                123L, 0L);
        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(localRepo), List.of(), List.of());

        when(securityService.isAdmin()).thenReturn(true);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("maven", null, null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(search);
        when(request.getQueryParamByKey("projectKey")).thenReturn(null);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        // Act
        testService.execute(request, response);

        // Assert
        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertEquals(1, iModel.results().size());
        assertEquals("maven-local", iModel.results().getFirst().repoKey());
        assertEquals("local", iModel.results().getFirst().repoType());
    }

    @Test
    void searchShouldReturnRemoteRepositoryWithCachedRepoType() {
        // Arrange
        Repository remoteRepo = new Repository("maven-remote", "remote", "maven",
                123L, 0L);
        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(remoteRepo), List.of(), List.of());

        when(securityService.isAdmin()).thenReturn(true);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("maven", null, null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(search);
        when(request.getQueryParamByKey("projectKey")).thenReturn(null);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        // Act
        testService.execute(request, response);

        // Assert
        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertEquals(1, iModel.results().size());
        assertEquals("maven-remote-cache", iModel.results().getFirst().repoKey());
        assertEquals("cached", iModel.results().getFirst().repoType());
    }

    @Test
    void searchShouldReturnMixedRepositoryTypes() {
        // Arrange - test that local, remote, and federated all appear correctly
        Repository localRepo = new Repository("npm-local", "local", "npm",
                100L, 0L);
        Repository remoteRepo = new Repository("npm-remote", "remote", "npm",
                200L, 0L);
        Repository federatedRepo = new Repository("npm-federated", "federated", "npm",
                300L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(localRepo, remoteRepo, federatedRepo), List.of(), List.of());

        when(securityService.isAdmin()).thenReturn(true);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("npm", null, null, null);
        // Use limit 5 to trigger autoComplete mode (synchronous) instead of allResults (async)
        // to avoid race conditions with TestSearchFunctions
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(search);
        when(request.getQueryParamByKey("projectKey")).thenReturn(null);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        // Act
        testService.execute(request, response);

        // Assert
        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertEquals(3, iModel.results().size());

        assertEquals("npm-local", iModel.results().get(0).repoKey());
        assertEquals("local", iModel.results().get(0).repoType());
        assertEquals("npm", iModel.results().get(0).repoPkgType());

        assertEquals("npm-remote-cache", iModel.results().get(1).repoKey());
        assertEquals("cached", iModel.results().get(1).repoType());
        assertEquals("npm", iModel.results().get(1).repoPkgType());

        assertEquals("npm-federated", iModel.results().get(2).repoKey());
        assertEquals("federated", iModel.results().get(2).repoType());
        assertEquals("npm", iModel.results().get(2).repoPkgType());
    }

    @Test
    void searchWithFiltersShouldIncludeFederatedRepositories() {
        // Arrange - test that filters don't exclude federated repos
        Repository federatedRepo = new Repository("docker-federated", "federated", "docker",
                456L, 0L);
        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctions(List.of(federatedRepo), List.of(), List.of());

        when(securityService.isAdmin()).thenReturn(true);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("docker", null, null, null);
        Filters filters = new Filters(Set.of(Type.REPO), null, null, null, null);
        Search search = new Search(query, 5, null, filters, false);

        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getImodel()).thenReturn(search);
        when(request.getQueryParamByKey("projectKey")).thenReturn(null);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        // Act
        testService.execute(request, response);

        // Assert
        assertEquals(200, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertEquals(1, iModel.results().size());
        assertEquals("docker-federated", iModel.results().getFirst().repoKey());
        assertEquals("federated", iModel.results().getFirst().repoType());
        assertEquals("docker", iModel.results().getFirst().repoPkgType());
    }

    private static class FailedTestSearchFunctions implements SearchFunctionProvider {
        @Override
        public List<Repository> queryRepositories(Query query, Filters filters, int limit, State state,
                boolean isExactMatch,
                JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            throw new SQLException("reason", "42601");
        }

        @Override
        public List<Node> queryFolders(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            throw new SQLException("reason", "42601");
        }

        @Override
        public List<Node> queryFiles(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            throw new SQLException("reason", "42601");
        }
    }

    private static class ThrowingSearchFunctionProvider implements SearchFunctionProvider {
        @Override
        public List<Repository> queryRepositories(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            throw new RuntimeException("Search execution failed");
        }

        @Override
        public List<Node> queryFolders(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            throw new RuntimeException("Search execution failed");
        }

        @Override
        public List<Node> queryFiles(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            throw new RuntimeException("Search execution failed");
        }
    }

    private static class TestSearchFunctions implements SearchFunctionProvider {

        private final List<Repository> repositories;
        private final List<Node> paths;
        private final List<Node> files;
        private final boolean clearAfterGet;

        @Getter
        private String repositorySelect;
        @Getter
        private String pathSelect;
        @Getter
        private String fileSelect;

        @Getter
        private PreparedQuery repositoryQuery;
        @Getter
        private PreparedQuery pathQuery;
        @Getter
        private PreparedQuery fileQuery;

        public TestSearchFunctions(List<Repository> repositories, List<Node> paths, List<Node> files) {
            this(repositories, paths, files, true);
        }

        public TestSearchFunctions(List<Repository> repositories, List<Node> paths, List<Node> files,
                boolean clearAfterGet) {
            this.repositories = new ArrayList<>(repositories);
            this.paths = new ArrayList<>(paths);
            this.files = new ArrayList<>(files);
            this.clearAfterGet = clearAfterGet;
        }

        @Override
        public List<Repository> queryRepositories(Query query, Filters filters, int limit, State state,
                boolean isExactMatch,
                JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            return SyntaxSearchQueryUtils.queryRepositories(query, filters, limit, state, isExactMatch, jdbcHelper,
                    deadline, this::getRepositoriesPrepared);
        }

        private List<Repository> getRepositoriesPrepared(JdbcHelper jdbcHelper, PreparedQuery query,
                Deadline deadline) {
            this.repositoryQuery = query;
            return getAndClear(repositories);
        }

        @Override
        public List<Node> queryFolders(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            return SyntaxSearchQueryUtils.queryPaths(query, filters, limit, state, isExactMatch, jdbcHelper,
                    deadline, this::getPathsPrepared);
        }

        private List<Node> getPathsPrepared(JdbcHelper jdbcHelper, PreparedQuery query, Deadline deadline) {
            this.pathQuery = query;
            return getAndClear(paths);
        }

        @Override
        public List<Node> queryFiles(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            return SyntaxSearchQueryUtils.queryFiles(query, filters, limit, state, isExactMatch, jdbcHelper,
                    deadline, this::getFilesPrepared);
        }

        private List<Node> getFilesPrepared(JdbcHelper jdbcHelper, PreparedQuery query, Deadline deadline) {
            this.fileQuery = query;
            return getAndClear(files);
        }

        private <T> List<T> getAndClear(List<T> list) {
            List<T> result = new ArrayList<>(list);
            if (clearAfterGet) {
                list.clear();
            }
            return result;
        }

    }

    @ParameterizedTest
    @MethodSource("isNotHiddenTestCases")
    void isNotHidden_ShouldReturnExpected_WhenGivenItem(
            String description, boolean isRepository, String repoKey, String path, String name, long size,
            String packageType, boolean isAdmin, RepoType repoType, boolean canManage, boolean expected) {
        lenient().when(securityService.isAdmin()).thenReturn(isAdmin);

        if (repoType != null) {
            RepoConfig repoConfig = mock(RepoConfig.class);
            when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
            when(repoConfig.getPackageType()).thenReturn(repoType);
        }

        lenient().when(authorizationService.canManage(any(RepoPath.class))).thenReturn(canManage);

        boolean result;
        if (isRepository) {
            Repository repository = new Repository(repoKey, "local", packageType, 123L, 0L);
            result = service.isNotHidden(repository);
        } else {
            Node node = new Node(1L, repoKey, path, name, size, 123L, 0L);
            result = service.isNotHidden(node);
        }

        assertEquals(expected, result, description);
    }

    private static Stream<Arguments> isNotHiddenTestCases() {
        return Stream.of(
                Arguments.of("Regular file as admin", false, "maven-local", "com/example", "artifact.jar", 1024L, null,
                        true, null, true, true),
                Arguments.of("Regular file as non-admin", false, "maven-local", "com/example", "artifact.jar", 1024L,
                        null, false, null, true, true),
                Arguments.of(".jfrog folder with MANAGE permission", false, "maven-local", ".", ".jfrog", 0L, null,
                        false, null, true, false),
                Arguments.of("File inside .jfrog with MANAGE permission", false, "docker-local", ".jfrog",
                        "repository.catalog", 1024L, null, false, null, true, false),
                Arguments.of(".jfrog folder without MANAGE permission", false, "maven-local", ".", ".jfrog", 0L, null,
                        false, null, false, false),
                Arguments.of("File inside .jfrog without MANAGE permission", false, "docker-local", ".jfrog",
                        "repository.catalog", 1024L, null, false, null, false, false),
                Arguments.of("Deep path inside .jfrog without MANAGE permission", false, "npm-local", ".jfrog/metadata",
                        "file.json", 512L, null, false, null, false, false),
                Arguments.of("File ending with .jfrog as non-admin", false, "maven-local", ".", "something.jfrog",
                        1024L, null, false, null, false, true),
                Arguments.of("Cargo .git folder", false, "cargo-local", ".", ".git", 0L, null, false, RepoType.Cargo,
                        true, false),
                Arguments.of("Cargo file inside .git folder", false, "cargo-local", ".git", "config", 256L, null, false,
                        RepoType.Cargo, true, false),
                Arguments.of("Non-Cargo .git folder", false, "maven-local", ".", ".git", 0L, null, true, RepoType.Maven,
                        true, true),
                Arguments.of("Cargo regular file", false, "cargo-local", "crate/v1", "package.crate", 2048L, null, true,
                        RepoType.Cargo, true, true),
                Arguments.of("Regular node in maven-local", false, "maven-local", ".", "test.txt", 100L, null, true,
                        null, true, true),
                Arguments.of("Node in jfrog-logs", false, "jfrog-logs", ".", "test.txt", 100L, null, true, null, true,
                        true),
                Arguments.of("Node in jfrog-billing-logs (visible)", false, "jfrog-billing-logs", ".", "test.txt", 100L,
                        null,
                        true, null, true, true),
                Arguments.of("Node in jfrog-usage-logs (hidden - visible=false)", false, "jfrog-usage-logs", ".",
                        "test.txt", 100L, null, true,
                        null, true, false),
                Arguments.of("Node in jfrog-full-sync-info (hidden - visible=false)", false, "jfrog-full-sync-info",
                        ".", "test.txt", 100L, null,
                        true, null, true, false),
                Arguments.of("Node in artifactory-build-info", false, "artifactory-build-info", ".", "test.txt", 100L,
                        null, true, null, true, true),
                Arguments.of("Regular repo", true, "maven-local", null, null, 0L, "maven", false, null, true, true),
                Arguments.of("Build info repo", true, "artifactory-build-info", null, null, 0L, "buildinfo", false,
                        null, true, true),
                Arguments.of("Pipe info repo", true, "artifactory-pipe-info", null, null, 0L, "pipeinfo", false, null,
                        true, true),
                Arguments.of("Release bundles repo", true, "release-bundles", null, null, 0L, "releasebundles", false,
                        null, true, true),
                Arguments.of("jfrog-logs repo", true, "jfrog-logs", null, null, 0L, "generic", false, null, true, true),
                Arguments.of("jfrog-billing-logs repo (visible)", true, "jfrog-billing-logs", null, null, 0L, "generic",
                        false,
                        null, true, true),
                Arguments.of("jfrog-usage-logs repo (hidden - visible=false)", true, "jfrog-usage-logs", null, null, 0L,
                        "generic", false, null,
                        true, false),
                Arguments.of("jfrog-full-sync-info repo (hidden - visible=false)", true, "jfrog-full-sync-info", null,
                        null, 0L, "generic",
                        false, null, true, false),
                Arguments.of("_intransit repo (hidden)", true, "_intransit", null, null, 0L, "generic", false, null,
                        true, false)
        );
    }

    @ParameterizedTest
    @MethodSource("checksumFilesTestCases")
    void isNotHidden_shouldFilterChecksumFiles(String fileName, boolean shouldBeVisible) {
        Node node = new Node(1L, "test-repo", "some/path", fileName, 1024L, 1234567890L, 0L);

        boolean result = service.isNotHidden(node);

        assertEquals(shouldBeVisible, result, "Checksum file " + fileName + " should be " +
                (shouldBeVisible ? "visible" : "hidden"));
    }

    private static Stream<Arguments> checksumFilesTestCases() {
        return Stream.of(
                // Checksum files - should be hidden
                Arguments.of("artifact.jar.sha1", false),
                Arguments.of("artifact.jar.md5", false),
                Arguments.of("artifact.jar.sha256", false),
                Arguments.of("file.SHA1", false),
                Arguments.of("file.MD5", false),
                Arguments.of("file.SHA256", false),
                // Regular files - should be visible
                Arguments.of("artifact.jar", true),
                Arguments.of("file.txt", true),
                Arguments.of("sha1-report.txt", true),
                Arguments.of("md5sum.log", true)
        );
    }

    @ParameterizedTest
    @MethodSource("cargoGitFolderTestCases")
    void isNotHidden_shouldFilterCargoGitFolders(String repoKey, String path, String name, boolean shouldBeVisible) {
        Node node = new Node(1L, repoKey, path, name, 1024L, 1234567890L, 0L);
        RepoConfig cargoRepo = mock(RepoConfig.class);
        RepoConfig genericRepo = mock(RepoConfig.class);

        lenient().when(repositoryService.getRepoConfigByKey("cargo-repo")).thenReturn(cargoRepo);
        lenient().when(cargoRepo.getPackageType()).thenReturn(RepoType.Cargo);
        lenient().when(repositoryService.getRepoConfigByKey("generic-repo")).thenReturn(genericRepo);
        lenient().when(genericRepo.getPackageType()).thenReturn(RepoType.Generic);

        boolean result = service.isNotHidden(node);

        assertEquals(shouldBeVisible, result, "Node in " + repoKey + " at " + path + "/" + name +
                " should be " + (shouldBeVisible ? "visible" : "hidden"));
    }

    private static Stream<Arguments> cargoGitFolderTestCases() {
        return Stream.of(
                // Cargo repo with .git at root level - should be hidden
                Arguments.of("cargo-repo", ".", ".git", false),
                Arguments.of("cargo-repo", ".", ".gitignore", false),
                // Cargo repo with files inside .git folder - should be hidden
                Arguments.of("cargo-repo", ".git", "config", false),
                Arguments.of("cargo-repo", ".git/objects", "abc123", false),
                // Cargo repo with regular files - should be visible
                Arguments.of("cargo-repo", ".", "src", true),
                Arguments.of("cargo-repo", "some/path", "file.txt", true),
                Arguments.of("cargo-repo", "crates", "my-crate", true),
                // Non-Cargo repo with .git folders - should be visible
                Arguments.of("generic-repo", ".", ".git", true),
                Arguments.of("generic-repo", ".", ".gitignore", true),
                Arguments.of("generic-repo", ".git", "config", true)
        );
    }

    @ParameterizedTest
    @MethodSource("internalMetadataTestCases")
    void isNotHidden_shouldExcludeInternalMetadataFromSearch(String path, String name, boolean isAdmin,
            boolean canManage, boolean shouldBeVisible) {
        Node node = new Node(1L, "test-repo", path, name, 1024L, 1234567890L, 0L);

        lenient().when(authorizationService.canManage(any(RepoPath.class))).thenReturn(canManage);

        boolean result = service.isNotHidden(node);

        assertEquals(shouldBeVisible, result, "Internal metadata at " + path + "/" + name +
                " should be " + (shouldBeVisible ? "included" : "excluded from search"));
    }

    private static Stream<Arguments> internalMetadataTestCases() {
        return Stream.of(
                // .jfrog folder at root - excluded from search for all users
                Arguments.of(".", ".jfrog", true, true, false),
                Arguments.of(".", ".jfrog", false, false, false),
                // Files inside .jfrog folder - excluded from search for all users
                Arguments.of(".jfrog", "repository.catalog", true, true, false),
                Arguments.of(".jfrog/metadata", "file.json", true, true, false),
                Arguments.of(".jfrog", "repository.catalog", false, false, false),
                Arguments.of(".jfrog/metadata", "file.json", false, false, false),
                // Regular files - visible for everyone
                Arguments.of(".", "file.txt", false, false, true),
                Arguments.of("some/path", "file.txt", false, false, true)
        );
    }

    @ParameterizedTest
    @MethodSource("machineLearningTmpFolderTestCases")
    void isNotHidden_shouldFilterMachineLearningTmpFoldersForNonAdmins(String repoKey, String path, String name,
            boolean canManage, boolean shouldBeVisible) {
        Node node = new Node(1L, repoKey, path, name, 1024L, 1234567890L, 0L);
        RepoConfig mlRepo = mock(RepoConfig.class);
        RepoConfig genericRepo = mock(RepoConfig.class);

        lenient().when(repositoryService.getRepoConfigByKey("ml-repo")).thenReturn(mlRepo);
        lenient().when(mlRepo.getPackageType()).thenReturn(RepoType.MachineLearning);
        lenient().when(repositoryService.getRepoConfigByKey("generic-repo")).thenReturn(genericRepo);
        lenient().when(genericRepo.getPackageType()).thenReturn(RepoType.Generic);
        lenient().when(authorizationService.canManage(any(RepoPath.class))).thenReturn(canManage);

        boolean result = service.isNotHidden(node);

        String userType = canManage ? "admin" : "non-admin";
        assertEquals(shouldBeVisible, result, "ML tmp folder at " + path + "/" + name +
                " should be " + (shouldBeVisible ? "visible" : "hidden") + " for " + userType);
    }

    private static Stream<Arguments> machineLearningTmpFolderTestCases() {
        return Stream.of(
                // ML repo tmp folders (3rd level) for non-admin - should be hidden
                Arguments.of("ml-repo", "dir1/dir2", "tmp", false, false),
                Arguments.of("ml-repo", "dir1/dir2/tmp", "file.txt", false, false),
                // ML repo tmp folders (4th level) for non-admin - should be hidden
                Arguments.of("ml-repo", "dir1/dir2/dir3", "tmp", false, false),
                Arguments.of("ml-repo", "dir1/dir2/dir3/tmp", "file.txt", false, false),
                // ML repo tmp folders for admin - should be visible
                Arguments.of("ml-repo", "dir1/dir2", "tmp", true, true),
                Arguments.of("ml-repo", "dir1/dir2/tmp", "file.txt", true, true),
                // ML repo non-tmp folders - should be visible
                Arguments.of("ml-repo", "dir1/dir2", "models", false, true),
                Arguments.of("ml-repo", "dir1/dir2", "data", false, true),
                // ML repo tmp at wrong level (1st/2nd level) - should be visible
                Arguments.of("ml-repo", ".", "tmp", false, true),
                Arguments.of("ml-repo", "dir1", "tmp", false, true),
                // Non-ML repo tmp folders - should be visible
                Arguments.of("generic-repo", "dir1/dir2", "tmp", false, true)
        );
    }

    @ParameterizedTest
    @MethodSource("inTransitAndRbv2TestCases")
    void isNotHidden_shouldFilterNodesFromHiddenRepos(String repoKey, String path, String name,
            boolean shouldBeVisible) {
        Node node = new Node(1L, repoKey, path, name, 1024L, 1234567890L, 0L);

        boolean result = service.isNotHidden(node);

        assertEquals(shouldBeVisible, result, "Node from " + repoKey + " should be " +
                (shouldBeVisible ? "visible" : "hidden"));
    }

    private static Stream<Arguments> inTransitAndRbv2TestCases() {
        return Stream.of(
                // In-transit repo - should be hidden
                Arguments.of("_intransit", "some/path", "file.txt", false),
                Arguments.of("_intransit", ".", "artifact.jar", false),
                // RBv2 distribution repos - should be hidden (must end with "release-bundles-v2-jfds")
                Arguments.of("release-bundles-v2-jfds", "path", "bundle.zip", false),
                Arguments.of("project-release-bundles-v2-jfds", "path", "bundle.zip", false),
                // RBv2 base repos - should be visible (these are the actual user-facing repos)
                Arguments.of("release-bundles-v2", "path", "bundle.json", true),
                Arguments.of("project-release-bundles-v2", "path", "bundle.json", true),
                // Regular repos - should be visible
                Arguments.of("libs-release-local", "path", "artifact.jar", true),
                Arguments.of("docker-local", "path", "manifest.json", true)
        );
    }

    @ParameterizedTest
    @MethodSource("evidencePathNavigationDisabledTestCases")
    void isNotHidden_shouldHideEvidencePaths_whenNavigationDisabled(String repoKey, String path, String name,
            boolean shouldBeVisible) {
        Node node = new Node(1L, repoKey, path, name, 1024L, 1234567890L, 0L);

        try (MockedStatic<ArtifactoryHome> mockedHome = mockStatic(ArtifactoryHome.class, Answers.CALLS_REAL_METHODS)) {
            mockedHome.when(() -> ArtifactoryHome.getBooleanFromSysConfig(EVIDENCE_NAVIGATION_ENABLED, false))
                    .thenReturn(false);

            boolean result = service.isNotHidden(node);

            assertEquals(shouldBeVisible, result,
                    "Node at " + path + "/" + name + " should be " + (shouldBeVisible ? "visible" : "hidden") +
                            " when evidence navigation is disabled");
        }
    }

    private static Stream<Arguments> evidencePathNavigationDisabledTestCases() {
        return Stream.of(
                // .evidence folder at root - hidden when navigation is disabled (default behavior)
                Arguments.of("maven-local", ".", ".evidence", false),
                // File directly inside .evidence - hidden when navigation is disabled
                Arguments.of("maven-local", ".evidence", "signature.json", false),
                // Nested file inside .evidence subfolder - hidden when navigation is disabled
                Arguments.of("docker-local", ".evidence/subfolder", "digest.json", false),
                // Paths that look similar but are NOT evidence - always visible
                Arguments.of("maven-local", ".", ".evidences", true),
                Arguments.of("maven-local", "some/path", "file.evidence", true),
                // Regular file - always visible
                Arguments.of("maven-local", "com/example", "artifact.jar", true)
        );
    }

    @ParameterizedTest
    @MethodSource("evidencePathNavigationEnabledTestCases")
    void isNotHidden_shouldShowEvidencePaths_whenNavigationEnabled(String repoKey, String path, String name) {
        Node node = new Node(1L, repoKey, path, name, 1024L, 1234567890L, 0L);

        try (MockedStatic<ArtifactoryHome> mockedHome = mockStatic(ArtifactoryHome.class, Answers.CALLS_REAL_METHODS)) {
            mockedHome.when(() -> ArtifactoryHome.getBooleanFromSysConfig(EVIDENCE_NAVIGATION_ENABLED, false))
                    .thenReturn(true);

            assertTrue(service.isNotHidden(node),
                    "Evidence path at " + path + "/" + name + " should be visible when navigation is enabled");
        }
    }

    private static Stream<Arguments> evidencePathNavigationEnabledTestCases() {
        return Stream.of(
                Arguments.of("maven-local", ".", ".evidence"),
                Arguments.of("maven-local", ".evidence", "signature.json"),
                Arguments.of("docker-local", ".evidence/subfolder", "digest.json")
        );
    }

    @Test
    void shouldReturn206WhenReposQueryTimesOut() {
        Repository repo1 = new Repository("repo1", "local", "generic", 48L, 0L);
        Repository repo2 = new Repository("repo2", "local", "generic", 49L, 0L);
        Node file = new Node(43L, "repo", "path", "file", 0, 48L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctionsWithTimeout(List.of(repo1, repo2), List.of(), List.of(file), true, false, false);

        when(securityService.isAdmin()).thenReturn(true);
        when(repositoryService.getRepoConfigByKey("repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("a", null, null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mockArtifactoryRestRequest(search);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);

        assertEquals(206, response.getResponseCode(), "Should return 206 when repos query times out");
        Response iModel = (Response) response.getIModel();
        assertEquals(3, iModel.results().size(), "Should return partial results");
        assertEquals(Type.REPO, iModel.results().get(0).itemType());
        assertEquals(Type.REPO, iModel.results().get(1).itemType());
        assertEquals(Type.FILE, iModel.results().get(2).itemType());
        assertEquals(true, iModel.isPartialResponse());
    }

    @Test
    void shouldReturn206WhenPathsQueryTimesOut() {
        Repository repo = new Repository("repo", "local", "generic", 48L, 0L);
        Node path1 = new Node(42L, "repo", "path1", "folder1", 0, 48L, 0L);
        Node path2 = new Node(43L, "repo", "path2", "folder2", 0, 49L, 0L);
        Node file = new Node(44L, "repo", "path", "file", 1024, 50L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctionsWithTimeout(List.of(repo), List.of(path1, path2), List.of(file), false, true,
                        false);

        when(securityService.isAdmin()).thenReturn(true);
        when(repositoryService.getRepoConfigByKey("repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("a", null, null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mockArtifactoryRestRequest(search);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);

        assertEquals(206, response.getResponseCode(), "Should return 206 when paths query times out");
        Response iModel = (Response) response.getIModel();
        assertEquals(4, iModel.results().size(), "Should return partial results");
        assertEquals(true, iModel.isPartialResponse());
    }

    @Test
    void shouldReturn206WhenFilesQueryTimesOut() {
        Repository repo = new Repository("repo", "local", "generic", 48L, 0L);
        Node path = new Node(42L, "repo", "path", "folder", 0, 48L, 0L);
        Node file1 = new Node(43L, "repo", "path", "file1.txt", 1024, 49L, 0L);
        Node file2 = new Node(44L, "repo", "path", "file2.txt", 2048, 50L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctionsWithTimeout(List.of(repo), List.of(path), List.of(file1, file2), false, false,
                        true);

        when(securityService.isAdmin()).thenReturn(true);
        when(repositoryService.getRepoConfigByKey("repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("a", null, null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mockArtifactoryRestRequest(search);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);

        assertEquals(206, response.getResponseCode(), "Should return 206 when files query times out");
        Response iModel = (Response) response.getIModel();
        assertEquals(4, iModel.results().size(), "Should return partial results");
        assertEquals(true, iModel.isPartialResponse());
    }

    @Test
    void shouldReturn206WhenMultipleQueriesTimeout() {
        Repository repo = new Repository("repo", "local", "generic", 48L, 0L);
        Node path = new Node(42L, "repo", "path", "folder", 0, 48L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctionsWithTimeout(List.of(repo), List.of(path), List.of(), true, true, true);

        when(securityService.isAdmin()).thenReturn(true);
        when(repositoryService.getRepoConfigByKey("repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("a", null, null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mockArtifactoryRestRequest(search);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);

        assertEquals(206, response.getResponseCode(), "Should return 206 when multiple queries timeout");
        Response iModel = (Response) response.getIModel();
        assertEquals(2, iModel.results().size(), "Should return whatever partial results were collected");
        assertEquals(true, iModel.isPartialResponse());
    }

    @Test
    void shouldReturn200WhenNoTimeout() {
        Repository repo = new Repository("repo", "local", "generic", 48L, 0L);
        Node path = new Node(42L, "repo", "path", "folder", 0, 48L, 0L);
        Node file = new Node(43L, "repo", "path", "file", 1024, 48L, 0L);

        SearchFunctionProvider testSearchFunctionProvider =
                new TestSearchFunctionsWithTimeout(List.of(repo), List.of(path), List.of(file), false, false, false);

        when(securityService.isAdmin()).thenReturn(true);
        when(repositoryService.getRepoConfigByKey("repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        SyntaxSearchServiceForTest testService = new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, testSearchFunctionProvider, projectsService);

        Query query = new Query("a", null, null, null);
        Search search = new Search(query, 5, null, null, false);

        ArtifactoryRestRequest request = mockArtifactoryRestRequest(search);

        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(request, response);

        assertEquals(200, response.getResponseCode(), "Should return 200 when no timeout occurs");
        Response iModel = (Response) response.getIModel();
        assertEquals(3, iModel.results().size());
    }

    @Test
    void executeSearchStage_shouldExitDrainLoopAndMarkTimeout_whenDeadlineExpiresBetweenIterations() {
        when(securityService.isAdmin()).thenReturn(true);
        lenient().when(repositoryService.getRepoConfigByKey(anyString())).thenReturn(repoConfig);
        lenient().when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        lenient().when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        List<Node> folders = buildNodeSequence(9);
        ClockAdvancingSearchFunctions provider =
                new ClockAdvancingSearchFunctions(List.of(), folders, List.of());
        MutableClock clock = new MutableClock();
        provider.advanceClockOnCall(clock, FOLDER, Duration.ofSeconds(60));

        SyntaxSearchServiceForTest testService = serviceWithClock(provider, clock);

        Search search = new Search(new Query("a", "repo-fixed", null, null), 10, null, null, false);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(mockArtifactoryRestRequest(search), response);

        assertEquals(206, response.getResponseCode());
        Response iModel = (Response) response.getIModel();
        assertTrue(iModel.isPartialResponse());
        assertTrue(provider.folderCallCount() <= 2);
    }

    @Test
    void executeSearchStages_shouldSkipLaterStagesAndMarkTimeout_whenDeadlineExpiresBetweenStages() {
        when(securityService.isAdmin()).thenReturn(true);

        ClockAdvancingSearchFunctions provider =
                new ClockAdvancingSearchFunctions(List.of(), List.of(), List.of());
        MutableClock clock = new MutableClock();
        provider.advanceClockOnCall(clock, REPO, Duration.ofSeconds(60));

        SyntaxSearchServiceForTest testService = serviceWithClock(provider, clock);

        Search search = new Search(new Query("a", null, null, null), 10, null, null, false);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(mockArtifactoryRestRequest(search), response);

        assertEquals(206, response.getResponseCode());
        assertEquals(0, provider.folderCallCount());
        assertEquals(0, provider.fileCallCount());
    }

    @Test
    void executeSearchStageForVirtualRepo_shouldStopFanout_whenDeadlineExpiresBetweenMembers() {
        when(securityService.isAdmin()).thenReturn(true);

        @SuppressWarnings("rawtypes")
        List<LocalRepo> nestedLocals = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LocalRepo<?, ?> nested = mock(LocalRepo.class);
            String key = "nested-" + i;
            lenient().when(nested.getKey()).thenReturn(key);
            lenient().when(nested.isLocal()).thenReturn(true);
            lenient().when(nested.isReal()).thenReturn(true);
            nestedLocals.add(nested);
        }
        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        when(virtualRepo.getKey()).thenReturn("virtual-repo");
        when(virtualRepo.getLocalRepositories()).thenReturn(nestedLocals);
        when(virtualRepo.getRemoteRepositories()).thenReturn(List.of());
        when(virtualRepo.getVirtualRepositories()).thenReturn(List.of());
        when(virtualRepo.isPathPatternValid(any(RepoPath.class), anyString())).thenReturn(true);
        when(repositoryService.virtualRepositoryByKey("virtual-repo")).thenReturn(virtualRepo);

        ClockAdvancingSearchFunctions provider = new ClockAdvancingSearchFunctions(List.of());
        MutableClock clock = new MutableClock();
        provider.advanceClockOnCall(clock, FOLDER, Duration.ofSeconds(60));

        SyntaxSearchServiceForTest testService = serviceWithClock(provider, clock);

        Search search = new Search(new Query("a", "virtual-repo", null, null), 10, null, null, false);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        testService.execute(mockArtifactoryRestRequest(search), response);

        assertEquals(206, response.getResponseCode());
        assertTrue(provider.folderCallCount() <= 2);
        assertEquals(0, provider.fileCallCount());
    }

    private SyntaxSearchServiceForTest serviceWithClock(SearchFunctionProvider provider, MutableClock clock) {
        JdbcHelper mockJdbcHelper = mock(JdbcHelper.class);
        ItemActionsPopulator mockItemActionsPopulator = mock(ItemActionsPopulator.class);
        return new SyntaxSearchServiceForTest(mockJdbcHelper, repositoryService, securityService,
                authorizationService, provider, projectsService, mockItemActionsPopulator) {
            @Override
            Deadline createDeadline(Query searchQuery) {
                return Deadline.startingNow(clock,
                        SyntaxSearchQueryUtils.getRequestBudgetSeconds(searchQuery));
            }
        };
    }

    private static List<Node> buildNodeSequence(int count) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            nodes.add(new Node(i + 1L, "some-repo", "path-" + i, "name-" + i, 0, 1_000_000L - i, 0L));
        }
        return nodes;
    }

    private static final class ClockAdvancingSearchFunctions implements SearchFunctionProvider {

        private final List<Repository> repositories;
        private final List<Node> folders;
        private final List<Node> files;

        private final AtomicInteger repoCalls = new AtomicInteger();
        private final AtomicInteger folderCalls = new AtomicInteger();
        private final AtomicInteger fileCalls = new AtomicInteger();

        private MutableClock clockToAdvance;
        private Type stageToAdvanceOn;
        private Duration advanceBy;

        ClockAdvancingSearchFunctions(List<Repository> repositories) {
            this(repositories, List.of(), List.of());
        }

        ClockAdvancingSearchFunctions(List<Repository> repositories, List<Node> folders, List<Node> files) {
            this.repositories = repositories;
            this.folders = folders;
            this.files = files;
        }

        void advanceClockOnCall(MutableClock clock, Type stage, Duration delta) {
            this.clockToAdvance = clock;
            this.stageToAdvanceOn = stage;
            this.advanceBy = delta;
        }

        int repoCallCount() {
            return repoCalls.get();
        }

        int folderCallCount() {
            return folderCalls.get();
        }

        int fileCallCount() {
            return fileCalls.get();
        }

        @Override
        public List<Repository> queryRepositories(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) {
            repoCalls.incrementAndGet();
            advanceIfMatchingStage(REPO);
            return new ArrayList<>(repositories);
        }

        @Override
        public List<Node> queryFolders(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) {
            folderCalls.incrementAndGet();
            advanceIfMatchingStage(FOLDER);
            return new ArrayList<>(folders);
        }

        @Override
        public List<Node> queryFiles(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) {
            fileCalls.incrementAndGet();
            advanceIfMatchingStage(FILE);
            return new ArrayList<>(files);
        }

        private synchronized void advanceIfMatchingStage(Type stage) {
            if (clockToAdvance != null && stage == stageToAdvanceOn) {
                clockToAdvance.advance(advanceBy);
            }
        }
    }

    private static final class MutableClock extends Clock {

        private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");

        private volatile Instant now = START;

        synchronized void advance(Duration delta) {
            now = now.plus(delta);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static @NonNull ArtifactoryRestRequest mockArtifactoryRestRequest(Search search) {
        return mockArtifactoryRestRequest(search, null);
    }

    private static @NonNull ArtifactoryRestRequest mockArtifactoryRestRequest(Search search, String projectKey) {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(request.getImodel()).thenReturn(search);
        when(request.getServletRequest()).thenReturn(servletRequest);
        when(request.getQueryParamByKey("projectKey")).thenReturn(projectKey);

        return request;
    }

    private SyntaxSearchServiceForTest createTestService(SearchFunctionProvider provider) {
        return new SyntaxSearchServiceForTest(null, repositoryService,
                securityService, authorizationService, provider, projectsService);
    }

    private Response executeAndGetResponse(SyntaxSearchServiceForTest testService, ArtifactoryRestRequest request) {
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        testService.execute(request, response);
        return (Response) response.getIModel();
    }

    private static class TestSearchFunctionsWithTimeout implements SearchFunctionProvider {

        private final List<Repository> repositories;
        private final List<Node> paths;
        private final List<Node> files;
        private final boolean timeoutRepos;
        private final boolean timeoutPaths;
        private final boolean timeoutFiles;

        public TestSearchFunctionsWithTimeout(List<Repository> repositories, List<Node> paths, List<Node> files,
                boolean timeoutRepos, boolean timeoutPaths, boolean timeoutFiles) {
            this.repositories = new ArrayList<>(repositories);
            this.paths = new ArrayList<>(paths);
            this.files = new ArrayList<>(files);
            this.timeoutRepos = timeoutRepos;
            this.timeoutPaths = timeoutPaths;
            this.timeoutFiles = timeoutFiles;
        }

        @Override
        public List<Repository> queryRepositories(Query query, Filters filters, int limit, State state,
                boolean isExactMatch,
                JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            if (timeoutRepos) {
                SyntaxSearchTimeoutTracker.markTimeout();
            }
            return new ArrayList<>(repositories);
        }

        @Override
        public List<Node> queryFolders(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            if (timeoutPaths) {
                SyntaxSearchTimeoutTracker.markTimeout();
            }
            return new ArrayList<>(paths);
        }

        @Override
        public List<Node> queryFiles(Query query, Filters filters, int limit, State state,
                boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
            if (timeoutFiles) {
                SyntaxSearchTimeoutTracker.markTimeout();
            }
            return new ArrayList<>(files);
        }
    }

    private static class SyntaxSearchServiceForTest extends SyntaxSearchService {

        public SyntaxSearchServiceForTest(JdbcHelper jdbcHelper,
                InternalRepositoryService internalRepositoryService,
                SecurityService securityService, AuthorizationService authorizationService,
                SearchFunctionProvider searchFunctionProvider, ProjectsService projectsService) {
            this(jdbcHelper, internalRepositoryService,
                    securityService, authorizationService, searchFunctionProvider,
                    projectsService, null);
        }

        public SyntaxSearchServiceForTest(JdbcHelper jdbcHelper,
                InternalRepositoryService internalRepositoryService,
                SecurityService securityService, AuthorizationService authorizationService,
                SearchFunctionProvider searchFunctionProvider, ProjectsService projectsService,
                ItemActionsPopulator itemActionsPopulator) {
            super(jdbcHelper, internalRepositoryService, securityService, authorizationService,
                    searchFunctionProvider, projectsService, itemActionsPopulator);
        }

        @Override
        String getContextUrl(HttpServletRequest request) {
            return "contextUrl";
        }

        @Override
        SyntaxSearchService getTransactionalMe() {
            try {
                super.getTransactionalMe();
            } catch (Exception ignored) {}
            return this;
        }
    }

    @Test
    void getRepositoriesShouldMarkTimeoutWhenTimeoutExceptionOccurs() throws SQLException {
        JdbcHelper mockJdbcHelper = mock(JdbcHelper.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(mockJdbcHelper.executeSelect(any(), anyString(), any(Object[].class))).thenReturn(resultSet);
        when(resultSet.next())
                .thenReturn(true)
                .thenReturn(true)
                .thenThrow(new SQLException("canceling statement due to user request", "57014"));

        when(resultSet.getString("repository_key")).thenReturn("repo1", "repo2");
        when(resultSet.getString("type")).thenReturn("local", "local");
        when(resultSet.getString("package_type")).thenReturn("generic", "generic");
        when(resultSet.getLong("modified")).thenReturn(1000L, 2000L);
        when(resultSet.getLong("created")).thenReturn(1000L, 2000L);

        SyntaxSearchTimeoutTracker.reset();

        List<Repository> result = SyntaxSearchQueryUtils.getRepositories(mockJdbcHelper, new PreparedQuery(),
                Deadline.startingNow(Integer.MAX_VALUE));

        assertEquals(2, result.size());
        assertTrue(SyntaxSearchTimeoutTracker.hasTimeout());
    }

    @Test
    void getRepositoriesShouldPassDeadlineRemainingBudgetAsJdbcQueryTimeout() throws SQLException {
        JdbcHelper mockJdbcHelper = mock(JdbcHelper.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(mockJdbcHelper.executeSelect(any(QueryControls.class), anyString(), any(Object[].class)))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Deadline deadline = Deadline.startingNow(7);

        SyntaxSearchQueryUtils.getRepositories(mockJdbcHelper, new PreparedQuery(), deadline);

        ArgumentCaptor<QueryControls> controlsCaptor = ArgumentCaptor.forClass(QueryControls.class);
        verify(mockJdbcHelper).executeSelect(controlsCaptor.capture(), anyString(), any(Object[].class));
        int passedTimeout = controlsCaptor.getValue().getQueryTimeout();
        assertTrue(passedTimeout >= 1 && passedTimeout <= 7);
    }

    @Test
    void getRepositoriesShouldClampJdbcQueryTimeoutToOne_whenDeadlineExpired() throws SQLException {
        JdbcHelper mockJdbcHelper = mock(JdbcHelper.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(mockJdbcHelper.executeSelect(any(QueryControls.class), anyString(), any(Object[].class)))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Deadline deadline = Deadline.startingNow(0);

        SyntaxSearchQueryUtils.getRepositories(mockJdbcHelper, new PreparedQuery(), deadline);

        ArgumentCaptor<QueryControls> controlsCaptor = ArgumentCaptor.forClass(QueryControls.class);
        verify(mockJdbcHelper).executeSelect(controlsCaptor.capture(), anyString(), any(Object[].class));
        assertEquals(1, controlsCaptor.getValue().getQueryTimeout());
    }

    @Test
    void getRepositoriesShouldRethrowNonTimeoutExceptions() throws SQLException {
        JdbcHelper mockJdbcHelper = mock(JdbcHelper.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(mockJdbcHelper.executeSelect(any(), anyString(), any(Object[].class))).thenReturn(resultSet);
        when(resultSet.next()).thenThrow(new SQLException("Connection lost", "08006"));

        SyntaxSearchTimeoutTracker.reset();

        assertThrows(SQLException.class, () ->
                SyntaxSearchQueryUtils.getRepositories(mockJdbcHelper, new PreparedQuery(),
                        Deadline.startingNow(Integer.MAX_VALUE)));

        assertFalse(SyntaxSearchTimeoutTracker.hasTimeout());
    }

    @Test
    void getNodesShouldMarkTimeoutWhenTimeoutExceptionOccurs() throws SQLException {
        JdbcHelper mockJdbcHelper = mock(JdbcHelper.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(mockJdbcHelper.executeSelect(any(), anyString(), any(Object[].class))).thenReturn(resultSet);
        when(resultSet.next())
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(true)
                .thenThrow(new SQLException("canceling statement due to user request", "57014"));

        when(resultSet.getLong("node_id")).thenReturn(1L, 2L, 3L);
        when(resultSet.getString("repo")).thenReturn("repo1", "repo1", "repo1");
        when(resultSet.getString("node_path")).thenReturn(".", ".", ".");
        when(resultSet.getString("node_name")).thenReturn("file1.txt", "file2.txt", "file3.txt");
        when(resultSet.getLong("bin_length")).thenReturn(100L, 200L, 300L);
        when(resultSet.getLong("modified")).thenReturn(1000L, 2000L, 3000L);
        when(resultSet.getLong("created")).thenReturn(1000L, 2000L, 3000L);

        SyntaxSearchTimeoutTracker.reset();

        List<Node> result = SyntaxSearchQueryUtils.getNodes(mockJdbcHelper, new PreparedQuery(),
                Deadline.startingNow(Integer.MAX_VALUE));

        assertEquals(3, result.size());
        assertTrue(SyntaxSearchTimeoutTracker.hasTimeout());
    }

    @Test
    void getNodesShouldRethrowNonTimeoutExceptions() throws SQLException {
        JdbcHelper mockJdbcHelper = mock(JdbcHelper.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(mockJdbcHelper.executeSelect(any(), anyString(), any(Object[].class))).thenReturn(resultSet);
        when(resultSet.next()).thenThrow(new SQLException("Connection lost", "08006"));

        SyntaxSearchTimeoutTracker.reset();

        assertThrows(SQLException.class, () ->
                SyntaxSearchQueryUtils.getNodes(mockJdbcHelper, new PreparedQuery(),
                        Deadline.startingNow(Integer.MAX_VALUE)));

        assertFalse(SyntaxSearchTimeoutTracker.hasTimeout());
    }

    @Test
    void getRepositoriesShouldReturnEmptyListWhenTimeoutOccursBeforeAnyResults() throws SQLException {
        JdbcHelper mockJdbcHelper = mock(JdbcHelper.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(mockJdbcHelper.executeSelect(any(), anyString(), any(Object[].class))).thenReturn(resultSet);
        when(resultSet.next()).thenThrow(new SQLException("canceling statement due to user request", "57014"));

        SyntaxSearchTimeoutTracker.reset();

        List<Repository> result = SyntaxSearchQueryUtils.getRepositories(mockJdbcHelper, new PreparedQuery(),
                Deadline.startingNow(Integer.MAX_VALUE));

        assertEquals(0, result.size());
        assertTrue(SyntaxSearchTimeoutTracker.hasTimeout());
    }

    @Test
    void getNodesShouldReturnEmptyListWhenTimeoutOccursBeforeAnyResults() throws SQLException {
        JdbcHelper mockJdbcHelper = mock(JdbcHelper.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(mockJdbcHelper.executeSelect(any(), anyString(), any(Object[].class))).thenReturn(resultSet);
        when(resultSet.next()).thenThrow(new SQLException("canceling statement due to user request", "57014"));

        SyntaxSearchTimeoutTracker.reset();

        List<Node> result = SyntaxSearchQueryUtils.getNodes(mockJdbcHelper, new PreparedQuery(),
                Deadline.startingNow(Integer.MAX_VALUE));

        assertEquals(0, result.size());
        assertTrue(SyntaxSearchTimeoutTracker.hasTimeout());
    }

    @ParameterizedTest
    @MethodSource("adjustQueryForNonAdminUsersEarlyReturnTestCases")
    void adjustQueryForNonAdminUsers_shouldReturnUnchangedQuery_whenEarlyReturnConditions(String description,
            boolean isAdmin, String repo) {
        Query originalQuery = new Query("test", repo, null, null);
        when(securityService.isAdmin()).thenReturn(isAdmin);

        Query result = service.adjustQueryForNonAdminUsers(originalQuery, Set.of());

        assertEquals(originalQuery, result);
        verify(securityService).isAdmin();
        verify(repositoryService, never()).getAllRepoConfigs();
    }

    private static Stream<Arguments> adjustQueryForNonAdminUsersEarlyReturnTestCases() {
        return Stream.of(
                Arguments.of("Admin user should return unchanged query", true, null),
                Arguments.of("Non-admin with repo specified should return unchanged query", false, "maven-local")
        );
    }

    @Test
    void adjustQueryForNonAdminUsers_shouldCollectPermittedRepos_whenNonAdminAndNoRepoSpecified() {
        Query originalQuery = new Query("test", null, null, null);
        RepoConfig localRepo = mock(RepoConfig.class);
        RepoConfig federatedRepo = mock(RepoConfig.class);
        RepoConfig noPermissionRepo = mock(RepoConfig.class);
        when(securityService.isAdmin()).thenReturn(false);
        when(repositoryService.getAllRepoConfigs()).thenReturn(List.of(localRepo, federatedRepo, noPermissionRepo));
        when(localRepo.getKey()).thenReturn("maven-local");
        when(localRepo.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(federatedRepo.getKey()).thenReturn("docker-federated");
        when(federatedRepo.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        when(noPermissionRepo.getKey()).thenReturn("npm-local");
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("maven-local"),
                any(Set.class), any())).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("docker-federated"),
                any(Set.class), any())).thenReturn(true);

        Query result = service.adjustQueryForNonAdminUsers(originalQuery, Set.of());

        assertEquals(2, result.permittedRepos().size());
        assertTrue(result.permittedRepos().contains("maven-local"));
        assertTrue(result.permittedRepos().contains("docker-federated"));
        assertFalse(result.permittedRepos().contains("npm-local"));
    }

    @Test
    void adjustQueryForNonAdminUsers_shouldHandleRemoteRepoCachePermissions_whenRemoteRepoHasPermissions() {
        Query originalQuery = new Query("test", null, null, null);
        RepoConfig remoteRepo = mock(RepoConfig.class);
        when(securityService.isAdmin()).thenReturn(false);
        when(repositoryService.getAllRepoConfigs()).thenReturn(List.of(remoteRepo));
        when(remoteRepo.getKey()).thenReturn("maven-remote");
        when(remoteRepo.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("maven-remote"),
                any(Set.class), any())).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("maven-remote-cache"),
                any(Set.class), any())).thenReturn(true);

        Query result = service.adjustQueryForNonAdminUsers(originalQuery, Set.of());

        assertTrue(result.permittedRepos().contains("maven-remote-cache"));
        assertEquals(1, result.permittedRepos().size());
        verify(authorizationService).userHasPermissionsOnRepositoryRoot(eq("maven-remote"),
                any(Set.class), any());
        verify(authorizationService).userHasPermissionsOnRepositoryRoot(eq("maven-remote-cache"),
                any(Set.class), any());
    }

    @Test
    void adjustQueryForNonAdminUsers_shouldReturnEmptyPermittedRepos_whenPermittedReposExceedSyntaxSearchLimit() {
        Query originalQuery = new Query("test", null, null, null);
        when(securityService.isAdmin()).thenReturn(false);
        List<RepoConfig> manyRepos = new ArrayList<>();
        int exceededNumberOfRepos = ConstantValues.syntaxSearchRepositoriesLimit.getInt() + 1;
        for (int i = 1; i <= exceededNumberOfRepos; i++) {
            RepoConfig repo = mock(RepoConfig.class);
            when(repo.getKey()).thenReturn("repo-" + i);
            when(repo.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
            manyRepos.add(repo);
        }
        when(repositoryService.getAllRepoConfigs()).thenReturn(manyRepos);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(anyString(), any(Set.class), any()))
                .thenReturn(true);

        Query result = service.adjustQueryForNonAdminUsers(originalQuery, Set.of());

        assertTrue(result.permittedRepos().isEmpty(),
                "Permitted repos should be empty when count exceeds syntaxSearchRepositoriesLimit");
        verify(repositoryService).getAllRepoConfigs();
    }
}