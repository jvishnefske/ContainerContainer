package org.artifactory.traffic.context;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Tashlitsky
 */
@ExtendWith(MockitoExtension.class)
class TrafficContextThreadLocalTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private SecurityService securityService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepoConfig repoConfig;
    @Mock
    private RepoPath repoPath;

    private MockedStatic<ContextHelper> contextHelperMock;
    private MockedStatic<RepoPathFactory> repoPathFactoryMock;

    @BeforeEach
    void setUp() {
        // Clear any existing traffic context before each test
        TrafficContextThreadLocal.unbind();

        // Setup static mocks
        contextHelperMock = mockStatic(ContextHelper.class);
        repoPathFactoryMock = mockStatic(RepoPathFactory.class);

        // Setup basic ContextHelper behavior
        contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContext);
    }

    @AfterEach
    void tearDown() {
        // Clean up static mocks and thread local
        contextHelperMock.close();
        repoPathFactoryMock.close();
        TrafficContextThreadLocal.unbind();
    }

    @Test
    void testContextLifecycleAndNullBehavior() {
        // Test all null context behavior
        assertNull(TrafficContextThreadLocal.getTrafficContext());
        assertFalse(TrafficContextThreadLocal.isTrafficLogV3Flow());
        assertNull(TrafficContextThreadLocal.getUsername());
        assertNull(TrafficContextThreadLocal.getRepoPath());

        // Test bind creates new context
        TrafficContextThreadLocal.bind();
        assertNotNull(TrafficContextThreadLocal.getTrafficContext());
        assertFalse(TrafficContextThreadLocal.isTrafficLogV3Flow()); // Default false

        // Test bind doesn't override existing context
        TrafficContext firstContext = TrafficContextThreadLocal.getTrafficContext();
        TrafficContextThreadLocal.bind();
        assertSame(firstContext, TrafficContextThreadLocal.getTrafficContext());

        // Test username and repo path with context
        assertNull(TrafficContextThreadLocal.getUsername()); // Default null
        assertNull(TrafficContextThreadLocal.getRepoPath()); // Default null

        // Test setting values
        String username = "testuser";
        firstContext.setUsername(username);
        firstContext.setRepoPath(repoPath);
        firstContext.setTrafficLogV3Flow(true);

        assertEquals(username, TrafficContextThreadLocal.getUsername());
        assertEquals(repoPath, TrafficContextThreadLocal.getRepoPath());
        assertTrue(TrafficContextThreadLocal.isTrafficLogV3Flow());

        // Test unbind clears context
        TrafficContextThreadLocal.unbind();
        assertNull(TrafficContextThreadLocal.getTrafficContext());
        assertFalse(TrafficContextThreadLocal.isTrafficLogV3Flow());
        assertNull(TrafficContextThreadLocal.getUsername());
        assertNull(TrafficContextThreadLocal.getRepoPath());
    }

    @Test
    void testBindWithOriginRepoKey() {
        // Given
        String repoKey = "test-repo";
        String projectKey = "test-project";
        when(artifactoryContext.beanForType(ProjectsService.class)).thenReturn(projectsService);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(projectsService.getProjectKeyOfRepository(repoKey)).thenReturn(projectKey);
        when(repositoryService.repoConfigByKey(repoKey)).thenReturn(repoConfig);

        // When
        TrafficContextThreadLocal.bind(repoKey);

        // Then
        TrafficContext context = TrafficContextThreadLocal.getTrafficContext();
        assertNotNull(context);
        assertEquals(repoKey, context.getOriginRepoKey());
        assertEquals(projectKey, context.getOriginProjectKey());
        assertEquals(repoConfig, context.getOriginRepoConfig());
        // Verify that bind(String) disables the traffic log v3 flow
        assertFalse(context.isTrafficLogV3Flow());
        assertFalse(TrafficContextThreadLocal.isTrafficLogV3Flow());
    }

    @Test
    void testBindWithOriginRepoKeyDisablesTrafficLogV3Flow() {
        // Given - Create context with traffic log v3 flow enabled
        TrafficContextThreadLocal.bind();
        TrafficContext context = TrafficContextThreadLocal.getTrafficContext();
        assertNotNull(context);
        context.setTrafficLogV3Flow(true);
        assertTrue(context.isTrafficLogV3Flow());

        String repoKey = "test-repo";
        String projectKey = "test-project";
        when(artifactoryContext.beanForType(ProjectsService.class)).thenReturn(projectsService);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(projectsService.getProjectKeyOfRepository(repoKey)).thenReturn(projectKey);
        when(repositoryService.repoConfigByKey(repoKey)).thenReturn(repoConfig);

        // When - Call bind(String) which should disable traffic log v3 flow
        TrafficContextThreadLocal.bind(repoKey);

        // Then - Verify that traffic log v3 flow is now disabled
        TrafficContext updatedContext = TrafficContextThreadLocal.getTrafficContext();
        assertSame(context, updatedContext);
        assertFalse(updatedContext.isTrafficLogV3Flow());
        assertFalse(TrafficContextThreadLocal.isTrafficLogV3Flow());
        assertEquals(repoKey, updatedContext.getOriginRepoKey());
        assertEquals(projectKey, updatedContext.getOriginProjectKey());
        assertEquals(repoConfig, updatedContext.getOriginRepoConfig());
    }

    @Test
    void testFillOriginFields() {
        // Given
        String repoKey = "test-repo";
        String projectKey = "test-project";
        TrafficContextThreadLocal.bind();
        when(artifactoryContext.beanForType(ProjectsService.class)).thenReturn(projectsService);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(projectsService.getProjectKeyOfRepository(repoKey)).thenReturn(projectKey);
        when(repositoryService.repoConfigByKey(repoKey)).thenReturn(repoConfig);

        // When
        TrafficContextThreadLocal.fillOriginFields(repoKey);

        // Then
        TrafficContext context = TrafficContextThreadLocal.getTrafficContext();
        assertNotNull(context);
        assertNotNull(context.getOriginRepoKey());
        assertEquals(repoKey, context.getOriginRepoKey());
        assertEquals(projectKey, context.getOriginProjectKey());
        assertEquals(repoConfig, context.getOriginRepoConfig());
    }

    @Test
    void testMethodsWithNoContext() {
        // Given - no context exists
        String repoKey = "test-repo";
        String path = "test/path";

        // When - Test fillOriginFields with no context
        TrafficContextThreadLocal.fillOriginFields(repoKey);

        // Then - should do nothing
        assertNull(TrafficContextThreadLocal.getTrafficContext());
        verify(projectsService, never()).getProjectKeyOfRepository(anyString());
        verify(repositoryService, never()).repoConfigByKey(anyString());

        // When - Test setTrafficLogV3Context with no context
        TrafficContextThreadLocal.setTrafficLogV3Context(repoKey, path);

        // Then - should do nothing
        assertNull(TrafficContextThreadLocal.getTrafficContext());
        verify(securityService, never()).currentUsername();
        repoPathFactoryMock.verify(() -> RepoPathFactory.create(anyString(), anyString()), never());
    }

    @Test
    void testSetTrafficLogV3Context() {
        // Given
        String repoKey = "test-repo";
        String path = "test/path";
        String username = "testuser";
        TrafficContextThreadLocal.bind();
        when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        when(securityService.currentUsername()).thenReturn(username);
        repoPathFactoryMock.when(() -> RepoPathFactory.create(repoKey, path)).thenReturn(repoPath);

        // When
        TrafficContextThreadLocal.setTrafficLogV3Context(repoKey, path);

        // Then
        TrafficContext context = TrafficContextThreadLocal.getTrafficContext();
        assertNotNull(context);
        assertTrue(context.isTrafficLogV3Flow());
        assertEquals(username, context.getUsername());
        assertEquals(repoPath, context.getRepoPath());

        verify(securityService).currentUsername();
        repoPathFactoryMock.verify(() -> RepoPathFactory.create(repoKey, path));
    }

    @Test
    void testThreadLocalIsolation() {
        // Given
        String repoKey1 = "repo1";
        String repoKey2 = "repo2";
        when(artifactoryContext.beanForType(ProjectsService.class)).thenReturn(projectsService);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(projectsService.getProjectKeyOfRepository(anyString())).thenReturn("project");
        when(repositoryService.repoConfigByKey(anyString())).thenReturn(repoConfig);

        // When - Test in current thread
        TrafficContextThreadLocal.bind(repoKey1);
        TrafficContext mainThreadContext = TrafficContextThreadLocal.getTrafficContext();

        // Test in another thread
        Thread otherThread = new Thread(() -> {
            TrafficContextThreadLocal.bind(repoKey2);
            TrafficContext otherThreadContext = TrafficContextThreadLocal.getTrafficContext();
            assertNotNull(otherThreadContext);
            assertNotNull(otherThreadContext.getOriginRepoKey());
            assertEquals(repoKey2, otherThreadContext.getOriginRepoKey());
        });
        otherThread.start();

        try {
            otherThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Thread was interrupted");
        }

        // Then - Verify main thread context is unchanged
        assertNotNull(mainThreadContext);
        assertNotNull(mainThreadContext.getOriginRepoKey());
        assertEquals(repoKey1, mainThreadContext.getOriginRepoKey());
        assertEquals(mainThreadContext, TrafficContextThreadLocal.getTrafficContext());
    }

    @Test
    void testCompleteWorkflow() {
        // Given
        String repoKey = "test-repo";
        String path = "test/path";
        String username = "testuser";
        String projectKey = "test-project";

        when(artifactoryContext.beanForType(ProjectsService.class)).thenReturn(projectsService);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(artifactoryContext.beanForType(SecurityService.class)).thenReturn(securityService);
        when(projectsService.getProjectKeyOfRepository(repoKey)).thenReturn(projectKey);
        when(repositoryService.repoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(securityService.currentUsername()).thenReturn(username);
        repoPathFactoryMock.when(() -> RepoPathFactory.create(repoKey, path)).thenReturn(repoPath);

        // When - Execute complete workflow
        TrafficContextThreadLocal.bind(repoKey);
        TrafficContextThreadLocal.setTrafficLogV3Context(repoKey, path);

        // Then - Verify all data is properly set
        TrafficContext context = TrafficContextThreadLocal.getTrafficContext();
        assertNotNull(context);
        assertEquals(repoKey, context.getOriginRepoKey());
        assertEquals(projectKey, context.getOriginProjectKey());
        assertEquals(repoConfig, context.getOriginRepoConfig());
        assertTrue(context.isTrafficLogV3Flow());
        assertEquals(username, context.getUsername());
        assertEquals(repoPath, context.getRepoPath());

        // Verify getter methods
        assertTrue(TrafficContextThreadLocal.isTrafficLogV3Flow());
        assertEquals(username, TrafficContextThreadLocal.getUsername());
        assertEquals(repoPath, TrafficContextThreadLocal.getRepoPath());

        // When - Unbind
        TrafficContextThreadLocal.unbind();

        // Then - Verify context is cleared
        assertNull(TrafficContextThreadLocal.getTrafficContext());
        assertFalse(TrafficContextThreadLocal.isTrafficLogV3Flow());
        assertNull(TrafficContextThreadLocal.getUsername());
        assertNull(TrafficContextThreadLocal.getRepoPath());
    }

    @Test
    void testDisableTrafficLogV3FlowWithNullContext() throws Exception {
        // Given - Ensure no context exists
        TrafficContextThreadLocal.unbind();
        assertNull(TrafficContextThreadLocal.getTrafficContext());

        // Use reflection to call the private disableTrafficLogV3Flow method directly
        // This allows us to test the null context branch without going through bind(String)
        Method disableMethod = TrafficContextThreadLocal.class.getDeclaredMethod("disableTrafficLogV3Flow");
        disableMethod.setAccessible(true);

        // When - Call disableTrafficLogV3Flow with null context
        // This should execute the false branch of the if condition
        disableMethod.invoke(null);

        // Then - Verify context is still null (method should do nothing)
        assertNull(TrafficContextThreadLocal.getTrafficContext());
        assertFalse(TrafficContextThreadLocal.isTrafficLogV3Flow());
    }
}
