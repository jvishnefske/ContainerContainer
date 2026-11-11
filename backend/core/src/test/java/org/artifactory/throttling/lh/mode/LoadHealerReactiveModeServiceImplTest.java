package org.artifactory.throttling.lh.mode;

import org.artifactory.system.load.event.SystemLoadEventData;
import org.artifactory.system.load.event.SystemLoadState;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class LoadHealerReactiveModeServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private LoadHealerService loadHealerService;

    private LoadHealerReactiveModeServiceImpl service;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        lenient().when(loadHealerService.getCurrentMode()).thenReturn(LoadHealerMode.REACTIVE.name());
    }

    @AfterEach
    public void teardown() throws Exception {
        mocks.close();
    }

    private LoadHealerReactiveModeServiceImpl createService() {
        return new LoadHealerReactiveModeServiceImpl(loadHealerService);
    }

    private static SystemLoadEventData event(SystemLoadState state) {
        return new SystemLoadEventData(state, 0L);
    }

    @Test
    void testOnSystemLoadEventNoOpWhenModeIsNotReactive() {
        when(loadHealerService.getCurrentMode()).thenReturn(LoadHealerMode.SIMULATION.name());
        service = createService();

        service.onSystemLoadEvent(event(SystemLoadState.OUTAGE));
        service.onSystemLoadEvent(event(SystemLoadState.OVERLOADED));

        verify(loadHealerService, never()).reloadModeFromConfig();
        verify(loadHealerService, never()).setActivitiesToReactiveMode();
    }

    @Test
    void testActivateReactiveModeNoOpWhenModeIsNotReactive() throws Exception {
        when(loadHealerService.getCurrentMode()).thenReturn(LoadHealerMode.PROACTIVE.name());
        service = createService();
        Field reactiveModeActiveField = LoadHealerReactiveModeServiceImpl.class.getDeclaredField("reactiveModeActive");
        reactiveModeActiveField.setAccessible(true);
        AtomicBoolean reactiveModeActive = (AtomicBoolean) reactiveModeActiveField.get(service);

        service.activateReactiveMode();

        assertFalse(reactiveModeActive.get());
        verify(loadHealerService, never()).setActivitiesToReactiveMode();
    }

    @Test
    void testActivateReactiveModeWhenNotActive() throws Exception {
        service = createService();
        Field reactiveModeActiveField = LoadHealerReactiveModeServiceImpl.class.getDeclaredField("reactiveModeActive");
        reactiveModeActiveField.setAccessible(true);
        AtomicBoolean reactiveModeActive = (AtomicBoolean) reactiveModeActiveField.get(service);
        assertFalse(reactiveModeActive.get());

        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        AtomicInteger restoreActivitiesCalled = new AtomicInteger(0);
        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();
        doAnswer(invocation -> {
            restoreActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).restoreActivitiesFromReactiveMode();

        service.activateReactiveMode();

        Awaitility.await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            assertTrue(reactiveModeActive.get());
            assertEquals(1, setActivitiesCalled.get());
        });
    }

    @Test
    void testActivateReactiveModeWhenAlreadyActive() throws Exception {
        service = createService();
        Field reactiveModeActiveField = LoadHealerReactiveModeServiceImpl.class.getDeclaredField("reactiveModeActive");
        reactiveModeActiveField.setAccessible(true);
        AtomicBoolean reactiveModeActive = (AtomicBoolean) reactiveModeActiveField.get(service);
        reactiveModeActive.set(true);

        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        service.activateReactiveMode();

        assertEquals(0, setActivitiesCalled.get());
    }

    @Test
    void testActivateReactiveModeConcurrentCalls() throws Exception {
        service = createService();

        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        CountDownLatch startLatch = new CountDownLatch(2);
        CountDownLatch endLatch = new CountDownLatch(2);

        Thread thread1 = new Thread(() -> {
            startLatch.countDown();
            try {
                startLatch.await();
                service.activateReactiveMode();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endLatch.countDown();
            }
        });

        Thread thread2 = new Thread(() -> {
            startLatch.countDown();
            try {
                startLatch.await();
                service.activateReactiveMode();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endLatch.countDown();
            }
        });

        thread1.start();
        thread2.start();

        assertTrue(endLatch.await(2, TimeUnit.SECONDS));

        Awaitility.await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertEquals(1, setActivitiesCalled.get()));
    }

    @Test
    void testOnSystemLoadEventWhenOutage() throws Exception {
        service = createService();
        Field reactiveModeActiveField = LoadHealerReactiveModeServiceImpl.class.getDeclaredField("reactiveModeActive");
        reactiveModeActiveField.setAccessible(true);
        AtomicBoolean reactiveModeActive = (AtomicBoolean) reactiveModeActiveField.get(service);
        assertFalse(reactiveModeActive.get());

        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        service.onSystemLoadEvent(event(SystemLoadState.OUTAGE));

        Awaitility.await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            assertTrue(reactiveModeActive.get());
            assertEquals(1, setActivitiesCalled.get());
        });
    }

    @Test
    void testOnSystemLoadEventWhenOutageDoesNotReloadFromDB() throws Exception {
        service = createService();
        doAnswer(invocation -> null).when(loadHealerService).setActivitiesToReactiveMode();

        service.onSystemLoadEvent(event(SystemLoadState.OUTAGE));

        Awaitility.await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                verify(loadHealerService).setActivitiesToReactiveMode());
        verify(loadHealerService, never()).reloadModeFromConfig();
    }

    @Test
    void testOnSystemLoadEventWhenOverloadedReloadsFromDB() {
        service = createService();

        service.onSystemLoadEvent(event(SystemLoadState.OVERLOADED));

        verify(loadHealerService).reloadModeFromConfig();
    }

    @Test
    void testOnSystemLoadEventWhenOverloadedDoesNotActivateReactiveMode() throws Exception {
        service = createService();
        Field reactiveModeActiveField = LoadHealerReactiveModeServiceImpl.class.getDeclaredField("reactiveModeActive");
        reactiveModeActiveField.setAccessible(true);
        AtomicBoolean reactiveModeActive = (AtomicBoolean) reactiveModeActiveField.get(service);
        assertFalse(reactiveModeActive.get());

        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        service.onSystemLoadEvent(event(SystemLoadState.OVERLOADED));

        assertFalse(reactiveModeActive.get());
        assertEquals(0, setActivitiesCalled.get());
    }

    @Test
    void testOnSystemLoadEventWhenHealthyTriggersNeither() {
        service = createService();

        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        service.onSystemLoadEvent(event(SystemLoadState.HEALTHY));

        verify(loadHealerService, never()).reloadModeFromConfig();
        assertEquals(0, setActivitiesCalled.get());
    }

    @Test
    void testOnSystemLoadEventWhenLoadedTriggersNeither() {
        service = createService();

        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        service.onSystemLoadEvent(event(SystemLoadState.LOADED));

        verify(loadHealerService, never()).reloadModeFromConfig();
        assertEquals(0, setActivitiesCalled.get());
    }

    @Test
    void testOnSystemLoadEventWhenLoadedAndReactiveModeActive() throws Exception {
        service = createService();
        Field reactiveModeActiveField = LoadHealerReactiveModeServiceImpl.class.getDeclaredField("reactiveModeActive");
        reactiveModeActiveField.setAccessible(true);
        AtomicBoolean reactiveModeActive = (AtomicBoolean) reactiveModeActiveField.get(service);
        reactiveModeActive.set(true);

        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        service.onSystemLoadEvent(event(SystemLoadState.LOADED));

        assertEquals(0, setActivitiesCalled.get());
    }

    @Test
    void testOnSystemLoadEventOutageWhenAlreadyActive() throws Exception {
        service = createService();
        Field reactiveModeActiveField = LoadHealerReactiveModeServiceImpl.class.getDeclaredField("reactiveModeActive");
        reactiveModeActiveField.setAccessible(true);
        AtomicBoolean reactiveModeActive = (AtomicBoolean) reactiveModeActiveField.get(service);
        reactiveModeActive.set(true);

        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        service.onSystemLoadEvent(event(SystemLoadState.OUTAGE));

        assertEquals(0, setActivitiesCalled.get());
    }

    @Test
    void testStartReactiveModeThreadWithInterruptedException() throws Exception {
        service = createService();
        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        AtomicInteger restoreActivitiesCalled = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            Thread.currentThread().interrupt();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        doAnswer(invocation -> {
            restoreActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).restoreActivitiesFromReactiveMode();

        service.activateReactiveMode();

        Awaitility.await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            assertEquals(1, setActivitiesCalled.get());
            assertEquals(1, restoreActivitiesCalled.get());
        });
    }

    @Test
    void testStartReactiveModeThreadWithShortDuration() throws Exception {
        service = createService();
        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        AtomicInteger restoreActivitiesCalled = new AtomicInteger(0);
        CountDownLatch setActivitiesLatch = new CountDownLatch(1);
        CountDownLatch restoreActivitiesLatch = new CountDownLatch(1);

        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            setActivitiesLatch.countDown();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        doAnswer(invocation -> {
            restoreActivitiesCalled.incrementAndGet();
            restoreActivitiesLatch.countDown();
            return null;
        }).when(loadHealerService).restoreActivitiesFromReactiveMode();

        service.activateReactiveMode();
        assertTrue(setActivitiesLatch.await(1, TimeUnit.SECONDS));
        assertEquals(1, setActivitiesCalled.get());
        Field reactiveModeActiveField = LoadHealerReactiveModeServiceImpl.class.getDeclaredField("reactiveModeActive");
        reactiveModeActiveField.setAccessible(true);
        AtomicBoolean reactiveModeActive = (AtomicBoolean) reactiveModeActiveField.get(service);
        assertTrue(reactiveModeActive.get());
    }

    @Test
    void testMultipleActivateReactiveModeCalls() throws Exception {
        service = createService();
        Field reactiveModeActiveField = LoadHealerReactiveModeServiceImpl.class.getDeclaredField("reactiveModeActive");
        reactiveModeActiveField.setAccessible(true);
        AtomicBoolean reactiveModeActive = (AtomicBoolean) reactiveModeActiveField.get(service);
        AtomicInteger setActivitiesCalled = new AtomicInteger(0);
        doAnswer(invocation -> {
            setActivitiesCalled.incrementAndGet();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        service.activateReactiveMode();
        assertTrue(reactiveModeActive.get());

        Awaitility.await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertEquals(1, setActivitiesCalled.get()));

        service.activateReactiveMode();

        assertEquals(1, setActivitiesCalled.get());
    }

    @Test
    void testConcurrentActivateReactiveMode() throws Exception {
        service = createService();
        Field reactiveModeActiveField = LoadHealerReactiveModeServiceImpl.class.getDeclaredField("reactiveModeActive");
        reactiveModeActiveField.setAccessible(true);
        AtomicBoolean reactiveModeActive = (AtomicBoolean) reactiveModeActiveField.get(service);
        CountDownLatch startLatch = new CountDownLatch(2);
        CountDownLatch endLatch = new CountDownLatch(2);
        AtomicInteger executionCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            executionCount.incrementAndGet();
            return null;
        }).when(loadHealerService).setActivitiesToReactiveMode();

        Thread thread1 = new Thread(() -> {
            startLatch.countDown();
            try {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                service.activateReactiveMode();
            } finally {
                endLatch.countDown();
            }
        });
        Thread thread2 = new Thread(() -> {
            startLatch.countDown();
            try {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                service.activateReactiveMode();
            } finally {
                endLatch.countDown();
            }
        });
        thread1.start();
        thread2.start();
        assertTrue(endLatch.await(1, TimeUnit.SECONDS));

        Awaitility.await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            assertEquals(1, executionCount.get());
            assertTrue(reactiveModeActive.get());
        });
    }
}
