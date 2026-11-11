package org.artifactory.storage.db.monitor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.util.concurrent.MoreExecutors;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.util.UniversalLimitHandlerUtility;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author  Eli Fishman
 */
@ExtendWith(MockitoExtension.class)
class ExceedingRowsViolationServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private SystemStatesDao systemStatesDao;

    private ExceedingRowsViolationService service;
    private File markerFile;

    @BeforeEach
    void setUp() {
        // Production resolveMarkerFile() returns /storage/dirty.<nodeId>.flag, which is not
        // writable in unit tests. Spy on the service so resolveMarkerFile() returns a
        // path under homeStub.getVarDir() instead. The var dir is cached per test class
        // in ArtifactoryHomeBoundJUnitTest, so the resolved marker file is the same across
        // every @Test in this class — reset it below to keep tests order-independent.
        markerFile = new File(homeStub.getVarDir(), "dirty.test.flag");
        service = spy(new ExceedingRowsViolationService(
                systemStatesDao, MoreExecutors.newDirectExecutorService()));
        // lenient(): only the onStartup_* tests exercise resolveMarkerFile(); the
        // rest of the suite hits persistViolation / cleanup paths that don't touch
        // the spy seam. Strict stubbing would otherwise fail them as "unnecessary".
        lenient().doReturn(markerFile).when(service).resolveMarkerFile();
        if (markerFile.exists() && !markerFile.delete()) {
            throw new IllegalStateException(
                    "Failed to remove leftover marker at " + markerFile.getAbsolutePath());
        }
    }

    @AfterEach
    void resetSystemProperties() {
        homeStub.initArtifactorySystemProperties();
        UniversalLimitHandlerUtility.setOomBlacklist(Set.of());
        if (markerFile != null && markerFile.exists()) {
            markerFile.delete();
        }
    }

    @Test
    void persistViolation_shouldUpsertWithCorrectRouteKeyAndData() throws Exception {
        DbQueryContext context = new DbQueryContext(
                "SELECT * FROM nodes", false, "aql-hint", false,
                "searchAql", "REST", "search", "maven", "admin");

        service.persistViolation(context, 1_500_000);

        ArgumentCaptor<String> routeKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> dataJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(systemStatesDao).upsert(
                eq(ExceedingRowsViolationService.USE_CASE),
                routeKeyCaptor.capture(),
                anyLong(),
                anyString(),
                eq("admin"),
                dataJsonCaptor.capture());

        assertEquals("searchAql", routeKeyCaptor.getValue());

        ExceedingRowsViolationRecord record = JsonUtils.getInstance()
                .readValue(dataJsonCaptor.getValue(), ExceedingRowsViolationRecord.class);
        assertEquals("SELECT * FROM nodes", record.query());
        assertEquals(1_500_000, record.exceedingRowsCount());
        assertEquals(ConstantValues.queryMonitorExceedingRowsThreshold.getLong(), record.threshold());
        assertEquals("searchAql", record.actionName());
        assertEquals("REST", record.actionKind());
        assertEquals("search", record.actionGroup());
        assertEquals("maven", record.actionPackage());
        assertEquals("admin", record.actionActor());
        assertEquals("aql-hint", record.hint());
        assertEquals("searchAql", record.routeKey());
    }

    @Test
    void persistViolation_shouldNotThrow_whenDaoFails() throws Exception {
        DbQueryContext context = new DbQueryContext(
                "SELECT 1", false, null, false,
                "test", "JOB", "gc", null, "system");
        doThrow(new SQLException("DB error"))
                .when(systemStatesDao).upsert(anyString(), anyString(), anyLong(),
                        anyString(), anyString(), anyString());

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Logger logger = (Logger) LoggerFactory.getLogger(
                ExceedingRowsViolationService.class);
        Level originalLevel = logger.getLevel();
        try {
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.DEBUG);

            service.persistViolation(context, 2_000_000);

            List<ILoggingEvent> warnEvents = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN)
                    .toList();
            assertEquals(1, warnEvents.size());
            assertTrue(warnEvents.get(0).getFormattedMessage()
                    .contains("DB error"));

            List<ILoggingEvent> debugEvents = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.DEBUG)
                    .toList();
            assertEquals(1, debugEvents.size());
            assertTrue(debugEvents.get(0).getFormattedMessage()
                    .contains("Persistence failure details"));
        } finally {
            logger.setLevel(originalLevel);
            logger.detachAppender(appender);
        }
    }

    @Test
    void cleanupOldViolations_shouldDeleteWithRetentionPeriod() throws Exception {
        setStringSystemProperty(
                ConstantValues.queryMonitorExceedingRowsViolationRetentionDays, "7");
        when(systemStatesDao.deleteByUseCaseOlderThan(anyString(), anyLong())).thenReturn(3);

        int deleted = service.cleanupOldViolations();

        assertEquals(3, deleted);
        verify(systemStatesDao).deleteByUseCaseOlderThan(
                eq(ExceedingRowsViolationService.USE_CASE), anyLong());
    }

    @Test
    void persistViolationAsync_shouldDelegateToExecutor() throws Exception {
        DbQueryContext context = new DbQueryContext(
                "SELECT * FROM test", false, "hint", false,
                "action", "REST", "group", "npm", "user");

        service.persistViolationAsync(context, 1_100_000);

        verify(systemStatesDao).upsert(anyString(), anyString(), anyLong(),
                anyString(), anyString(), anyString());
    }

    @Test
    void persistViolation_shouldUpsertWithSameRouteKey_whenCalledTwice() throws Exception {
        // The DAO-level INSERT-then-UPDATE behaviour for the same key is covered by
        // SystemStatesDaoTest#upsert_overwritesExistingRow_whenSameKey.
        // Here we verify that the service issues two ordered upsert calls, both targeting
        // the same route key but carrying distinct exceedingRowsCount payloads.
        DbQueryContext context = new DbQueryContext(
                "SELECT 1", false, null, false,
                "action", "REST", "group", null, "user");

        service.persistViolation(context, 1_000_001);
        service.persistViolation(context, 1_000_002);

        ArgumentCaptor<String> routeKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> dataJsonCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.InOrder ordered = inOrder(systemStatesDao);
        ordered.verify(systemStatesDao).upsert(
                eq(ExceedingRowsViolationService.USE_CASE),
                routeKeyCaptor.capture(),
                anyLong(), anyString(), anyString(), dataJsonCaptor.capture());
        ordered.verify(systemStatesDao).upsert(
                eq(ExceedingRowsViolationService.USE_CASE),
                routeKeyCaptor.capture(),
                anyLong(), anyString(), anyString(), dataJsonCaptor.capture());

        List<String> capturedRouteKeys = routeKeyCaptor.getAllValues();
        assertEquals(capturedRouteKeys.get(0), capturedRouteKeys.get(1),
                "Both calls must upsert the same route key — one row per route");

        List<String> dataJsons = dataJsonCaptor.getAllValues();
        ExceedingRowsViolationRecord first = JsonUtils.getInstance()
                .readValue(dataJsons.get(0), ExceedingRowsViolationRecord.class);
        ExceedingRowsViolationRecord second = JsonUtils.getInstance()
                .readValue(dataJsons.get(1), ExceedingRowsViolationRecord.class);
        assertEquals(1_000_001, first.exceedingRowsCount(), "First call carries initial row count");
        assertEquals(1_000_002, second.exceedingRowsCount(), "Second call carries updated row count");
    }

    @Test
    void onStartup_shouldSetBlacklist_whenPreviousRunWasDirty() throws Exception {
        setStringSystemProperty(
                ConstantValues.queryMonitorExceedingRowsViolationEnableRemedy, "TRUE");

        createMarkerFile();
        when(systemStatesDao.findByUseCaseSince(
                eq(ExceedingRowsViolationService.USE_CASE), anyLong()))
                .thenReturn(List.of(
                        new SystemStateRecord(ExceedingRowsViolationService.USE_CASE,
                                "searchAql", System.currentTimeMillis(), "node-1", "admin", "{}")));

        service.onStartup();

        Collection<String> blacklist = UniversalLimitHandlerUtility.getEffectiveBlocklist();
        assertEquals(1, blacklist.size());
        assertTrue(blacklist.contains("searchAql"));
        // The marker is kept for the current run (not consumed by onStartup).
        // It is deleted only when shutdown() signals a clean shutdown.
        assertTrue(markerFile.exists(),
                "Dirty-shutdown marker must remain in place for the current run");

        service.shutdown();
        assertFalse(markerFile.exists(),
                "Dirty-shutdown marker must be deleted on graceful shutdown");
    }

    @Test
    void onStartup_shouldCreateMarker_andSkipBlacklist_whenPreviousRunWasClean()
            throws Exception {
        setStringSystemProperty(
                ConstantValues.queryMonitorExceedingRowsViolationEnableRemedy, "TRUE");

        assertFalse(markerFile.exists(), "No marker before first startup");

        service.onStartup();

        verify(systemStatesDao, never())
                .findByUseCaseSince(anyString(), anyLong());
        assertTrue(UniversalLimitHandlerUtility.getEffectiveBlocklist().isEmpty());
        assertTrue(markerFile.exists(),
                "onStartup() must create the dirty-shutdown marker for the current run");

        service.shutdown();
        assertFalse(markerFile.exists(),
                "Dirty-shutdown marker must be deleted on graceful shutdown");
    }

    @Test
    void onStartup_shouldLoadViolationsFromAllNodes() throws Exception {
        setStringSystemProperty(
                ConstantValues.queryMonitorExceedingRowsViolationEnableRemedy, "TRUE");

        createMarkerFile();
        when(systemStatesDao.findByUseCaseSince(
                eq(ExceedingRowsViolationService.USE_CASE), anyLong()))
                .thenReturn(List.of(
                        new SystemStateRecord(ExceedingRowsViolationService.USE_CASE,
                                "search/aql", System.currentTimeMillis(), "node-1", "user1", "{}"),
                        new SystemStateRecord(ExceedingRowsViolationService.USE_CASE,
                                "download npm", System.currentTimeMillis(), "node-2", "user2", "{}")));

        service.onStartup();

        Collection<String> blacklist = UniversalLimitHandlerUtility.getEffectiveBlocklist();
        assertEquals(2, blacklist.size(),
                "Violations from all nodes must be loaded — no per-node filtering");
        assertTrue(blacklist.contains("search/aql"));
        assertTrue(blacklist.contains("download npm"));
        assertTrue(markerFile.exists(),
                "Dirty-shutdown marker must remain in place for the current run");
    }

    @Test
    void onStartup_shouldUseExactRouteKey_forNpmDownloadViolation() throws Exception {
        setStringSystemProperty(
                ConstantValues.queryMonitorExceedingRowsViolationEnableRemedy, "TRUE");

        createMarkerFile();
        when(systemStatesDao.findByUseCaseSince(
                eq(ExceedingRowsViolationService.USE_CASE), anyLong()))
                .thenReturn(List.of(
                        new SystemStateRecord(ExceedingRowsViolationService.USE_CASE,
                                "download npm", System.currentTimeMillis(), "node-1", "user", "{}")));

        service.onStartup();

        Collection<String> blacklist = UniversalLimitHandlerUtility.getEffectiveBlocklist();
        assertEquals(1, blacklist.size());
        assertTrue(blacklist.contains("download npm"),
                "Route key must be used directly as the exact blocklist pattern");

        service.shutdown();
    }

    @Test
    void onStartup_shouldSkipRecord_whenRouteKeyIsAbsent() throws Exception {
        setStringSystemProperty(
                ConstantValues.queryMonitorExceedingRowsViolationEnableRemedy, "TRUE");

        createMarkerFile();
        when(systemStatesDao.findByUseCaseSince(
                eq(ExceedingRowsViolationService.USE_CASE), anyLong()))
                .thenReturn(List.of(
                        new SystemStateRecord(ExceedingRowsViolationService.USE_CASE,
                                "", System.currentTimeMillis(), "node-1", null, "{}")));

        service.onStartup();

        assertTrue(UniversalLimitHandlerUtility.getEffectiveBlocklist().isEmpty(),
                "Records with a blank id must be skipped");

        service.shutdown();
    }

    @Test
    void onStartup_shouldDoNothing_whenRemedyFeatureDisabled() throws Exception {
        setStringSystemProperty(
                ConstantValues.queryMonitorExceedingRowsViolationEnableRemedy, "false");

        service.onStartup();

        verify(systemStatesDao, never())
                .findByUseCaseSince(anyString(), anyLong());
        assertTrue(UniversalLimitHandlerUtility.getEffectiveBlocklist().isEmpty());
        assertFalse(markerFile.exists(),
                "No marker should be created when the remedy feature is disabled");
    }

    private File createMarkerFile() throws IOException {
        markerFile.getParentFile().mkdirs();
        assertTrue(markerFile.createNewFile() || markerFile.exists());
        return markerFile;
    }
}
