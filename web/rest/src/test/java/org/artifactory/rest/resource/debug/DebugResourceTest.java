package org.artifactory.rest.resource.debug;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.debug.DebugLevelConfig;
import org.artifactory.debug.DebugService;
import org.artifactory.debug.DebugServiceImpl;
import org.artifactory.debug.threads.ThreadDumpResponse;
import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.annotations.DataProvider;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Map;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.artifactory.rest.resource.debug.DebugResource.DELETE_LOG_LEVEL_MESSAGE;
import static org.artifactory.rest.resource.debug.DebugResource.UPDATE_LOG_LEVEL_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Amir Avidan
 */
class DebugResourceTest {

    @Mock
    private DebugService debugService;
    @Mock
    private AolService aolService;
    @Mock
    private ArtifactoryContext context;
    @InjectMocks
    private DebugResource debugResource;
    private AutoCloseable mocks;
    private static final String jbcClassName = "org.jfrog.storage.JdbcHelper";

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        when((aolService.isAol())).thenReturn(false);
        when(aolService.isDashboardUser()).thenReturn(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void getLogLevel() {
        when(debugService.getLogLevelFromClass(jbcClassName)).thenReturn("INFO");
        try (Response response = debugResource.getDebugLevel(jbcClassName)) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(response.getEntity().toString(), "INFO");
            verify(debugService).getLogLevelFromClass(jbcClassName);
        }
    }

    @Test
    void getLogLevelToNonExistingClass() {
        when(debugService.getLogLevelFromClass("Blala")).thenReturn("WARN");
        try (Response response = debugResource.getDebugLevel("Blala")) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(response.getEntity().toString(), "WARN");
            verify(debugService).getLogLevelFromClass("Blala");
        }
    }

    @Test
    void getWithAolOnSetDebugLevels() {
        when(aolService.isAol()).thenReturn(true);
        when((aolService.isDashboardUser())).thenReturn(false);
        assertThrows(BadRequestException.class, () -> debugResource.setDebugLevels(any()));
        verify(debugService, times(0)).changeLogLevel(any());
    }

    @Test
    void getWithAolGetDebugLevel() {
        when(aolService.isAol()).thenReturn(true);
        when((aolService.isDashboardUser())).thenReturn(false);
        assertThrows(BadRequestException.class, () -> debugResource.getDebugLevel(any()));
        verify(debugService, times(0)).getLogLevelFromClass(any());
    }

    @Test
    void getWithAolDeleteDebugLevel() {
        when(aolService.isAol()).thenReturn(true);
        when((aolService.isDashboardUser())).thenReturn(false);
        assertThrows(BadRequestException.class, () -> debugResource.deleteDebugLevel());
        verify(debugService, times(0)).restoreDebugSettings();
    }

    @Test
    void setLevelForClass() {
        DebugLevelConfig debugLevelConfig = new DebugLevelConfig(Map.of(jbcClassName, Level.INFO), 5);
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(SC_OK);
        when(mockResponse.getEntity()).thenReturn(UPDATE_LOG_LEVEL_MESSAGE);
        try (Response response = debugResource.setDebugLevels(debugLevelConfig)) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(UPDATE_LOG_LEVEL_MESSAGE, response.getEntity().toString());
            verify(debugService).changeLogLevel(debugLevelConfig);
        }
    }

    @Test
    void deleteRequestTest() {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(SC_OK);
        when(mockResponse.getEntity()).thenReturn(DELETE_LOG_LEVEL_MESSAGE);
        try (Response response = debugResource.deleteDebugLevel()) {
            assertEquals(SC_OK, response.getStatus());
            assertEquals(DELETE_LOG_LEVEL_MESSAGE, response.getEntity().toString());
            verify(debugService).restoreDebugSettings();
        }
    }

    @ParameterizedTest
    @MethodSource("booleans")
    void testThreadDumpsFromHaSystems(boolean propagatePods) throws JsonProcessingException {
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        if (propagatePods) {
            HaAddon mockedHaAddon = Mockito.mock(HaAddon.class);
            when(mockedHaAddon.propagateThreadDumps()).thenReturn(new ArrayList<>());
            when(addonsManager.addonByType(HaAddon.class)).thenReturn(mockedHaAddon);
        }

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        AolService aolService = Mockito.mock(AolService.class);
        when(aolService.isAol()).thenReturn(false);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(context);

        DebugService debugServiceImpl = new DebugServiceImpl(addonsManager);
        DebugResource resource = new DebugResource(debugServiceImpl);
        Response threadDumps = resource.getThreadDumps(propagatePods);
        String entity = (String) threadDumps.getEntity();

        ObjectMapper objectMapper = new ObjectMapper();
        entity = entity.replace("\n", "\\n");
        ThreadDumpResponse threadDumpResponse = objectMapper.readValue(entity, ThreadDumpResponse.class);
        assertEquals(1, threadDumpResponse.getThreadDumps().size());
    }

    @DataProvider
    public static Object[][] booleans() {
        return new Object[][] {
                {true},
                {false}
        };
    }
}
