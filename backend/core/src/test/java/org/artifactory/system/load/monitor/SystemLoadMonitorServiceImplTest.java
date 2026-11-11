package org.artifactory.system.load.monitor;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.probes.tomcat.ConnectorMetric;
import org.artifactory.probes.tomcat.ConnectorsMonitor;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.system.load.event.ConnectorLoadState;
import org.artifactory.system.load.event.SystemLoadEventData;
import org.artifactory.system.load.event.SystemLoadState;
import org.artifactory.system.load.listener.SystemLoadEventListener;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.system.load.event.ConnectorLoadState.HEALTHY;
import static org.artifactory.system.load.event.ConnectorLoadState.LOADED;
import static org.artifactory.system.load.event.ConnectorLoadState.OUTAGE;
import static org.artifactory.system.load.event.ConnectorLoadState.OVERLOADED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemLoadMonitorServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ConnectorsMonitor connectorsMonitor;
    @Mock
    private TaskService taskService;
    @Mock
    private SystemLoadEventListener listener;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ArtifactoryContext artifactoryContext;
    private SystemLoadMonitorServiceImpl service;

    private static Stream<Arguments> twoConnectorsChangeFromHealthyToLoaded() {
        return Stream.of(
                Arguments.of(HEALTHY, LOADED, SystemLoadState.LOADED),
                Arguments.of(HEALTHY, OUTAGE, SystemLoadState.OUTAGE),
                Arguments.of(HEALTHY, OVERLOADED, SystemLoadState.OVERLOADED),

                Arguments.of(LOADED, LOADED, SystemLoadState.LOADED),
                Arguments.of(LOADED, OVERLOADED, SystemLoadState.OVERLOADED),
                Arguments.of(LOADED, OUTAGE, SystemLoadState.OUTAGE),

                Arguments.of(OVERLOADED, OVERLOADED, SystemLoadState.OVERLOADED),
                Arguments.of(OVERLOADED, OUTAGE, SystemLoadState.OUTAGE),

                Arguments.of(OUTAGE, OUTAGE, SystemLoadState.OUTAGE)
        );
    }

    private static Stream<Arguments> loadedStatesProvider() {
        return Stream.of(Arguments.of(LOADED), Arguments.of(OVERLOADED), Arguments.of(OUTAGE));
    }

    @BeforeEach
    void setUp() {
        when(artifactoryContext.beansForType(SystemLoadEventListener.class).values()).thenReturn(Set.of(listener));
        when(connectorsMonitor.getActiveConnectorsMetrics()).thenReturn(Collections.emptySet());
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        service = new SystemLoadMonitorServiceImpl(connectorsMonitor, taskService, new DisabledOutageSelfProbe());
        service.init();
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testScheduleSystemLoadMonitoring() {
        verify(taskService).startTask(any(Task.class), eq(false));
    }

    @Test
    void testMonitorSystemLoad_SingleConnector_NotifiesOnStateChange() {
        ConnectorMetric oldMetric = mock(ConnectorMetric.class);
        ConnectorMetric newMetric = mock(ConnectorMetric.class);

        when(oldMetric.name()).thenReturn("httpConnector");
        when(oldMetric.getConnectorLoadState()).thenReturn(HEALTHY);
        when(newMetric.name()).thenReturn("httpConnector");
        when(newMetric.getConnectorLoadState()).thenReturn(OVERLOADED);

        simulateInitialState(oldMetric);
        long changeTime = 12345L;
        simulateStateChange(changeTime, newMetric);

        service.monitorSystemLoad();

        verifyChangeEventTriggered(SystemLoadState.OVERLOADED, changeTime, listener);
        assertEquals(SystemLoadState.OVERLOADED, service.getSystemLoadState());
    }

    @ParameterizedTest
    @MethodSource("twoConnectorsChangeFromHealthyToLoaded")
    void testMonitorSystemLoad_TwoConnectors_ChangeFromHealthyToLoaded(ConnectorLoadState firstConnectorState,
            ConnectorLoadState secondConnectorState, SystemLoadState expectedSystemLoadState) {
        ConnectorMetric oldMetric1 = mock(ConnectorMetric.class);
        ConnectorMetric oldMetric2 = mock(ConnectorMetric.class);
        ConnectorMetric newMetric1 = mock(ConnectorMetric.class);
        ConnectorMetric newMetric2 = mock(ConnectorMetric.class);

        lenient().when(oldMetric1.name()).thenReturn("httpConnector1");
        lenient().when(oldMetric1.getConnectorLoadState()).thenReturn(HEALTHY);
        when(oldMetric2.name()).thenReturn("httpConnector2");
        lenient().when(oldMetric2.getConnectorLoadState()).thenReturn(HEALTHY);

        lenient().when(newMetric1.name()).thenReturn("httpConnector1");
        when(newMetric1.getConnectorLoadState()).thenReturn(firstConnectorState);

        lenient().when(newMetric2.name()).thenReturn("httpConnector2");
        when(newMetric2.getConnectorLoadState()).thenReturn(secondConnectorState);

        simulateInitialState(oldMetric1, oldMetric2);
        assertEquals(SystemLoadState.HEALTHY, service.getSystemLoadState());

        long changeTime = 12345L;
        simulateStateChange(changeTime, newMetric1, newMetric2);

        service.monitorSystemLoad();

        verifyChangeEventTriggered(expectedSystemLoadState, changeTime, listener);
        assertEquals(expectedSystemLoadState, service.getSystemLoadState());
    }

    @ParameterizedTest
    @MethodSource("loadedStatesProvider")
    void testMonitorSystemLoad_TwoConnectors_ChangeFromLoadedToHealthy(ConnectorLoadState loadedState) {
        ConnectorMetric oldMetric1 = mock(ConnectorMetric.class);
        ConnectorMetric oldMetric2 = mock(ConnectorMetric.class);
        ConnectorMetric newMetric1 = mock(ConnectorMetric.class);
        ConnectorMetric newMetric2 = mock(ConnectorMetric.class);

        lenient().when(oldMetric1.name()).thenReturn("httpConnector1");
        lenient().when(oldMetric1.getConnectorLoadState()).thenReturn(loadedState);
        when(oldMetric2.name()).thenReturn("httpConnector2");
        lenient().when(oldMetric2.getConnectorLoadState()).thenReturn(loadedState);

        lenient().when(newMetric1.name()).thenReturn("httpConnector1");
        when(newMetric1.getConnectorLoadState()).thenReturn(HEALTHY);

        lenient().when(newMetric2.name()).thenReturn("httpConnector2");
        when(newMetric2.getConnectorLoadState()).thenReturn(HEALTHY);

        simulateInitialState(oldMetric1, oldMetric2);
        assertEquals(SystemLoadState.fromConnectorLoadState(loadedState), service.getSystemLoadState());

        long changeTime = 12345L;
        simulateStateChange(changeTime, newMetric1, newMetric2);

        service.monitorSystemLoad();

        verifyChangeEventTriggered(SystemLoadState.HEALTHY, changeTime, listener);
        assertEquals(SystemLoadState.HEALTHY, service.getSystemLoadState());
    }

    @Test
    void testMonitorSystemLoad_NoNotificationIfNoStateChange() {
        ConnectorMetric metric = mock(ConnectorMetric.class);
        when(metric.name()).thenReturn("httpConnector");
        when(metric.getConnectorLoadState()).thenReturn(HEALTHY);
        when(connectorsMonitor.getActiveConnectorsMetrics()).thenReturn(Set.of(metric));

        service.monitorSystemLoad();

        verify(listener).onSystemLoadEvent(any());

        // No state change
        service.monitorSystemLoad();

        verifyNoMoreInteractions(listener);
        assertEquals(SystemLoadState.HEALTHY, service.getSystemLoadState());
    }

    @Test
    void testFailToNotifyListener_DoesNotPreventOtherNotifications() {
        ConnectorMetric oldMetric = mock(ConnectorMetric.class);
        ConnectorMetric newMetric = mock(ConnectorMetric.class);

        when(oldMetric.name()).thenReturn("httpConnector");
        when(oldMetric.getConnectorLoadState()).thenReturn(HEALTHY);
        when(newMetric.name()).thenReturn("httpConnector");
        when(newMetric.getConnectorLoadState()).thenReturn(OVERLOADED);

        // Make the listener throw an exception
        lenient().doThrow(new RuntimeException("Listener failed")).when(listener).onSystemLoadEvent(any());

        simulateInitialState(oldMetric);
        long changeTime = 12345L;
        simulateStateChange(changeTime, newMetric);

        service.monitorSystemLoad();

        // Verify that despite the exception, the state was updated
        assertEquals(SystemLoadState.OVERLOADED, service.getSystemLoadState());
    }

    @Test
    void testUpdateSystemLoadStateProbeTimedOutEscalatesToOutage() {
        ConnectorMetric metric = mock(ConnectorMetric.class);
        when(metric.name()).thenReturn("httpConnector");
        when(metric.getConnectorLoadState()).thenReturn(OVERLOADED);

        simulateInitialState(metric);
        assertEquals(SystemLoadState.OVERLOADED, service.getSystemLoadState());

        OutageSelfProbe probe = mock(OutageSelfProbe.class);
        when(probe.getLatestResult()).thenReturn(new ProbeResult(25_000, true, 12345L));
        service.setOutageSelfProbe(probe);

        service.updateSystemLoadState();

        assertEquals(SystemLoadState.OUTAGE, service.getSystemLoadState());
    }

    @Test
    void testUpdateSystemLoadStateProbeTimedOutConnectorsLoadedEscalatesToOutage() {
        ConnectorMetric metric = mock(ConnectorMetric.class);
        when(metric.name()).thenReturn("httpConnector");
        when(metric.getConnectorLoadState()).thenReturn(LOADED);

        simulateInitialState(metric);
        assertEquals(SystemLoadState.LOADED, service.getSystemLoadState());

        OutageSelfProbe probe = mock(OutageSelfProbe.class);
        when(probe.getLatestResult()).thenReturn(new ProbeResult(25_000, true, 12345L));
        service.setOutageSelfProbe(probe);

        service.updateSystemLoadState();

        assertEquals(SystemLoadState.OUTAGE, service.getSystemLoadState());
    }

    @Test
    void testUpdateSystemLoadStateProbeTimedOutConnectorsAlreadyOutageStaysOutage() {
        ConnectorMetric metric = mock(ConnectorMetric.class);
        when(metric.name()).thenReturn("httpConnector");
        when(metric.getConnectorLoadState()).thenReturn(OUTAGE);

        simulateInitialState(metric);

        OutageSelfProbe probe = mock(OutageSelfProbe.class);
        when(probe.getLatestResult()).thenReturn(new ProbeResult(25_000, true, 12345L));
        service.setOutageSelfProbe(probe);

        service.updateSystemLoadState();

        assertEquals(SystemLoadState.OUTAGE, service.getSystemLoadState());
    }

    @Test
    void testUpdateSystemLoadStateProbeSuccessKeepsConnectorDerivedState() {
        ConnectorMetric metric = mock(ConnectorMetric.class);
        when(metric.name()).thenReturn("httpConnector");
        when(metric.getConnectorLoadState()).thenReturn(OVERLOADED);

        simulateInitialState(metric);

        OutageSelfProbe probe = mock(OutageSelfProbe.class);
        when(probe.getLatestResult()).thenReturn(new ProbeResult(150, false, 12345L));
        service.setOutageSelfProbe(probe);

        service.updateSystemLoadState();

        assertEquals(SystemLoadState.OVERLOADED, service.getSystemLoadState());
    }

    @Test
    void testUpdateSystemLoadStateDisabledProbeKeepsConnectorDerivedState() {
        ConnectorMetric metric = mock(ConnectorMetric.class);
        when(metric.name()).thenReturn("httpConnector");
        when(metric.getConnectorLoadState()).thenReturn(OVERLOADED);

        simulateInitialState(metric);

        service.updateSystemLoadState();

        assertEquals(SystemLoadState.OVERLOADED, service.getSystemLoadState());
    }

    @Test
    void testUpdateSystemLoadStateProbeTimedOutConnectorsHealthyEscalatesToOutage() {
        ConnectorMetric metric = mock(ConnectorMetric.class);
        when(metric.name()).thenReturn("httpConnector");
        when(metric.getConnectorLoadState()).thenReturn(HEALTHY);

        simulateInitialState(metric);
        assertEquals(SystemLoadState.HEALTHY, service.getSystemLoadState());

        OutageSelfProbe probe = mock(OutageSelfProbe.class);
        when(probe.getLatestResult()).thenReturn(new ProbeResult(25_000, true, 12345L));
        service.setOutageSelfProbe(probe);

        service.updateSystemLoadState();

        assertEquals(SystemLoadState.OUTAGE, service.getSystemLoadState());
    }

    @Test
    void testUpdateSystemLoadStateProbeRecoveryDeescalatesFromOutage() {
        ConnectorMetric metric = mock(ConnectorMetric.class);
        when(metric.name()).thenReturn("httpConnector");
        when(metric.getConnectorLoadState()).thenReturn(OVERLOADED);

        simulateInitialState(metric);

        OutageSelfProbe probe = mock(OutageSelfProbe.class);
        when(probe.getLatestResult()).thenReturn(new ProbeResult(25_000, true, 12345L));
        service.setOutageSelfProbe(probe);

        service.updateSystemLoadState();
        assertEquals(SystemLoadState.OUTAGE, service.getSystemLoadState());

        when(probe.getLatestResult()).thenReturn(new ProbeResult(100, false, 12346L));

        service.updateSystemLoadState();
        assertEquals(SystemLoadState.OVERLOADED, service.getSystemLoadState());
    }

    @Test
    void testMonitorSystemLoadProbeTimedOutNotifiesEvenWithoutConnectorChange() {
        ConnectorMetric metric = mock(ConnectorMetric.class);
        when(metric.name()).thenReturn("httpConnector");
        when(metric.getConnectorLoadState()).thenReturn(OVERLOADED);

        simulateInitialState(metric);

        long changeTime = 99999L;
        simulateStateChange(changeTime, metric);

        OutageSelfProbe probe = mock(OutageSelfProbe.class);
        when(probe.getLatestResult()).thenReturn(new ProbeResult(25_000, true, changeTime));
        service.setOutageSelfProbe(probe);

        service.monitorSystemLoad();

        verifyChangeEventTriggered(SystemLoadState.OUTAGE, changeTime, listener);
        assertEquals(SystemLoadState.OUTAGE, service.getSystemLoadState());
    }

    @Test
    void testMonitorSystemLoadProbeStillTimedOutDoesNotReNotifyListeners() {
        ConnectorMetric metric = mock(ConnectorMetric.class);
        when(metric.name()).thenReturn("httpConnector");
        when(metric.getConnectorLoadState()).thenReturn(OVERLOADED);

        simulateInitialState(metric);

        long changeTime = 99999L;
        simulateStateChange(changeTime, metric);

        OutageSelfProbe probe = mock(OutageSelfProbe.class);
        when(probe.getLatestResult()).thenReturn(new ProbeResult(25_000, true, changeTime));
        service.setOutageSelfProbe(probe);

        service.monitorSystemLoad();
        verifyChangeEventTriggered(SystemLoadState.OUTAGE, changeTime, listener);

        // Probe still timed out, connectors unchanged — listener must not be notified again.
        service.monitorSystemLoad();
        verifyNoMoreInteractions(listener);
        // Probe must be consulted on every tick — once per monitorSystemLoad() call.
        verify(probe, times(2)).getLatestResult();
        assertEquals(SystemLoadState.OUTAGE, service.getSystemLoadState());
    }

    @Test
    void testDisabledProbeInjected() {
        SystemLoadMonitorServiceImpl freshService =
                new SystemLoadMonitorServiceImpl(connectorsMonitor, taskService, new DisabledOutageSelfProbe());
        freshService.init();

        assertInstanceOf(DisabledOutageSelfProbe.class, freshService.getOutageSelfProbe());
    }

    @Test
    void testDynamicallyRegisterListener_NotifiedOnStateChange() {
        ConnectorMetric oldMetric = mock(ConnectorMetric.class);
        ConnectorMetric newMetric = mock(ConnectorMetric.class);

        when(oldMetric.name()).thenReturn("httpConnector");
        when(oldMetric.getConnectorLoadState()).thenReturn(HEALTHY);
        when(newMetric.name()).thenReturn("httpConnector");
        when(newMetric.getConnectorLoadState()).thenReturn(OVERLOADED);

        simulateInitialState(oldMetric);

        SystemLoadEventListener newListener = mock(SystemLoadEventListener.class);
        service.registerListener(newListener);

        long changeTime = 12345L;
        simulateStateChange(changeTime, newMetric);

        service.monitorSystemLoad();

        verifyChangeEventTriggered(SystemLoadState.OVERLOADED, changeTime, newListener);
        verifyChangeEventTriggered(SystemLoadState.OVERLOADED, changeTime, listener);
        assertEquals(SystemLoadState.OVERLOADED, service.getSystemLoadState());
    }

    private void verifyChangeEventTriggered(SystemLoadState newLoadState, long changeTime,
            SystemLoadEventListener listener) {
        ArgumentCaptor<SystemLoadEventData> captor = ArgumentCaptor.forClass(SystemLoadEventData.class);
        verify(listener).onSystemLoadEvent(captor.capture());
        SystemLoadEventData event = captor.getValue();
        assertEquals(newLoadState, event.systemLoadState());
        assertEquals(changeTime, event.creationTime());
    }

    private void simulateStateChange(long creationTime, ConnectorMetric... newMetrics) {
        when(connectorsMonitor.getActiveConnectorsMetrics()).thenReturn(Set.of(newMetrics));
        service.setTimeSupplier(() -> creationTime);
    }

    private void simulateInitialState(ConnectorMetric... oldMetrics) {
        when(connectorsMonitor.getActiveConnectorsMetrics()).thenReturn(Set.of(oldMetrics));
        service.initConnectorMetrics();
        service.updateSystemLoadState();
    }
}