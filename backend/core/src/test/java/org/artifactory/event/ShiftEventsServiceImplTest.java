package org.artifactory.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.Sets;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.event.queue.QueueTriggeringService;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.locks.service.UnsupportedReentrantLockException;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.event.EventsService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.awaitility.Awaitility;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.artifactory.concurrent.NonStandardLockCategory.FED_SHIFT_EVENTS;
import static org.artifactory.event.ShiftEventsServiceImpl.SHIFT_EVENT_LOCK_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

@Test
public class ShiftEventsServiceImplTest extends ArtifactoryHomeBoundTest {

    private ShiftEventsServiceImpl shiftEventsService;

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HaCommonAddon haCommonAddon;
    @Mock
    private ConflictsGuard<Object> conflictsGuard;
    @Mock
    private ConflictGuard conflictGuard;
    @Mock
    private EventsService eventsService;
    @Mock
    private QueueTriggeringService queueTriggeringService;

    private AtomicInteger aWaits = null;
    private AutoCloseable mocks;

    @BeforeClass
    private void before() {
        ((Logger) LoggerFactory.getILoggerFactory().getLogger(ShiftEventsServiceImpl.class.getName()))
                .setLevel(Level.DEBUG);
    }

    @AfterClass
    private void after() {
        ((Logger) LoggerFactory.getILoggerFactory().getLogger(ShiftEventsServiceImpl.class.getName()))
                .setLevel(Level.INFO);
    }

    @BeforeMethod
    private void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        setStringSystemProperty(ConstantValues.shiftEventsTryLockTimeoutMs, "5000");
        setStringSystemProperty(ConstantValues.shiftEventsPeriodicMs, "3000");
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(haCommonAddon.getConflictsGuard(FED_SHIFT_EVENTS.getName())).thenReturn(conflictsGuard);
        ArtifactoryContext context = Mockito.mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        when(conflictsGuard.getLock(SHIFT_EVENT_LOCK_KEY)).thenReturn(conflictGuard);
        when(conflictGuard.tryToLock(anyLong(), any(TimeUnit.class))).thenReturn(true);

        aWaits = new AtomicInteger();
        shiftEventsService = new ShiftEventsServiceImpl(addonsManager, eventsService, queueTriggeringService) {
            @Override
            void await() throws InterruptedException {
                aWaits.incrementAndGet();
                super.await();
            }
        };
    }

    @AfterMethod
    public void cleanup() throws Exception {
        shiftEventsService.stop();
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testStop() throws Exception {
        // Given
        shiftEventsService.onContextCreated();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).with()
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> aWaits.get() == 1);

        // When
        shiftEventsService.stop();
        // Then
        assertTrue(shiftEventsService.executor.isTerminated());
    }

    @Test
    public void testHasPendingShifts() {
        // When
        shiftEventsService.triggerShift();
        // Then
        assertTrue(shiftEventsService.versionCache.hasNewVersion());
    }

    @Test
    public void testTriggerShiftAwakeThread() throws InterruptedException {
        Instant start = Instant.now();
        CountDownLatch latch = new CountDownLatch(1);
        new Thread(() -> {
            synchronized (shiftEventsService.lock) {
                try {
                    latch.countDown();
                    shiftEventsService.lock.wait(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
        latch.await(5, TimeUnit.SECONDS);
        shiftEventsService.triggerShift();

        assertTrue(Duration.between(start, Instant.now()).toMillis() < 2000);
    }

    @Test
    public void testTriggerQueues() {

        Set<String> repos = Sets.newHashSet("repo1", "repo2");

        // Given
        when(eventsService.shiftEvents()).thenAnswer(invocation -> {
            Set<String> r = new HashSet<>(repos);
            repos.clear();
            return r;
        });
        // When
        shiftEventsService.shiftEvents();
        // Then
        Mockito.verify(queueTriggeringService, Mockito.times(1))
                .triggerQueues(eq(Sets.newHashSet("repo1", "repo2")));
    }

    @Test
    public void testShiftEventsWithIsolatedMemberButNotHA() {
        HaAddon haAddon = Mockito.mock(HaAddon.class);
        when(ContextHelper.get().beanForType(HaAddon.class)).thenReturn(haAddon);
        setStringSystemProperty(ConstantValues.shiftEventsIsolatedMember, "test");
        shiftEventsService = new ShiftEventsServiceImpl(addonsManager, eventsService, queueTriggeringService) {
            @Override
            void await() throws InterruptedException {
                aWaits.incrementAndGet();
                super.await();
            }
        };
        shiftEventsService.shiftEvents();
        Mockito.verify(haAddon).isHaEnabled();
        Mockito.verify(haAddon, Mockito.never()).getCurrentMemberServerId();
        Mockito.verify(eventsService).shiftEvents();
    }

    @Test
    public void testShiftEventsWithIsolatedMember() {
        HaAddon haAddon = Mockito.mock(HaAddon.class);
        when(ContextHelper.get().beanForType(HaAddon.class)).thenReturn(haAddon);
        setStringSystemProperty(ConstantValues.shiftEventsIsolatedMember, "test");
        when(haAddon.getCurrentMemberServerId()).thenReturn("test");
        when(haAddon.isHaEnabled()).thenReturn(true);
        shiftEventsService = new ShiftEventsServiceImpl(addonsManager, eventsService, queueTriggeringService) {
            @Override
            void await() throws InterruptedException {
                aWaits.incrementAndGet();
                super.await();
            }
        };
        Set<String> repos = Set.of("test");
        when(eventsService.shiftEvents()).thenReturn(repos).thenReturn(Set.of());
        shiftEventsService.shiftEvents();
        Mockito.verify(haAddon).isHaEnabled();
        Mockito.verify(eventsService, Mockito.times(2)).shiftEvents();
        Mockito.verify(queueTriggeringService, Mockito.times(2)).triggerConstantQueues();
        Mockito.verify(queueTriggeringService).triggerQueues(repos);
        Mockito.verify(haAddon).getCurrentMemberServerId();
    }

    @Test
    public void testShiftEventsWithIsolatedMemberRequestMemberOffline() {
        HaAddon haAddon = Mockito.mock(HaAddon.class);
        when(ContextHelper.get().beanForType(HaAddon.class)).thenReturn(haAddon);
        setStringSystemProperty(ConstantValues.shiftEventsIsolatedMember, "test");
        when(haAddon.getCurrentMemberServerId()).thenReturn("test2");
        ArtifactoryServer artifactoryServer = Mockito.mock(ArtifactoryServer.class);
        when(haAddon.getAllArtifactoryServers()).thenReturn(List.of(artifactoryServer));
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(artifactoryServer.getServerId()).thenReturn("test");
        when(artifactoryServer.getServerState()).thenReturn(ArtifactoryServerState.OFFLINE);

        shiftEventsService = new ShiftEventsServiceImpl(addonsManager, eventsService, queueTriggeringService) {
            @Override
            void await() throws InterruptedException {
                aWaits.incrementAndGet();
                super.await();
            }
        };
        shiftEventsService.shiftEvents();
        Mockito.verify(haAddon).isHaEnabled();
        Mockito.verify(eventsService).shiftEvents();
        Mockito.verify(haAddon).getCurrentMemberServerId();
        Mockito.verify(artifactoryServer).getServerId();
        Mockito.verify(artifactoryServer).getServerState();
    }

    @Test
    public void testShiftEventsWithIsolatedMemberRequestOnDifferentMember() throws Exception {
        HaAddon haAddon = Mockito.mock(HaAddon.class);
        when(ContextHelper.get().beanForType(HaAddon.class)).thenReturn(haAddon);
        SecurityService securityService = Mockito.mock(SecurityService.class);
        Mockito.doAnswer(invocationOnMock -> {
            Runnable argument = invocationOnMock.getArgument(0, Runnable.class);
            argument.run();
            return null;
        }).when(securityService).doAsSystem(any());
        when(ContextHelper.get().beanForType(SecurityService.class)).thenReturn(securityService);
        setStringSystemProperty(ConstantValues.shiftEventsIsolatedMember, "test");
        when(haAddon.getCurrentMemberServerId()).thenReturn("test2");
        ArtifactoryServer artifactoryServer = Mockito.mock(ArtifactoryServer.class);
        when(haAddon.getAllArtifactoryServers()).thenReturn(List.of(artifactoryServer));
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(artifactoryServer.getServerId()).thenReturn("test");
        when(artifactoryServer.getServerState()).thenReturn(ArtifactoryServerState.RUNNING);
        when(artifactoryServer.getLastHeartbeat()).thenReturn(System.currentTimeMillis());

        shiftEventsService = new ShiftEventsServiceImpl(addonsManager, eventsService, queueTriggeringService) {
            @Override
            void await() throws InterruptedException {
                aWaits.incrementAndGet();
                super.await();
            }
        };
        shiftEventsService.shiftEvents();
        Mockito.verify(haAddon).isHaEnabled();
        Mockito.verify(eventsService, Mockito.never()).shiftEvents();
        Mockito.verify(haAddon).getCurrentMemberServerId();
        Mockito.verify(haAddon).propagateShiftEventsOperation("test");
        Mockito.verify(artifactoryServer, Mockito.times(2)).getServerId();
        Mockito.verify(artifactoryServer).getServerState();
        // RTFS-4802: propagating to the isolated member must never touch the distributed lock.
        Mockito.verify(conflictsGuard, Mockito.never()).getLock(SHIFT_EVENT_LOCK_KEY);
        Mockito.verify(conflictGuard, Mockito.never()).tryToLock(Mockito.anyLong(), Mockito.any(TimeUnit.class));
    }

    @Test
    public void testShiftEventsWithIsolatedMemberOnDifferentMemberNeverAcquiresDistributedLock() throws Exception {
        HaAddon haAddon = Mockito.mock(HaAddon.class);
        when(ContextHelper.get().beanForType(HaAddon.class)).thenReturn(haAddon);
        SecurityService securityService = Mockito.mock(SecurityService.class);
        Mockito.doAnswer(invocationOnMock -> {
            Runnable argument = invocationOnMock.getArgument(0, Runnable.class);
            argument.run();
            return null;
        }).when(securityService).doAsSystem(any());
        when(ContextHelper.get().beanForType(SecurityService.class)).thenReturn(securityService);
        setStringSystemProperty(ConstantValues.shiftEventsIsolatedMember, "test");
        when(haAddon.getCurrentMemberServerId()).thenReturn("test2");
        ArtifactoryServer artifactoryServer = Mockito.mock(ArtifactoryServer.class);
        when(haAddon.getAllArtifactoryServers()).thenReturn(List.of(artifactoryServer));
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(artifactoryServer.getServerId()).thenReturn("test");
        when(artifactoryServer.getServerState()).thenReturn(ArtifactoryServerState.RUNNING);
        when(artifactoryServer.getLastHeartbeat()).thenReturn(System.currentTimeMillis());

        shiftEventsService = new ShiftEventsServiceImpl(addonsManager, eventsService, queueTriggeringService) {
            @Override
            void await() throws InterruptedException {
                aWaits.incrementAndGet();
                super.await();
            }
        };

        // When: RTFS-4802 regression guard - this is the core fix, verified independently of the test above.
        shiftEventsService.shiftEvents();

        // Then
        Mockito.verify(haAddon).propagateShiftEventsOperation("test");
        Mockito.verify(eventsService, Mockito.never()).shiftEvents();
        Mockito.verify(conflictsGuard, Mockito.never()).getLock(SHIFT_EVENT_LOCK_KEY);
        Mockito.verify(conflictGuard, Mockito.never()).tryToLock(Mockito.anyLong(), Mockito.any(TimeUnit.class));
    }

    @Test
    public void testIsolatedMemberIdentityIsCachedAcrossCycles() {
        HaAddon haAddon = Mockito.mock(HaAddon.class);
        when(ContextHelper.get().beanForType(HaAddon.class)).thenReturn(haAddon);
        SecurityService securityService = Mockito.mock(SecurityService.class);
        Mockito.doAnswer(invocationOnMock -> {
            Runnable argument = invocationOnMock.getArgument(0, Runnable.class);
            argument.run();
            return null;
        }).when(securityService).doAsSystem(any());
        when(ContextHelper.get().beanForType(SecurityService.class)).thenReturn(securityService);
        setStringSystemProperty(ConstantValues.shiftEventsIsolatedMember, "test");
        when(haAddon.getCurrentMemberServerId()).thenReturn("test2");
        ArtifactoryServer artifactoryServer = Mockito.mock(ArtifactoryServer.class);
        when(haAddon.getAllArtifactoryServers()).thenReturn(List.of(artifactoryServer));
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(artifactoryServer.getServerId()).thenReturn("test");
        when(artifactoryServer.getServerState()).thenReturn(ArtifactoryServerState.RUNNING);
        when(artifactoryServer.getLastHeartbeat()).thenReturn(System.currentTimeMillis());

        shiftEventsService = new ShiftEventsServiceImpl(addonsManager, eventsService, queueTriggeringService) {
            @Override
            void await() throws InterruptedException {
                aWaits.incrementAndGet();
                super.await();
            }
        };

        // When: two separate cycles
        shiftEventsService.shiftEvents();
        shiftEventsService.shiftEvents();

        // Then: the restart-only identity fact is resolved once and cached...
        Mockito.verify(haAddon, Mockito.times(1)).getCurrentMemberServerId();
        // ...but liveness (can change at runtime) is re-checked every cycle...
        Mockito.verify(haAddon, Mockito.times(2)).getAllArtifactoryServers();
        // ...and propagation itself still happens every cycle regardless of caching.
        Mockito.verify(haAddon, Mockito.times(2)).propagateShiftEventsOperation("test");
    }

    @Test
    public void testShiftEventsWithIsolatedMemberRunningButHeartbeatStale() {
        // RTFS-4802: a hard-killed node's server_state column stays RUNNING forever (nothing updates it
        // away from RUNNING short of the 3-hour-default stale-server cleanup job deleting the row) - so the
        // liveness check must also verify heartbeat freshness, not just the stored state.
        HaAddon haAddon = Mockito.mock(HaAddon.class);
        when(ContextHelper.get().beanForType(HaAddon.class)).thenReturn(haAddon);
        setStringSystemProperty(ConstantValues.shiftEventsIsolatedMember, "test");
        when(haAddon.getCurrentMemberServerId()).thenReturn("test2");
        ArtifactoryServer artifactoryServer = Mockito.mock(ArtifactoryServer.class);
        when(haAddon.getAllArtifactoryServers()).thenReturn(List.of(artifactoryServer));
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(artifactoryServer.getServerId()).thenReturn("test");
        when(artifactoryServer.getServerState()).thenReturn(ArtifactoryServerState.RUNNING);
        when(artifactoryServer.getLastHeartbeat()).thenReturn(
                System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));

        shiftEventsService = new ShiftEventsServiceImpl(addonsManager, eventsService, queueTriggeringService) {
            @Override
            void await() throws InterruptedException {
                aWaits.incrementAndGet();
                super.await();
            }
        };

        // When
        shiftEventsService.shiftEvents();

        // Then: falls back to running locally instead of propagating to a server whose stored state says
        // RUNNING but whose heartbeat is stale.
        Mockito.verify(haAddon, Mockito.never()).propagateShiftEventsOperation(Mockito.anyString());
        Mockito.verify(eventsService).shiftEvents();
    }

    @Test
    public void testStartIsTryingShift() {

        // When
        shiftEventsService.onContextCreated();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).with()
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> aWaits.get() > 0);

        // Then
        Mockito.verify(shiftEventsService.eventsService, Mockito.times(1)).shiftEvents();
        Mockito.verify(shiftEventsService.queueTriggeringService, Mockito.never()).triggerQueues(Mockito.anySet());
    }

    @Test
    public void testTriggerShiftWakeupThreadAndShiftEvents() throws Exception {
        // Given
        shiftEventsService.onContextCreated();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).with()
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> aWaits.get() > 0);

        Thread.sleep(100);

        shiftEventsService.triggerShift();

        // Then
        Awaitility.await().atMost(5, TimeUnit.SECONDS).with()
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> aWaits.get() == 2);
        Mockito.verify(eventsService, Mockito.times(2)).shiftEvents();
        Mockito.verify(shiftEventsService.queueTriggeringService, Mockito.never()).triggerQueues(Mockito.anySet());
    }

    @Test
    public void testTriggerShiftDuringShiftInvokeAnotherShift() throws Exception {

        // Given
        shiftEventsService.onContextCreated();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).with()
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> aWaits.get() == 1);

        Thread.sleep(100);
        CountDownLatch count1 = new CountDownLatch(1);
        CountDownLatch count2 = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            count1.countDown();
            count2.await(5, TimeUnit.SECONDS);
            return Collections.emptySet();
        }).when(eventsService).shiftEvents();

        // When
        shiftEventsService.triggerShift();
        count1.await(5, TimeUnit.SECONDS);
        shiftEventsService.triggerShift();
        count2.countDown();

        // Then
        Awaitility.await().atMost(5, TimeUnit.SECONDS).with()
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> aWaits.get() >= 2);

        Mockito.verify(eventsService, Mockito.times(3)).shiftEvents();
        Mockito.verify(shiftEventsService.queueTriggeringService, Mockito.never()).triggerQueues(Mockito.anySet());
    }

    @Test
    public void testExceptionDuringShiftInvokeAnotherShiftAfterWaiting() {
        // Given
        AtomicInteger numOfshiftOccurred = new AtomicInteger();
        Set<String> repos = Sets.newHashSet("successful-shift-repo");
        when(eventsService.shiftEvents()).thenAnswer((Answer<Set<String>>) invocation -> {
            if (numOfshiftOccurred.getAndIncrement() == 2) {
                throw new RuntimeException("shift error");
            } else {
                Set<String> r = new HashSet<>(repos);
                repos.clear();
                return r;
            }
        });

        // When
        shiftEventsService.onContextCreated();

        // Then
        Awaitility.await().atMost(10, TimeUnit.SECONDS).with()
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> aWaits.get() == 2 && numOfshiftOccurred.get() == 3);

        Mockito.verify(eventsService, Mockito.times(3)).shiftEvents();
        Mockito.verify(shiftEventsService.queueTriggeringService, Mockito.times(1))
                .triggerQueues(Mockito.anySet());
    }

    @Test(timeOut = 5000)
    public void testRunShiftEventsBreaksBeforeLockLeaseExpires() {
        // Given: a 1-minute lease with a 50% break threshold, so the loop should break once simulated
        // elapsed time exceeds 30 seconds - even though the mocked backlog never empties (RTFS-4532: a
        // large real backlog can take many days to drain, which must not mean holding the lock that long).
        setStringSystemProperty(ConstantValues.haShiftEventsLockLeasePeriodMinutes, "1");
        setStringSystemProperty(ConstantValues.shiftEventsLockLeaseBreakThresholdPercent, "50");

        AtomicInteger repoCounter = new AtomicInteger();
        when(eventsService.shiftEvents())
                .thenAnswer(invocation -> Set.of("repo-" + repoCounter.incrementAndGet()));

        Instant base = Instant.now();
        AtomicInteger nowCalls = new AtomicInteger();
        shiftEventsService = new ShiftEventsServiceImpl(addonsManager, eventsService, queueTriggeringService) {
            @Override
            void await() throws InterruptedException {
                aWaits.incrementAndGet();
                super.await();
            }

            @Override
            Instant now() {
                // 1st call is the loop-start snapshot; each check thereafter advances by 20s of
                // simulated time, so elapsed time crosses the 30s threshold on the 2nd batch.
                return base.plusSeconds(20L * nowCalls.getAndIncrement());
            }
        };

        // When
        shiftEventsService.shiftEvents();

        // Then: the do-while loop stopped after 2 batches instead of spinning forever on a backlog
        // that never empties.
        Mockito.verify(eventsService, Mockito.times(2)).shiftEvents();
        Mockito.verify(queueTriggeringService, Mockito.times(2)).triggerConstantQueues();
    }

    @Test
    public void testNoDistributedLockTryShiftAgain() throws Exception {
        // Given
        shiftEventsService.onContextCreated();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).with()
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> aWaits.get() == 1);

        AtomicInteger locks = new AtomicInteger();
        when(conflictsGuard.tryToLock(eq(SHIFT_EVENT_LOCK_KEY), anyLong(),
                any(TimeUnit.class))).thenAnswer(
                (Answer<Boolean>) invocation -> {
                    LoggerFactory.getLogger(ShiftEventsServiceImpl.class).debug("try lock ==> {}", locks.get());
                    return locks.incrementAndGet() >= 3;
                });

        // When
        shiftEventsService.triggerShift();

        // Then
        Awaitility.await().atMost(5, TimeUnit.SECONDS).with()
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> aWaits.get() >= 2);

        Mockito.verify(eventsService, Mockito.times(2)).shiftEvents();
        Mockito.verify(shiftEventsService.queueTriggeringService, Mockito.never()).triggerQueues(Mockito.anySet());
    }

    @Test
    public void testForceReleaseReentrantLockWithRetry() throws Exception {
        shiftEventsService.onContextCreated();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).with()
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> aWaits.get() == 1);

        AtomicBoolean lockedByMe = new AtomicBoolean(true);
        when(conflictGuard.tryToLock(anyLong(), any(TimeUnit.class))).thenAnswer(
                (Answer<Boolean>) invocation -> {
                    if (lockedByMe.get()) {
                        throw new UnsupportedReentrantLockException();
                    } else {
                        return true;
                    }
                });

        Mockito.doAnswer(invocation -> {
            lockedByMe.set(false);
            return null;
        }).when(conflictGuard).forceUnlock();

        // When
        shiftEventsService.triggerShift();

        // Then
        Awaitility.await().atMost(15, TimeUnit.SECONDS).with()
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> aWaits.get() >= 2);

        Mockito.verify(conflictGuard, Mockito.times(1)).unlock();
    }
}