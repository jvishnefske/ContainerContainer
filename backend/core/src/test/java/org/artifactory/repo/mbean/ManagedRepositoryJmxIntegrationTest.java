package org.artifactory.repo.mbean;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.storage.fs.service.FileService;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link ManagedRepository} that verifies JMX access works correctly
 * when invoked from external threads (simulating JMX/RMI access patterns).
 * <p>
 * This test registers the MBean with the platform MBeanServer and invokes it from
 * a separate thread pool to simulate real-world JMX client access.
 *
 * @author oleksandrl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManagedRepositoryJmxIntegrationTest {

    private static final String MBEAN_TYPE = "Repositories";
    private static final String TEST_REPO_KEY = "test-repo-integration";
    public static final String REPOSITORY_KEY_ATTR = "RepositoryKey";
    public static final String ARTIFACTS_COUNT_ATTR = "ArtifactsCount";
    public static final String ARTIFACTS_TOTAL_SIZE_ATTR = "ArtifactsTotalSize";

    @Mock
    private ArtifactoryContext context;
    @Mock
    private ArtifactoryHome home;
    @Mock
    private FileService fileService;

    private MBeanServer mbeanServer;
    private ObjectName mbeanName;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome.bind(home);
        when(context.beanForType(FileService.class)).thenReturn(fileService);
        when(context.getArtifactoryHome()).thenReturn(home);
        mbeanServer = ManagementFactory.getPlatformMBeanServer();
        mbeanName = MBeanRegistrationServiceImpl.createObjectName(MBEAN_TYPE, TEST_REPO_KEY);
        executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("JMX-Test-Thread");
            return thread;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mbeanServer.isRegistered(mbeanName)) {
            mbeanServer.unregisterMBean(mbeanName);
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
        try {
            unbindArtifactoryContext();
        } catch (Exception e) {
            // Ignore - may not be bound
        }
    }

    @Test
    void getRepositoryKey_shouldReturnRepoKey_whenAccessedViaJmx() throws Exception {
        // Arrange
        ManagedRepository managedRepository = new ManagedRepository(TEST_REPO_KEY);
        mbeanServer.registerMBean(managedRepository, mbeanName);
        // Act - Access via JMX getAttribute (simulating remote access)
        Object result = mbeanServer.getAttribute(mbeanName, REPOSITORY_KEY_ATTR);
        // Assert
        assertEquals(TEST_REPO_KEY, result);
    }

    @Test
    void getArtifactsCount_shouldReturnCount_whenAccessedViaJmxFromDifferentThread() throws Exception {
        // Arrange
        doAnswer(invocation -> {
            assertArtifactoryHomeBound();
            return 12345;
        }).when(fileService).getFilesCount(any(RepoPathImpl.class));
        ManagedRepository managedRepository = new ManagedRepository(TEST_REPO_KEY);
        mbeanServer.registerMBean(managedRepository, mbeanName);
        // Act - Access from a different thread (simulating JMX/RMI thread WITHOUT context)
        Callable<Long> jmxCall = createCallToGetAttributeAndVerifyContextBinding(ARTIFACTS_COUNT_ATTR);
        Future<Long> future = executorService.submit(jmxCall);
        Long result = getFutureWithAssertErrorBypass(future);
        assertEquals(12345L, result);
    }

    @Test
    void getArtifactsTotalSize_shouldReturnSize_whenAccessedViaJmxFromDifferentThread() throws Exception {
        // Arrange
        doAnswer(invocation -> {
            assertArtifactoryHomeBound();
            return 9876543210L;
        }).when(fileService).getFilesTotalSize(any(RepoPathImpl.class));
        ManagedRepository managedRepository = new ManagedRepository(TEST_REPO_KEY);
        mbeanServer.registerMBean(managedRepository, mbeanName);
        // Act - Access from a different thread (simulating JMX/RMI thread WITHOUT context)
        Callable<Long> jmxCall = createCallToGetAttributeAndVerifyContextBinding(ARTIFACTS_TOTAL_SIZE_ATTR);
        Future<Long> future = executorService.submit(jmxCall);
        Long result = getFutureWithAssertErrorBypass(future);
        // Assert
        assertEquals(9876543210L, result);
    }

    @Test
    void getAllAttributes_shouldReturnAllValues_whenAccessedViaJmx() throws Exception {
        // Arrange
        doAnswer(invocation -> {
            assertArtifactoryHomeBound();
            return 100;
        }).when(fileService).getFilesCount(any(RepoPathImpl.class));
        doAnswer(invocation -> {
            assertArtifactoryHomeBound();
            return 1024000L;
        }).when(fileService).getFilesTotalSize(any(RepoPathImpl.class));
        ManagedRepository managedRepository = new ManagedRepository(TEST_REPO_KEY);
        mbeanServer.registerMBean(managedRepository, mbeanName);
        Callable<AttributeList> jmxCall = createAttributeListCallable();
        Future<javax.management.AttributeList> future = executorService.submit(jmxCall);
        javax.management.AttributeList attributes = getFutureWithAssertErrorBypass(future);
        // Assert
        assertNotNull(attributes);
        assertEquals(3, attributes.size());
        Attribute repoKeyAttr = (Attribute) attributes.getFirst();
        assertEquals(REPOSITORY_KEY_ATTR, repoKeyAttr.getName());
        assertEquals(TEST_REPO_KEY, repoKeyAttr.getValue());
        Attribute countAttr = (Attribute) attributes.get(1);
        assertEquals(ARTIFACTS_COUNT_ATTR, countAttr.getName());
        assertEquals(100L, countAttr.getValue());
        Attribute sizeAttr = (Attribute) attributes.get(2);
        assertEquals(ARTIFACTS_TOTAL_SIZE_ATTR, sizeAttr.getName());
        assertEquals(1024000L, sizeAttr.getValue());
    }

    @Test
    void multipleSequentialCalls_shouldWorkCorrectly_whenAccessedViaJmx() throws Exception {
        // Arrange
        // Verify context is bound on each call
        doAnswer(invocation -> {
            assertArtifactoryHomeBound();
            return 50;
        }).doAnswer(invocation -> {
            assertArtifactoryHomeBound();
            return 75;
        }).doAnswer(invocation -> {
            assertArtifactoryHomeBound();
            return 100;
        }).when(fileService).getFilesCount(any(RepoPathImpl.class));
        ManagedRepository managedRepository = new ManagedRepository(TEST_REPO_KEY);
        mbeanServer.registerMBean(managedRepository, mbeanName);
        // Act - Multiple sequential calls from JMX thread WITHOUT context
        Callable<Long> jmxCall = createCallToGetAttributeAndVerifyContextBinding(ARTIFACTS_COUNT_ATTR);
        // Assert
        assertEquals(50L, getFutureWithAssertErrorBypass(executorService.submit(jmxCall)));
        assertEquals(75L, getFutureWithAssertErrorBypass(executorService.submit(jmxCall)));
        assertEquals(100L, getFutureWithAssertErrorBypass(executorService.submit(jmxCall)));
    }

    @Test
    void concurrentAccess_shouldWorkCorrectly_whenMultipleThreadsAccessMBean() throws Exception {
        // Arrange
        // Verify context is bound during concurrent access
        doAnswer(invocation -> {
            assertArtifactoryHomeBound();
            return 1000;
        }).when(fileService).getFilesCount(any(RepoPathImpl.class));
        doAnswer(invocation -> {
            assertArtifactoryHomeBound();
            return 5000000L;
        }).when(fileService).getFilesTotalSize(any(RepoPathImpl.class));
        ManagedRepository managedRepository = new ManagedRepository(TEST_REPO_KEY);
        mbeanServer.registerMBean(managedRepository, mbeanName);
        ExecutorService multiThreadExecutor = Executors.newFixedThreadPool(3);
        try {
            // Act - Access from multiple threads simultaneously WITHOUT context
            Callable<Long> countCall = createCallToGetAttributeAndVerifyContextBinding(ARTIFACTS_COUNT_ATTR);
            Callable<Long> sizeCall = createCallToGetAttributeAndVerifyContextBinding(ARTIFACTS_TOTAL_SIZE_ATTR);
            Future<Long> countFuture = multiThreadExecutor.submit(countCall);
            Future<Long> sizeFuture = multiThreadExecutor.submit(sizeCall);
            Long count = getFutureWithAssertErrorBypass(countFuture);
            Long size = getFutureWithAssertErrorBypass(sizeFuture);
            // Assert
            assertEquals(1000L, count);
            assertEquals(5000000L, size);
        } finally {
            multiThreadExecutor.shutdownNow();
        }
    }

    private <T> T getFutureWithAssertErrorBypass(Future<T> future) throws ExecutionException, InterruptedException {
        try {
            return future.get();
        } catch (Throwable t) {
            if (ExceptionUtils.getThrowableList(t).stream().anyMatch(AssertionError.class::isInstance)) {
                throw new AssertionError(t);
            }
            throw t;
        }
    }

    private static void unbindArtifactoryContext() {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        verifyContextNotBound();
    }

    private static void verifyContextNotBound() {
        assertThrows(IllegalStateException.class, ArtifactoryHome::get,
                "Context should NOT be bound on JMX thread before/after MBean invocation");
    }

    private void assertArtifactoryHomeBound() {
        // Verify ArtifactoryHome (and thus ArtifactoryContext) is bound during the MBean methods execution
        ArtifactoryHome boundHome = assertDoesNotThrow(ArtifactoryHome::get,
                "ArtifactoryHome must be bound during MBean methods execution");
        assertEquals(home, boundHome, "Bound home should match the mocked home");
    }

    private Callable<Long> createCallToGetAttributeAndVerifyContextBinding(String attributeName) {
        return () -> {
            // Explicitly unbind context to simulate JMX/RMI thread that has no context
            unbindArtifactoryContext();
            // Now invoke the MBean - it must bind context itself
            long result = (Long) mbeanServer.getAttribute(mbeanName, attributeName);
            // Make sure context is unbound from JMX thread after MBean invocation
            verifyContextNotBound();
            return result;
        };
    }

    private @NonNull Callable<AttributeList> createAttributeListCallable() {
        String[] attributeNames = {REPOSITORY_KEY_ATTR, ARTIFACTS_COUNT_ATTR, ARTIFACTS_TOTAL_SIZE_ATTR};
        // Act - Get all attributes at once (common JMX monitoring pattern)
        return () -> {
            // Explicitly unbind context to simulate JMX/RMI thread
            unbindArtifactoryContext();
            AttributeList results = mbeanServer.getAttributes(mbeanName, attributeNames);
            // Make sure context is unbound from JMX thread after MBean invocation
            verifyContextNotBound();
            return results;
        };
    }
}
