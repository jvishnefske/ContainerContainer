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

package org.artifactory.rest.resource.devopsagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.exception.http.NotImplementedException;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogEntry;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogEventEntry;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogEventsResponse;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogSearchResponse;
import org.artifactory.rest.services.devopsagent.ErrorCatalogEventsRequest;
import org.artifactory.rest.services.devopsagent.ErrorCatalogRestService;
import org.artifactory.rest.services.devopsagent.ErrorCatalogSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resource-layer unit tests for {@link ErrorCatalogResource}. Locks the static
 * {@link RolesAllowed} guarantee, asserts query-param→request-DTO wiring, validates the
 * Jackson-serialized envelope shape against the documented field set, and confirms exceptions
 * propagate up to the JAX-RS exception mappers without resource-layer swallowing.
 */
public class ErrorCatalogResourceTest {


    private ErrorCatalogRestService service;
    private ErrorCatalogResource resource;

    @BeforeEach
    public void setUp() {
        service = mock(ErrorCatalogRestService.class);
        resource = new ErrorCatalogResource(service);
    }

    @Test
    public void rolesAllowedAnnotationDeclaresAdminAndHa() {
        // Defends against future refactors that silently strip the class-level @RolesAllowed and
        // accidentally rely on Jersey's parent-resource propagation — see api-doc v2 § 3.
        RolesAllowed rolesAllowed = ErrorCatalogResource.class.getAnnotation(RolesAllowed.class);
        assertNotNull(rolesAllowed, "ErrorCatalogResource must carry @RolesAllowed at class level");
        List<String> declared = Arrays.asList(rolesAllowed.value());
        assertTrue(declared.contains(AuthorizationService.ROLE_ADMIN),
                "@RolesAllowed must include ROLE_ADMIN; got: " + declared);
        assertTrue(declared.contains(HaRestConstants.ROLE_HA),
                "@RolesAllowed must include ROLE_HA; got: " + declared);
        assertEquals(2, declared.size(), "@RolesAllowed must be exactly {ROLE_ADMIN, ROLE_HA}; got: " + declared);
    }

    @Test
    public void queryParamsAreCollectedIntoRequestDto() {
        when(service.search(org.mockito.ArgumentMatchers.any(ErrorCatalogSearchRequest.class)))
                .thenReturn(new ErrorCatalogSearchResponse(List.of(), false));

        Response response = resource.search(
                "1,2", "ERROR", "c.A", "java.io.IOException",
                5L, 100L, 10L, 20L, 30L, 40L,
                "checksum mismatch", true, 50, 100);

        assertEquals(200, response.getStatus());
        ArgumentCaptor<ErrorCatalogSearchRequest> captor =
                ArgumentCaptor.forClass(ErrorCatalogSearchRequest.class);
        verify(service).search(captor.capture());
        ErrorCatalogSearchRequest forwarded = captor.getValue();
        assertEquals("1,2", forwarded.idsCsv());
        assertEquals("ERROR", forwarded.severitiesCsv());
        assertEquals("c.A", forwarded.loggerClassesCsv());
        assertEquals("java.io.IOException", forwarded.exceptionClassesCsv());
        assertEquals(5L, forwarded.occurrenceCountFrom());
        assertEquals(100L, forwarded.occurrenceCountTo());
        assertEquals(10L, forwarded.firstOccurrenceFrom());
        assertEquals(20L, forwarded.firstOccurrenceTo());
        assertEquals(30L, forwarded.lastOccurrenceFrom());
        assertEquals(40L, forwarded.lastOccurrenceTo());
        assertEquals("checksum mismatch", forwarded.search());
        assertEquals(true, forwarded.matchAll());
        assertEquals(50, forwarded.limit());
        assertEquals(100, forwarded.offset());
    }

    @Test
    public void responseEnvelopeAndEntrySchemaMatchDocumentedFieldSet() throws Exception {
        ErrorCatalogSearchResponse body = new ErrorCatalogSearchResponse(
                List.of(new ErrorCatalogEntry(
                        1234L,
                        "ab12cd34",
                        "ERROR",
                        "org.artifactory.foo.Bar",
                        "java.lang.RuntimeException",
                        "Failed to {} resource {}",
                        1715630000000L,
                        1715670000000L,
                        42L)),
                true);
        when(service.search(org.mockito.ArgumentMatchers.any(ErrorCatalogSearchRequest.class))).thenReturn(body);

        Response response = resource.search(
                null, null, null, null,
                null, null, null, null, null, null,
                null, false, 1000, 0);
        assertEquals(200, response.getStatus());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(response.getEntity());
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = mapper.readValue(json, Map.class);
        // Envelope: exactly { logs, hasMore } — no extras, no missing.
        assertEquals(2, envelope.size(), "Envelope must have exactly logs + hasMore; got: " + envelope.keySet());
        assertTrue(envelope.containsKey("logs"));
        assertTrue(envelope.containsKey("hasMore"));
        assertEquals(Boolean.TRUE, envelope.get("hasMore"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> logs = (List<Map<String, Object>>) envelope.get("logs");
        assertEquals(1, logs.size());
        Map<String, Object> entry = logs.get(0);
        // Entry must contain exactly the 9 documented fields — drift here is a contract break.
        assertEquals(9, entry.size(), "Entry must have exactly 9 fields; got: " + entry.keySet());
        assertTrue(entry.containsKey("id"));
        assertTrue(entry.containsKey("signatureHash"));
        assertTrue(entry.containsKey("severity"));
        assertTrue(entry.containsKey("loggerClass"));
        assertTrue(entry.containsKey("exceptionClass"));
        assertTrue(entry.containsKey("templateMessage"));
        assertTrue(entry.containsKey("firstOccurrenceAt"));
        assertTrue(entry.containsKey("lastOccurrenceAt"));
        assertTrue(entry.containsKey("occurrenceCount"));
    }

    @Test
    public void serviceBadRequestPropagatesUnswallowed() {
        when(service.search(org.mockito.ArgumentMatchers.any(ErrorCatalogSearchRequest.class)))
                .thenThrow(new BadRequestException("'severity' must be one of WARN, ERROR (got: INFO)"));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> resource.search(
                null, "INFO", null, null,
                null, null, null, null, null, null,
                null, false, 1000, 0));
        assertTrue(ex.getMessage().startsWith("'severity'"));
    }

    @Test
    public void serviceNotFoundPropagatesUnswallowed() {
        when(service.search(org.mockito.ArgumentMatchers.any(ErrorCatalogSearchRequest.class)))
                .thenThrow(new NotFoundException("Error/warning logs API is disabled on this instance"));

        NotFoundException ex = assertThrows(NotFoundException.class, () -> resource.search(
                null, null, null, null,
                null, null, null, null, null, null,
                null, false, 1000, 0));
        assertEquals("Error/warning logs API is disabled on this instance", ex.getMessage());
    }

    @Test
    public void serviceNotImplementedPropagatesUnswallowed() {
        when(service.search(org.mockito.ArgumentMatchers.any(ErrorCatalogSearchRequest.class)))
                .thenThrow(new NotImplementedException("Error/warning logs API is available for PostgreSQL only"));

        NotImplementedException ex = assertThrows(NotImplementedException.class, () -> resource.search(
                null, null, null, null,
                null, null, null, null, null, null,
                null, false, 1000, 0));
        assertEquals("Error/warning logs API is available for PostgreSQL only", ex.getMessage());
    }

    // ============================================================
    // Raw events drill-down sub-path: GET /events (RTDEV-86991)
    // ============================================================

    @Test
    public void eventsHasNoMethodLevelRolesAllowedOverride() throws Exception {
        // Defends api-doc v2 § 3.1: the new events() method intentionally has no per-method
        // @RolesAllowed override — it inherits the class-level annotation. A future PR adding an
        // override would silently widen or narrow scope without breaking the class-level test above.
        Method events = ErrorCatalogResource.class.getMethod(
                "events", String.class, Long.class, Long.class, String.class, int.class);
        assertNull(events.getAnnotation(RolesAllowed.class),
                "events() must NOT carry a method-level @RolesAllowed — class-level guards it");
    }

    @Test
    public void eventsQueryParamsAreCollectedIntoRequestDto() {
        when(service.fetchEvents(org.mockito.ArgumentMatchers.any(ErrorCatalogEventsRequest.class)))
                .thenReturn(new ErrorCatalogEventsResponse(List.of(), null));

        Response response = resource.events("1,2", 100L, 200L, "tok", 50);

        assertEquals(200, response.getStatus());
        ArgumentCaptor<ErrorCatalogEventsRequest> captor =
                ArgumentCaptor.forClass(ErrorCatalogEventsRequest.class);
        verify(service).fetchEvents(captor.capture());
        ErrorCatalogEventsRequest forwarded = captor.getValue();
        assertEquals("1,2", forwarded.templateIdsCsv());
        assertEquals(100L, forwarded.occurredFrom());
        assertEquals(200L, forwarded.occurredTo());
        assertEquals("tok", forwarded.pageToken());
        assertEquals(50, forwarded.limit());
    }

    @Test
    public void eventsResponseEnvelopeAndEntrySchemaMatchDocumentedFieldSet() throws Exception {
        ErrorCatalogEventsResponse body = new ErrorCatalogEventsResponse(
                List.of(new ErrorCatalogEventEntry(982341L, 42L, 1729012345678L, "raw line")),
                "eyJ0IjoxLCJpIjoyfQ");
        when(service.fetchEvents(org.mockito.ArgumentMatchers.any(ErrorCatalogEventsRequest.class)))
                .thenReturn(body);

        Response response = resource.events("42", null, null, null, 100);
        assertEquals(200, response.getStatus());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(response.getEntity());
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = mapper.readValue(json, Map.class);
        // Envelope: exactly { events, nextPageToken } when token present — no extras, no missing.
        assertEquals(2, envelope.size(),
                "Envelope must have exactly events + nextPageToken (when token present); got: "
                        + envelope.keySet());
        assertTrue(envelope.containsKey("events"));
        assertTrue(envelope.containsKey("nextPageToken"));
        assertEquals("eyJ0IjoxLCJpIjoyfQ", envelope.get("nextPageToken"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) envelope.get("events");
        assertEquals(1, events.size());
        Map<String, Object> entry = events.get(0);
        // Entry must contain exactly the 4 documented fields — drift here is a contract break.
        assertEquals(4, entry.size(), "Event entry must have exactly 4 fields; got: " + entry.keySet());
        assertTrue(entry.containsKey("id"));
        assertTrue(entry.containsKey("templateId"));
        assertTrue(entry.containsKey("occurredAt"));
        assertTrue(entry.containsKey("rawMessage"));
    }

    @Test
    public void eventsResponseOmitsNextPageTokenOnLastPage() throws Exception {
        // @JsonInclude(NON_NULL) on the response record: null token must be omitted, not serialised
        // as "nextPageToken":null. Agents parse for field presence per api-doc v2 § 5.5.
        ErrorCatalogEventsResponse body = new ErrorCatalogEventsResponse(
                List.of(new ErrorCatalogEventEntry(1L, 42L, 1L, "m")),
                null);
        when(service.fetchEvents(org.mockito.ArgumentMatchers.any(ErrorCatalogEventsRequest.class)))
                .thenReturn(body);

        Response response = resource.events("42", null, null, null, 100);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(response.getEntity());
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = mapper.readValue(json, Map.class);

        assertEquals(1, envelope.size(),
                "Envelope on last page must have exactly 'events' (no nextPageToken); got: "
                        + envelope.keySet());
        assertTrue(envelope.containsKey("events"));
        assertFalse(envelope.containsKey("nextPageToken"),
                "nextPageToken must be omitted on last page, not serialised as null");
    }

    @Test
    public void eventsServiceBadRequestPropagatesUnswallowed() {
        when(service.fetchEvents(org.mockito.ArgumentMatchers.any(ErrorCatalogEventsRequest.class)))
                .thenThrow(new BadRequestException("'template_ids' must contain between 1 and 50 ids"));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resource.events(null, null, null, null, 100));
        assertTrue(ex.getMessage().startsWith("'template_ids'"));
    }

    @Test
    public void eventsServiceNotFoundPropagatesUnswallowed() {
        when(service.fetchEvents(org.mockito.ArgumentMatchers.any(ErrorCatalogEventsRequest.class)))
                .thenThrow(new NotFoundException("Error/warning logs API is disabled on this instance"));

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> resource.events("1", null, null, null, 100));
        assertEquals("Error/warning logs API is disabled on this instance", ex.getMessage());
    }

    @Test
    public void eventsServiceNotImplementedPropagatesUnswallowed() {
        when(service.fetchEvents(org.mockito.ArgumentMatchers.any(ErrorCatalogEventsRequest.class)))
                .thenThrow(new NotImplementedException("Error/warning logs API is available for PostgreSQL only"));

        NotImplementedException ex = assertThrows(NotImplementedException.class,
                () -> resource.events("1", null, null, null, 100));
        assertEquals("Error/warning logs API is available for PostgreSQL only", ex.getMessage());
    }
}
