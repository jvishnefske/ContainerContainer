/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.rest.services.devopsagent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.exception.http.NotImplementedException;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogEntry;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogEventsResponse;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogSearchResponse;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogTemplateDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.entity.RawEventRow;
import org.artifactory.storage.db.devopsagent.errorcatalog.entity.TemplateRow;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.AgenticSearchCriteria;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.KeysetCursor;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer unit tests for {@link ErrorCatalogRestServiceImpl}. Covers parameter parsing,
 * validation (including the stable {@code 'paramName'} prefix convention from api-documentation
 * v2 § 6.3), silent clamping, feature-flag and DB-type guards, hasMore detection, DTO mapping,
 * and outcome-tagged counter emission.
 */
public class ErrorCatalogRestServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private ErrorCatalogTemplateDao templatesDao;
    private ErrorCatalogDao rawDao;
    private ArtifactoryDbProperties dbProperties;
    private SimpleMeterRegistry meterRegistry;
    private ErrorCatalogRestServiceImpl service;

    @BeforeEach
    public void setUp() {
        templatesDao = mock(ErrorCatalogTemplateDao.class);
        rawDao = mock(ErrorCatalogDao.class);
        dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        meterRegistry = new SimpleMeterRegistry();
        service = new ErrorCatalogRestServiceImpl(templatesDao, rawDao, dbProperties, meterRegistry);
        service.registerCounters();
        // Enable the feature for all tests by default; individual tests that verify the disabled
        // path override this via setStringSystemProperty(..., "false").
        setStringSystemProperty(ConstantValues.devopsTroubleshootingEnabled, "true");
    }

    private static ErrorCatalogSearchRequest defaults() {
        return new ErrorCatalogSearchRequest(null, null, null, null,
                null, null, null, null, null, null, null, false, 1000, 0);
    }

    private static ErrorCatalogSearchRequest withLimit(int limit) {
        return new ErrorCatalogSearchRequest(null, null, null, null,
                null, null, null, null, null, null, null, false, limit, 0);
    }

    private static ErrorCatalogSearchRequest withOffset(int offset) {
        return new ErrorCatalogSearchRequest(null, null, null, null,
                null, null, null, null, null, null, null, false, 1000, offset);
    }

    private static TemplateRow row(long id, String severity) {
        return new TemplateRow(id, "h" + id, severity, "c.A", null, "msg",
                1L, 2L, 3L, 4L, 5L);
    }

    private double counterCount(String outcome) {
        return meterRegistry.counter("error_catalog_api.search_total", "outcome", outcome).count();
    }

    @Test
    public void happyPathReturnsRowsAndIncrementsOkCounter() {
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt()))
                .thenReturn(List.of(row(1, "ERROR"), row(2, "WARN")));

        ErrorCatalogSearchResponse response = service.search(defaults());

        assertEquals(2, response.logs().size());
        assertFalse(response.hasMore());
        assertEquals(1L, response.logs().get(0).id());
        assertEquals(1.0, counterCount("ok"));
        assertEquals(0.0, counterCount("bad_request"));
    }

    @Test
    public void searchTemplatesCalledWithLimitPlusOne() {
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt())).thenReturn(List.of());

        service.search(withLimit(50));

        verify(templatesDao).searchTemplates(any(AgenticSearchCriteria.class), eq(51), eq(0));
    }

    @Test
    public void hasMoreTrueWhenDaoReturnsLimitPlusOneRows() {
        // limit=2 → DAO asked for 3 → return 3 → hasMore=true → response carries first 2 only.
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt()))
                .thenReturn(List.of(row(1, "ERROR"), row(2, "ERROR"), row(3, "ERROR")));

        ErrorCatalogSearchResponse response = service.search(withLimit(2));

        assertTrue(response.hasMore());
        assertEquals(2, response.logs().size());
    }

    @Test
    public void rowMappingDropsCreatedAtAndPreservesNullExceptionClass() {
        TemplateRow source = new TemplateRow(11L, "h11", "ERROR", "c.A", null, "msg",
                10L, 20L, 30L, 40L, 50L);
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt())).thenReturn(List.of(source));

        ErrorCatalogEntry entry = service.search(defaults()).logs().get(0);

        assertEquals(11L, entry.id());
        assertEquals("h11", entry.signatureHash());
        assertEquals("ERROR", entry.severity());
        assertEquals("c.A", entry.loggerClass());
        assertNull(entry.exceptionClass());
        assertEquals(10L, entry.firstOccurrenceAt());
        assertEquals(20L, entry.lastOccurrenceAt());
        assertEquals(30L, entry.occurrenceCount());
    }

    // ============================================================
    // Validation: every BadRequest message must begin with the
    // documented single-quoted parameter-name prefix (api-doc v2 § 6.3).
    // ============================================================

    @Test
    public void invalidSeverity400StartsWithSeverityPrefix() {
        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(null, "INFO", null, null,
                null, null, null, null, null, null, null, false, 1000, 0);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.search(req));
        assertTrue(ex.getMessage().startsWith("'severity'"), "Got: " + ex.getMessage());
        assertEquals(1.0, counterCount("bad_request"));
    }

    @Test
    public void idListOversize400StartsWithIdPrefix() {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                csv.append(",");
            }
            csv.append(i);
        }
        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(csv.toString(), null, null, null,
                null, null, null, null, null, null, null, false, 1000, 0);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.search(req));
        assertTrue(ex.getMessage().startsWith("'id'"), "Got: " + ex.getMessage());
    }

    @Test
    public void idNonNumeric400StartsWithIdPrefix() {
        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest("1,abc,3", null, null, null,
                null, null, null, null, null, null, null, false, 1000, 0);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.search(req));
        assertTrue(ex.getMessage().startsWith("'id'"), "Got: " + ex.getMessage());
    }

    @Test
    public void severityListOversize400StartsWithSeverityPrefix() {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                csv.append(",");
            }
            csv.append("WARN");
        }
        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(null, csv.toString(), null, null,
                null, null, null, null, null, null, null, false, 1000, 0);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.search(req));
        assertTrue(ex.getMessage().startsWith("'severity'"), "Got: " + ex.getMessage());
    }

    @Test
    public void loggerClassListOversize400StartsWithLoggerClassPrefix() {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                csv.append(",");
            }
            csv.append("c.A").append(i);
        }
        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(null, null, csv.toString(), null,
                null, null, null, null, null, null, null, false, 1000, 0);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.search(req));
        assertTrue(ex.getMessage().startsWith("'logger_class'"), "Got: " + ex.getMessage());
    }

    @Test
    public void exceptionClassListOversize400StartsWithExceptionClassPrefix() {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                csv.append(",");
            }
            csv.append("c.E").append(i);
        }
        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(null, null, null, csv.toString(),
                null, null, null, null, null, null, null, false, 1000, 0);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.search(req));
        assertTrue(ex.getMessage().startsWith("'exception_class'"), "Got: " + ex.getMessage());
    }

    @Test
    public void occurrenceCountFromGreaterThanTo400StartsWithFromPrefix() {
        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(null, null, null, null,
                100L, 50L, null, null, null, null, null, false, 1000, 0);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.search(req));
        assertTrue(ex.getMessage().startsWith("'occurrence_count_from'"), "Got: " + ex.getMessage());
    }

    @Test
    public void firstOccurrenceFromGreaterThanTo400StartsWithFromPrefix() {
        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(null, null, null, null,
                null, null, 200L, 100L, null, null, null, false, 1000, 0);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.search(req));
        assertTrue(ex.getMessage().startsWith("'first_occurrence_from'"), "Got: " + ex.getMessage());
    }

    @Test
    public void lastOccurrenceFromGreaterThanTo400StartsWithFromPrefix() {
        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(null, null, null, null,
                null, null, null, null, 500L, 100L, null, false, 1000, 0);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.search(req));
        assertTrue(ex.getMessage().startsWith("'last_occurrence_from'"), "Got: " + ex.getMessage());
    }

    @Test
    public void zeroLimit400StartsWithLimitPrefix() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.search(withLimit(0)));
        assertTrue(ex.getMessage().startsWith("'limit'"), "Got: " + ex.getMessage());
    }

    @Test
    public void negativeOffset400StartsWithOffsetPrefix() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.search(withOffset(-1)));
        assertTrue(ex.getMessage().startsWith("'offset'"), "Got: " + ex.getMessage());
    }

    // ============================================================
    // Silent clamping above the ceilings — positive paths.
    // ============================================================

    @Test
    public void limitAboveCeilingSilentlyClampedToMaxPlusOne() {
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt())).thenReturn(List.of());
        service.search(withLimit(5000));
        verify(templatesDao).searchTemplates(any(AgenticSearchCriteria.class), eq(2001), eq(0));
    }

    @Test
    public void offsetAboveCeilingSilentlyClampedTo10000() {
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt())).thenReturn(List.of());
        service.search(withOffset(999_999));
        verify(templatesDao).searchTemplates(any(AgenticSearchCriteria.class), eq(1001), eq(10_000));
    }

    // ============================================================
    // Search tokenisation: tsquery built only from non-meta tokens.
    // ============================================================

    @Test
    public void searchAllStrippedTokensTreatedAsAbsent() {
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt())).thenReturn(List.of());

        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(null, null, null, null,
                null, null, null, null, null, null, "& | !", false, 1000, 0);
        service.search(req);

        ArgumentCaptor<AgenticSearchCriteria> captor = ArgumentCaptor.forClass(AgenticSearchCriteria.class);
        verify(templatesDao).searchTemplates(captor.capture(), anyInt(), anyInt());
        assertNull(captor.getValue().tsqueryString(), "All-stripped search must produce a null tsqueryString");
    }

    @Test
    public void searchAndModeJoinsTokensWithAmpersand() {
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt())).thenReturn(List.of());

        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(null, null, null, null,
                null, null, null, null, null, null, "checksum mismatch", true, 1000, 0);
        service.search(req);

        ArgumentCaptor<AgenticSearchCriteria> captor = ArgumentCaptor.forClass(AgenticSearchCriteria.class);
        verify(templatesDao).searchTemplates(captor.capture(), anyInt(), anyInt());
        assertEquals("checksum & mismatch", captor.getValue().tsqueryString());
    }

    @Test
    public void searchOrModeJoinsTokensWithPipe() {
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt())).thenReturn(List.of());

        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(null, null, null, null,
                null, null, null, null, null, null, "checksum mismatch", false, 1000, 0);
        service.search(req);

        ArgumentCaptor<AgenticSearchCriteria> captor = ArgumentCaptor.forClass(AgenticSearchCriteria.class);
        verify(templatesDao).searchTemplates(captor.capture(), anyInt(), anyInt());
        assertEquals("checksum | mismatch", captor.getValue().tsqueryString());
    }

    // ============================================================
    // 404 (flag-off) and 501 (non-PG) paths increment their own counters.
    // ============================================================

    @Test
    public void featureFlagOffThrowsNotFoundAndIncrementsCounter() {
        setStringSystemProperty(ConstantValues.devopsTroubleshootingEnabled, "false");

        NotFoundException ex = assertThrows(NotFoundException.class, () -> service.search(defaults()));
        assertEquals("Error/warning logs API is disabled on this instance", ex.getMessage());
        assertEquals(1.0, counterCount("not_found"));
    }

    @Test
    public void nonPostgresThrowsNotImplementedAndIncrementsCounter() {
        when(dbProperties.getDbType()).thenReturn(DbType.DERBY);

        NotImplementedException ex = assertThrows(NotImplementedException.class, () -> service.search(defaults()));
        assertEquals("Error/warning logs API is available for PostgreSQL only", ex.getMessage());
        assertEquals(1.0, counterCount("not_implemented"));
    }

    // ============================================================
    // Per-field criteria propagation — assert the DAO sees what the spec says.
    // ============================================================

    @Test
    public void parsedListsAndRangesPropagateToDaoCriteria() {
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt())).thenReturn(List.of());

        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(
                "1,2,3", "ERROR,WARN", "c.A,c.B", "java.io.IOException",
                5L, 100L, 10L, 20L, 30L, 40L, null, false, 1000, 0);
        service.search(req);

        ArgumentCaptor<AgenticSearchCriteria> captor = ArgumentCaptor.forClass(AgenticSearchCriteria.class);
        verify(templatesDao).searchTemplates(captor.capture(), anyInt(), anyInt());
        AgenticSearchCriteria criteria = captor.getValue();
        assertEquals(List.of(1L, 2L, 3L), criteria.ids());
        assertEquals(List.of("ERROR", "WARN"), criteria.severities());
        assertEquals(List.of("c.A", "c.B"), criteria.loggerClasses());
        assertEquals(List.of("java.io.IOException"), criteria.exceptionClasses());
        assertEquals(5L, criteria.occurrenceCountFrom());
        assertEquals(100L, criteria.occurrenceCountTo());
        assertEquals(10L, criteria.firstOccurrenceFrom());
        assertEquals(20L, criteria.firstOccurrenceTo());
        assertEquals(30L, criteria.lastOccurrenceFrom());
        assertEquals(40L, criteria.lastOccurrenceTo());
        assertNotNull(criteria);
    }

    @Test
    public void emptyAndBlankCsvTokensAreFiltered() {
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt())).thenReturn(List.of());

        ErrorCatalogSearchRequest req = new ErrorCatalogSearchRequest(
                " 1 , 2 , , 3 ", "WARN, ERROR ,", null, null,
                null, null, null, null, null, null, null, false, 1000, 0);
        service.search(req);

        ArgumentCaptor<AgenticSearchCriteria> captor = ArgumentCaptor.forClass(AgenticSearchCriteria.class);
        verify(templatesDao).searchTemplates(captor.capture(), anyInt(), anyInt());
        AgenticSearchCriteria criteria = captor.getValue();
        assertEquals(List.of(1L, 2L, 3L), criteria.ids());
        assertEquals(List.of("WARN", "ERROR"), criteria.severities());
    }

    // ============================================================
    // Static utility coverage — concise checks on the validation helpers.
    // ============================================================

    @Test
    public void validateAndClampLimitClampsAndRejects() {
        assertEquals(1000, ErrorCatalogRestServiceImpl.validateAndClampLimit(1000));
        assertEquals(2000, ErrorCatalogRestServiceImpl.validateAndClampLimit(2000));
        assertEquals(2000, ErrorCatalogRestServiceImpl.validateAndClampLimit(5000));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> ErrorCatalogRestServiceImpl.validateAndClampLimit(0));
        assertTrue(ex.getMessage().startsWith("'limit'"));
    }

    @Test
    public void validateAndClampOffsetClampsAndRejects() {
        assertEquals(0, ErrorCatalogRestServiceImpl.validateAndClampOffset(0));
        assertEquals(10_000, ErrorCatalogRestServiceImpl.validateAndClampOffset(10_000));
        assertEquals(10_000, ErrorCatalogRestServiceImpl.validateAndClampOffset(999_999));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> ErrorCatalogRestServiceImpl.validateAndClampOffset(-1));
        assertTrue(ex.getMessage().startsWith("'offset'"));
    }

    @Test
    public void buildTsqueryHandlesNullBlankAndAllStripped() {
        assertNull(ErrorCatalogRestServiceImpl.buildTsquery(null, false));
        assertNull(ErrorCatalogRestServiceImpl.buildTsquery("   ", false));
        assertNull(ErrorCatalogRestServiceImpl.buildTsquery("& | ( ) :", true));
        assertEquals("foo & bar", ErrorCatalogRestServiceImpl.buildTsquery("foo bar", true));
        assertEquals("foo | bar", ErrorCatalogRestServiceImpl.buildTsquery("foo bar", false));
        // Strip metacharacters but keep the remainder of the token.
        assertEquals("foo & bar", ErrorCatalogRestServiceImpl.buildTsquery("f(o)o b!a*r", true));
    }

    @Test
    public void allowedSeveritiesIsExactlyWarnAndError() {
        assertEquals(2, ErrorCatalogRestServiceImpl.allowedSeverities().size());
        assertTrue(ErrorCatalogRestServiceImpl.allowedSeverities().contains("WARN"));
        assertTrue(ErrorCatalogRestServiceImpl.allowedSeverities().contains("ERROR"));
    }

    @Test
    public void tsqueryMetacharactersAreTheDocumentedSet() {
        char[] meta = ErrorCatalogRestServiceImpl.tsqueryMetacharacters();
        List<Character> list = new ArrayList<>();
        for (char c : meta) {
            list.add(c);
        }
        assertTrue(list.contains('&'));
        assertTrue(list.contains('|'));
        assertTrue(list.contains('!'));
        assertTrue(list.contains('('));
        assertTrue(list.contains(')'));
        assertTrue(list.contains(':'));
        assertTrue(list.contains('*'));
        assertTrue(list.contains('<'));
        assertTrue(list.contains('>'));
    }

    // ============================================================
    // Raw events drill-down: fetchEvents (RTDEV-86991)
    // ============================================================

    private static ErrorCatalogEventsRequest eventsRequest(String templateIdsCsv) {
        return new ErrorCatalogEventsRequest(templateIdsCsv, null, null, null, 100);
    }

    private static ErrorCatalogEventsRequest eventsRequestWithLimit(String templateIdsCsv, int limit) {
        return new ErrorCatalogEventsRequest(templateIdsCsv, null, null, null, limit);
    }

    private static RawEventRow rawRow(long id, long templateId, long occurredAt) {
        return new RawEventRow(id, templateId, occurredAt, "msg-" + id);
    }

    private double eventsCounterCount(String outcome) {
        return meterRegistry.counter("error_catalog_api.fetch_events_total", "outcome", outcome).count();
    }

    @Test
    public void fetchEventsHappyPathReturnsRowsAndIncrementsOkCounter() {
        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(rawRow(11L, 42L, 300L), rawRow(10L, 42L, 200L), rawRow(9L, 42L, 100L)));

        ErrorCatalogEventsResponse response = service.fetchEvents(eventsRequest("42"));

        assertEquals(3, response.events().size());
        assertEquals(11L, response.events().get(0).id());
        assertEquals(42L, response.events().get(0).templateId());
        assertEquals(300L, response.events().get(0).occurredAt());
        assertEquals("msg-11", response.events().get(0).rawMessage());
        assertNull(response.nextPageToken());
        assertEquals(1.0, eventsCounterCount("ok"));
        assertEquals(0.0, eventsCounterCount("bad_request"));
    }

    @Test
    public void fetchEventsCallsDaoWithExactLimitAndParsedIds() {
        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        service.fetchEvents(new ErrorCatalogEventsRequest("1,2,3", null, null, null, 50));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<KeysetCursor> cursorCaptor = ArgumentCaptor.forClass(KeysetCursor.class);
        verify(rawDao).findByTemplatesAndTimeRange(
                idsCaptor.capture(), eq((Long) null), eq((Long) null), cursorCaptor.capture(), eq(50));
        assertEquals(List.of(1L, 2L, 3L), idsCaptor.getValue());
        assertNull(cursorCaptor.getValue());
    }

    @Test
    public void fetchEventsTimeWindowParametersPropagateToDao() {
        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        service.fetchEvents(new ErrorCatalogEventsRequest("1", 100L, 200L, null, 100));

        verify(rawDao).findByTemplatesAndTimeRange(
                eq(List.of(1L)), eq(100L), eq(200L), eq((KeysetCursor) null), eq(100));
    }

    @Test
    public void fetchEventsNextPageTokenSetWhenDaoReturnsLimitRows() {
        // limit=2 → DAO returns exactly 2 → nextPageToken is encoded from the last row.
        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(rawRow(11L, 42L, 300L), rawRow(10L, 42L, 200L)));

        ErrorCatalogEventsResponse response = service.fetchEvents(eventsRequestWithLimit("42", 2));

        assertNotNull(response.nextPageToken());
        // Decode the token and confirm it carries the LAST row's (occurredAt, id).
        String json = new String(java.util.Base64.getUrlDecoder().decode(response.nextPageToken()),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\"t\":200"), "Token must encode last row occurredAt=200; got: " + json);
        assertTrue(json.contains("\"i\":10"), "Token must encode last row id=10; got: " + json);
    }

    @Test
    public void fetchEventsNextPageTokenNullWhenDaoReturnsBelowLimit() {
        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(rawRow(11L, 42L, 300L)));

        ErrorCatalogEventsResponse response = service.fetchEvents(eventsRequestWithLimit("42", 10));

        assertNull(response.nextPageToken());
    }

    @Test
    public void fetchEventsPageTokenRoundTripDecodesBackToOriginal() {
        // Encode a token via fetchEvents (limit=1, single row), then re-issue with that token as
        // page_token; DAO must be invoked with a cursor matching the original (occurredAt, id).
        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(rawRow(982341L, 42L, 1729012345678L)));
        String token = service.fetchEvents(eventsRequestWithLimit("42", 1)).nextPageToken();
        assertNotNull(token);

        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        service.fetchEvents(new ErrorCatalogEventsRequest("42", null, null, token, 1));

        ArgumentCaptor<KeysetCursor> cursorCaptor = ArgumentCaptor.forClass(KeysetCursor.class);
        verify(rawDao, org.mockito.Mockito.times(2)).findByTemplatesAndTimeRange(
                any(), any(), any(), cursorCaptor.capture(), anyInt());
        // First call: cursor null. Second call: cursor decodes to (1729012345678, 982341).
        List<KeysetCursor> captured = cursorCaptor.getAllValues();
        assertNull(captured.get(0));
        KeysetCursor decoded = captured.get(1);
        assertNotNull(decoded);
        assertEquals(1729012345678L, decoded.timeValue());
        assertEquals(982341L, decoded.id());
    }

    @Test
    public void fetchEventsMissingTemplateIds400StartsWithTemplateIdsPrefix() {
        BadRequestException ex1 = assertThrows(BadRequestException.class,
                () -> service.fetchEvents(eventsRequest(null)));
        assertTrue(ex1.getMessage().startsWith("'template_ids'"), "Got: " + ex1.getMessage());
        BadRequestException ex2 = assertThrows(BadRequestException.class,
                () -> service.fetchEvents(eventsRequest("")));
        assertTrue(ex2.getMessage().startsWith("'template_ids'"), "Got: " + ex2.getMessage());
        // Both bad-request paths bumped the counter — two failures → 2.0.
        assertEquals(2.0, eventsCounterCount("bad_request"));
    }

    @Test
    public void fetchEventsOversizeTemplateIds400StartsWithTemplateIdsPrefix() {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                csv.append(",");
            }
            csv.append(i);
        }
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.fetchEvents(eventsRequest(csv.toString())));
        assertTrue(ex.getMessage().startsWith("'template_ids'"), "Got: " + ex.getMessage());
    }

    @Test
    public void fetchEventsNonNumericTemplateIds400StartsWithTemplateIdsPrefix() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.fetchEvents(eventsRequest("1,abc,3")));
        assertTrue(ex.getMessage().startsWith("'template_ids'"), "Got: " + ex.getMessage());
    }

    @Test
    public void fetchEventsOccurredFromGreaterThanTo400StartsWithOccurredFromPrefix() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.fetchEvents(new ErrorCatalogEventsRequest("1", 200L, 100L, null, 100)));
        assertTrue(ex.getMessage().startsWith("'occurred_from'"), "Got: " + ex.getMessage());
    }

    @Test
    public void fetchEventsOccurredFromEqualToOccurredToIsAccepted() {
        // Boundary: equality is allowed; the DAO's `< toMsExclusive` predicate yields zero rows
        // but the request itself is valid (no 400).
        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        ErrorCatalogEventsResponse response =
                service.fetchEvents(new ErrorCatalogEventsRequest("1", 200L, 200L, null, 100));
        assertEquals(0, response.events().size());
    }

    @Test
    public void fetchEventsBadPageToken400StartsWithPageTokenPrefix() {
        BadRequestException ex1 = assertThrows(BadRequestException.class,
                () -> service.fetchEvents(new ErrorCatalogEventsRequest("1", null, null, "!!!notbase64!!!", 100)));
        assertTrue(ex1.getMessage().startsWith("'page_token'"), "Got: " + ex1.getMessage());
        // base64 of "not json" — decodes successfully to bytes but parses as a string scalar, not
        // an object with t/i fields.
        String base64NonJson = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("nope".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        BadRequestException ex2 = assertThrows(BadRequestException.class,
                () -> service.fetchEvents(new ErrorCatalogEventsRequest("1", null, null, base64NonJson, 100)));
        assertTrue(ex2.getMessage().startsWith("'page_token'"), "Got: " + ex2.getMessage());
    }

    @Test
    public void fetchEventsZeroOrNegativeLimit400StartsWithLimitPrefix() {
        BadRequestException ex1 = assertThrows(BadRequestException.class,
                () -> service.fetchEvents(eventsRequestWithLimit("1", 0)));
        assertTrue(ex1.getMessage().startsWith("'limit'"), "Got: " + ex1.getMessage());
        BadRequestException ex2 = assertThrows(BadRequestException.class,
                () -> service.fetchEvents(eventsRequestWithLimit("1", -1)));
        assertTrue(ex2.getMessage().startsWith("'limit'"), "Got: " + ex2.getMessage());
    }

    @Test
    public void fetchEventsLimitAboveCapSilentlyClampedTo500() {
        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        service.fetchEvents(eventsRequestWithLimit("1", 10_000));
        verify(rawDao).findByTemplatesAndTimeRange(any(), any(), any(), any(), eq(500));
    }

    @Test
    public void fetchEventsLimitAt500BoundaryIsHonored() {
        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        service.fetchEvents(eventsRequestWithLimit("1", 500));
        verify(rawDao).findByTemplatesAndTimeRange(any(), any(), any(), any(), eq(500));
    }

    @Test
    public void fetchEventsNonPostgresThrowsNotImplementedAndIncrementsCounter() {
        when(dbProperties.getDbType()).thenReturn(DbType.DERBY);
        NotImplementedException ex = assertThrows(NotImplementedException.class,
                () -> service.fetchEvents(eventsRequest("1")));
        assertEquals("Error/warning logs API is available for PostgreSQL only", ex.getMessage());
        assertEquals(1.0, eventsCounterCount("not_implemented"));
    }

    @Test
    public void fetchEventsFeatureFlagOffThrowsNotFoundAndIncrementsCounter() {
        setStringSystemProperty(ConstantValues.devopsTroubleshootingEnabled, "false");
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.fetchEvents(eventsRequest("1")));
        assertEquals("Error/warning logs API is disabled on this instance", ex.getMessage());
        assertEquals(1.0, eventsCounterCount("not_found"));
    }

    @Test
    public void fetchEventsOkCounterOnlyIncrementsOnSuccess() {
        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        service.fetchEvents(eventsRequest("1"));
        assertEquals(1.0, eventsCounterCount("ok"));
        assertEquals(0.0, eventsCounterCount("bad_request"));
        assertEquals(0.0, eventsCounterCount("not_found"));
        assertEquals(0.0, eventsCounterCount("not_implemented"));
    }

    @Test
    public void fetchEventsCountersDistinctFromSearchCounters() {
        when(templatesDao.searchTemplates(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(rawDao.findByTemplatesAndTimeRange(any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        service.search(defaults());
        service.fetchEvents(eventsRequest("1"));

        // Each metric is named distinctly and carries its own per-outcome tag space.
        assertEquals(1.0, counterCount("ok"), "search_total{ok} = 1");
        assertEquals(1.0, eventsCounterCount("ok"), "fetch_events_total{ok} = 1");
    }

    @Test
    public void validateAndClampEventsLimitClampsAndRejects() {
        assertEquals(100, ErrorCatalogRestServiceImpl.validateAndClampEventsLimit(100));
        assertEquals(500, ErrorCatalogRestServiceImpl.validateAndClampEventsLimit(500));
        assertEquals(500, ErrorCatalogRestServiceImpl.validateAndClampEventsLimit(10_000));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> ErrorCatalogRestServiceImpl.validateAndClampEventsLimit(0));
        assertTrue(ex.getMessage().startsWith("'limit'"));
    }
}
