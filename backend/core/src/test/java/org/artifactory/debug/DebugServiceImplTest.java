package org.artifactory.debug;

import ch.qos.logback.classic.Level;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Amir Avidan
 */
class DebugServiceImplTest {

    @Mock
    private HaAddon haAddon;
    @Mock
    private ArtifactoryApplicationContext context;
    @Mock
    private AddonsManager addonsManager;
    @InjectMocks
    private DebugServiceImpl debugService;
    private final String trafficLogClassName = "org.artifactory.traffic.TrafficLogger";
    private AutoCloseable mocks;
    private Field oldConfigState;
    private Field restoreLoggersTask;

    @BeforeEach
    void setUp() throws NoSuchFieldException {
        mocks = openMocks(this);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        ArtifactoryContextThreadBinder.bind(context);
        enableAccessToPrivateFields();
    }

    private void enableAccessToPrivateFields() throws NoSuchFieldException {
        oldConfigState = debugService.getClass().getDeclaredField("oldConfigState");
        oldConfigState.setAccessible(true);
        restoreLoggersTask = debugService.getClass().getDeclaredField("restoreLoggersTask");
        restoreLoggersTask.setAccessible(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        debugService.restoreDebugSettings();
        oldConfigState.setAccessible(false);
        restoreLoggersTask.setAccessible(false);
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void getLogLevel() {
        assertEquals(debugService.getLogLevelFromClass(trafficLogClassName), Level.WARN.toString());
    }

    @Test
    void testChangeDebugLevel() {
        DebugLevelConfig debugLevelChange = new DebugLevelConfig(Map.of(trafficLogClassName, Level.INFO), 1);
        debugService.changeLogLevel(debugLevelChange);
        assertEquals(debugService.getLogLevelFromClass(trafficLogClassName),
                Level.INFO.toString());
    }

    @Test
    void deleteDebugSettings() {
        assertEquals(debugService.getLogLevelFromClass(trafficLogClassName), Level.WARN.toString());
        DebugLevelConfig debugLevelChange = new DebugLevelConfig(Map.of(trafficLogClassName, Level.TRACE), 10);
        debugService.changeLogLevel(debugLevelChange);
        assertEquals(debugService.getLogLevelFromClass(trafficLogClassName),
                Level.TRACE.toString());
        debugService.restoreDebugSettings();
        assertEquals(debugService.getLogLevelFromClass(trafficLogClassName),
                Level.WARN.toString());
    }

    @Test
    void testPropagateSetDebugLoggers() throws IllegalAccessException {
        DebugLevelConfig debugLevelConfig =
                new DebugLevelConfig(Map.of(trafficLogClassName, Level.DEBUG), 50);
        debugService.propagateLoggersSettings(debugLevelConfig);
        assertEquals(Level.DEBUG.toString(),
                debugService.getLogLevelFromClass(trafficLogClassName));
        debugService.propagateLoggersSettings(DebugLevelConfig.EMPTY);
        assertEquals(new HashMap<>(), oldConfigState.get(debugService));
    }

    @Test
    void restoreOperationNotExecutedWhenRestoreLoggersTaskIsNull() throws IllegalAccessException {
        debugService.restoreDebugSettings();
        assertNull(restoreLoggersTask.get(debugService));
    }

    @ParameterizedTest
    @MethodSource("createWrongInput")
    void testAssertInputConfig(DebugLevelConfig debugLevelConfig) {
        assertThrows(BadRequestException.class, () -> debugService.changeLogLevel(debugLevelConfig));
    }

    private static Stream<Arguments> createWrongInput() {
        Map<String, Level> mockMap = mock(Map.class);
        DebugLevelConfig debugLevelConfig = mock(DebugLevelConfig.class);
        when(debugLevelConfig.classes()).thenReturn(mockMap);
        when(debugLevelConfig.timer()).thenReturn(5);
        when(mockMap.size()).thenReturn(101);
        return Stream.of(
                Arguments.of(new DebugLevelConfig(null, 0)),
                Arguments.of(new DebugLevelConfig(Map.of("blala", Level.INFO), -1)),
                Arguments.of(debugLevelConfig));
    }

    @Test
    void testTryToDeleteWhenTaskStarted() throws IllegalAccessException {
        ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        when(scheduledFuture.cancel(false)).thenReturn(false);
        restoreLoggersTask.set(debugService, scheduledFuture);
        debugService.restoreDebugSettings();
        verify(scheduledFuture, times(1)).cancel(false);
    }
}
