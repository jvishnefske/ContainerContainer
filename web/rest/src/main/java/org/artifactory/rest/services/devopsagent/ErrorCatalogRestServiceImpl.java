package org.artifactory.rest.services.devopsagent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.exception.http.NotImplementedException;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogEntry;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogEventEntry;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogEventsResponse;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogSearchResponse;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogTemplateDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.entity.RawEventRow;
import org.artifactory.storage.db.devopsagent.errorcatalog.entity.TemplateRow;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.AgenticSearchCriteria;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.KeysetCursor;
import org.jfrog.storage.DbType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Default implementation of {@link ErrorCatalogRestService}. Owns parsing, validation,
 * clamping, search tokenisation, DAO invocation, hasMore detection, and per-outcome metric
 * emission for the agentic read surface introduced by RTDEV-85639.
 *
 * <p>Validation messages follow the stable single-quoted parameter-name prefix convention
 * documented in api-documentation v2 § 6.3 so agent callers can branch on {@code (status, prefix)}
 * rather than free-form English.</p>
 */
@Service
@Slf4j
public class ErrorCatalogRestServiceImpl implements ErrorCatalogRestService {

    static final int DEFAULT_LIMIT = 1000;
    static final int MAX_LIMIT = 2000;
    static final int MAX_OFFSET = 10_000;

    static final int ID_LIST_CAP = 100;
    static final int OTHER_LIST_CAP = 50;

    // Raw-events drill-down caps — tighter than the templates endpoint because each row carries the
    // full raw_message payload (research v2 § 1.1).
    static final int EVENTS_DEFAULT_LIMIT = 100;
    static final int EVENTS_MAX_LIMIT = 500;
    static final int EVENTS_TEMPLATE_IDS_CAP = 50;

    private static final Set<String> ALLOWED_SEVERITIES = Set.of("WARN", "ERROR");

    // PG tsquery metacharacters stripped from each token before composing the to_tsquery argument.
    // Agents may send free-form prose; trimming these defends against unintended parse failures.
    private static final char[] TSQUERY_METACHARACTERS = {'&', '|', '!', '(', ')', ':', '*', '<', '>'};

    private static final String SEARCH_METRIC_NAME = "error_catalog_api.search_total";
    private static final String EVENTS_METRIC_NAME = "error_catalog_api.fetch_events_total";
    private static final String OUTCOME_OK = "ok";
    private static final String OUTCOME_BAD_REQUEST = "bad_request";
    private static final String OUTCOME_NOT_FOUND = "not_found";
    private static final String OUTCOME_NOT_IMPLEMENTED = "not_implemented";

    // Page-token JSON field names — single-char for compact base64 envelopes.
    private static final String TOKEN_FIELD_TIME = "t";
    private static final String TOKEN_FIELD_ID = "i";

    private final ErrorCatalogTemplateDao templatesDao;
    private final ErrorCatalogDao rawDao;
    private final ArtifactoryDbProperties dbProperties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Counter searchOkCounter;
    private Counter searchBadRequestCounter;
    private Counter searchNotFoundCounter;
    private Counter searchNotImplementedCounter;

    private Counter eventsOkCounter;
    private Counter eventsBadRequestCounter;
    private Counter eventsNotFoundCounter;
    private Counter eventsNotImplementedCounter;

    public ErrorCatalogRestServiceImpl(ErrorCatalogTemplateDao templatesDao,
            ErrorCatalogDao rawDao,
            ArtifactoryDbProperties dbProperties,
            MeterRegistry meterRegistry) {
        this.templatesDao = templatesDao;
        this.rawDao = rawDao;
        this.dbProperties = dbProperties;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerCounters() {
        searchOkCounter = counter(SEARCH_METRIC_NAME,
                "Count of agentic error_catalog search calls, tagged by outcome", OUTCOME_OK);
        searchBadRequestCounter = counter(SEARCH_METRIC_NAME,
                "Count of agentic error_catalog search calls, tagged by outcome", OUTCOME_BAD_REQUEST);
        searchNotFoundCounter = counter(SEARCH_METRIC_NAME,
                "Count of agentic error_catalog search calls, tagged by outcome", OUTCOME_NOT_FOUND);
        searchNotImplementedCounter = counter(SEARCH_METRIC_NAME,
                "Count of agentic error_catalog search calls, tagged by outcome", OUTCOME_NOT_IMPLEMENTED);

        eventsOkCounter = counter(EVENTS_METRIC_NAME,
                "Count of agentic error_catalog raw-events fetch calls, tagged by outcome", OUTCOME_OK);
        eventsBadRequestCounter = counter(EVENTS_METRIC_NAME,
                "Count of agentic error_catalog raw-events fetch calls, tagged by outcome", OUTCOME_BAD_REQUEST);
        eventsNotFoundCounter = counter(EVENTS_METRIC_NAME,
                "Count of agentic error_catalog raw-events fetch calls, tagged by outcome", OUTCOME_NOT_FOUND);
        eventsNotImplementedCounter = counter(EVENTS_METRIC_NAME,
                "Count of agentic error_catalog raw-events fetch calls, tagged by outcome",
                OUTCOME_NOT_IMPLEMENTED);
    }

    private Counter counter(String name, String description, String outcome) {
        return Counter.builder(name)
                .description(description)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    @Override
    public ErrorCatalogSearchResponse search(ErrorCatalogSearchRequest request) {
        try {
            ErrorCatalogSearchResponse response = doSearch(request);
            searchOkCounter.increment();
            return response;
        } catch (NotFoundException e) {
            searchNotFoundCounter.increment();
            throw e;
        } catch (NotImplementedException e) {
            searchNotImplementedCounter.increment();
            throw e;
        } catch (BadRequestException e) {
            searchBadRequestCounter.increment();
            throw e;
        }
    }

    @Override
    public ErrorCatalogEventsResponse fetchEvents(ErrorCatalogEventsRequest request) {
        try {
            ErrorCatalogEventsResponse response = doFetchEvents(request);
            eventsOkCounter.increment();
            return response;
        } catch (NotFoundException e) {
            eventsNotFoundCounter.increment();
            throw e;
        } catch (NotImplementedException e) {
            eventsNotImplementedCounter.increment();
            throw e;
        } catch (BadRequestException e) {
            eventsBadRequestCounter.increment();
            throw e;
        }
    }

    private ErrorCatalogEventsResponse doFetchEvents(ErrorCatalogEventsRequest request) {
        if (!ConstantValues.devopsTroubleshootingEnabled.getBoolean()) {
            throw new NotFoundException("Error/warning logs API is disabled on this instance");
        }
        if (dbProperties.getDbType() != DbType.POSTGRESQL) {
            throw new NotImplementedException("Error/warning logs API is available for PostgreSQL only");
        }

        List<Long> templateIds = parseLongList(request.templateIdsCsv(), "template_ids", EVENTS_TEMPLATE_IDS_CAP);
        if (templateIds.isEmpty()) {
            throw new BadRequestException(
                    "'template_ids' must contain between 1 and " + EVENTS_TEMPLATE_IDS_CAP + " ids");
        }
        validateOccurredRange(request.occurredFrom(), request.occurredTo());
        int limit = validateAndClampEventsLimit(request.limit());
        KeysetCursor cursor = decodePageToken(request.pageToken());

        if (log.isDebugEnabled()) {
            log.debug("error_catalog events: templateIds={} occurredFrom={} occurredTo={} cursorPresent={} "
                            + "limit={}",
                    templateIds, request.occurredFrom(), request.occurredTo(), cursor != null, limit);
        }

        List<RawEventRow> rows = rawDao.findByTemplatesAndTimeRange(
                templateIds, request.occurredFrom(), request.occurredTo(), cursor, limit);
        List<ErrorCatalogEventEntry> entries = new ArrayList<>(rows.size());
        for (RawEventRow row : rows) {
            entries.add(toEventEntry(row));
        }
        String nextPageToken = (rows.size() == limit) ? encodePageToken(rows.get(rows.size() - 1)) : null;
        return new ErrorCatalogEventsResponse(entries, nextPageToken);
    }

    private static ErrorCatalogEventEntry toEventEntry(RawEventRow row) {
        return new ErrorCatalogEventEntry(row.id(), row.templateId(), row.occurredAt(), row.rawMessage());
    }

    private static void validateOccurredRange(Long from, Long to) {
        if (from != null && to != null && from > to) {
            throw new BadRequestException(
                    "'occurred_from' must be <= 'occurred_to' (got: " + from + " > " + to + ")");
        }
    }

    static int validateAndClampEventsLimit(int requestedLimit) {
        if (requestedLimit < 1) {
            throw new BadRequestException("'limit' must be >= 1 (got: " + requestedLimit + ")");
        }
        return Math.min(requestedLimit, EVENTS_MAX_LIMIT);
    }

    private String encodePageToken(RawEventRow lastRow) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(TOKEN_FIELD_TIME, lastRow.occurredAt());
        node.put(TOKEN_FIELD_ID, lastRow.id());
        try {
            byte[] json = objectMapper.writeValueAsBytes(node);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (JsonProcessingException e) {
            // Defensive — ObjectNode.write cannot fail in practice. If it does, surface as 500.
            throw new IllegalStateException("Failed to encode page token", e);
        }
    }

    private KeysetCursor decodePageToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(token);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("'page_token' is not a valid token");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("'page_token' is not a valid token");
        }
        if (node == null || !node.isObject()
                || !node.hasNonNull(TOKEN_FIELD_TIME) || !node.hasNonNull(TOKEN_FIELD_ID)
                || !node.get(TOKEN_FIELD_TIME).canConvertToLong()
                || !node.get(TOKEN_FIELD_ID).canConvertToLong()) {
            throw new BadRequestException("'page_token' is not a valid token");
        }
        return KeysetCursor.of(node.get(TOKEN_FIELD_TIME).asLong(), node.get(TOKEN_FIELD_ID).asLong());
    }

    private ErrorCatalogSearchResponse doSearch(ErrorCatalogSearchRequest request) {
        if (!ConstantValues.devopsTroubleshootingEnabled.getBoolean()) {
            throw new NotFoundException("Error/warning logs API is disabled on this instance");
        }
        if (dbProperties.getDbType() != DbType.POSTGRESQL) {
            throw new NotImplementedException("Error/warning logs API is available for PostgreSQL only");
        }

        List<Long> ids = parseLongList(request.idsCsv(), "id", ID_LIST_CAP);
        List<String> severities = parseStringList(request.severitiesCsv(), "severity", OTHER_LIST_CAP);
        validateSeverityVocabulary(severities);
        List<String> loggerClasses = parseStringList(request.loggerClassesCsv(), "logger_class", OTHER_LIST_CAP);
        List<String> exceptionClasses = parseStringList(request.exceptionClassesCsv(), "exception_class",
                OTHER_LIST_CAP);

        validateRange("occurrence_count", request.occurrenceCountFrom(), request.occurrenceCountTo());
        validateRange("first_occurrence", request.firstOccurrenceFrom(), request.firstOccurrenceTo());
        validateRange("last_occurrence", request.lastOccurrenceFrom(), request.lastOccurrenceTo());

        int limit = validateAndClampLimit(request.limit());
        int offset = validateAndClampOffset(request.offset());
        String tsqueryString = buildTsquery(request.search(), request.matchAll());

        if (log.isDebugEnabled()) {
            log.debug("error_catalog search: ids={} severity={} logger={} excClass={} occ=[{}..{}] "
                            + "firstOcc=[{}..{}] lastOcc=[{}..{}] search='{}' matchAll={} limit={} offset={}",
                    ids, severities, loggerClasses, exceptionClasses,
                    request.occurrenceCountFrom(), request.occurrenceCountTo(),
                    request.firstOccurrenceFrom(), request.firstOccurrenceTo(),
                    request.lastOccurrenceFrom(), request.lastOccurrenceTo(),
                    request.search(), request.matchAll(), limit, offset);
        }

        AgenticSearchCriteria criteria = new AgenticSearchCriteria(
                ids, severities, loggerClasses, exceptionClasses,
                request.occurrenceCountFrom(), request.occurrenceCountTo(),
                request.firstOccurrenceFrom(), request.firstOccurrenceTo(),
                request.lastOccurrenceFrom(), request.lastOccurrenceTo(),
                tsqueryString);

        List<TemplateRow> rows = templatesDao.searchTemplates(criteria, limit + 1, offset);
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        List<ErrorCatalogEntry> entries = new ArrayList<>(rows.size());
        for (TemplateRow row : rows) {
            entries.add(toEntry(row));
        }
        return new ErrorCatalogSearchResponse(entries, hasMore);
    }

    private static ErrorCatalogEntry toEntry(TemplateRow row) {
        return new ErrorCatalogEntry(
                row.id(),
                row.signatureHash(),
                row.severity(),
                row.loggerClass(),
                row.exceptionClass(),
                row.templateMessage(),
                row.firstOccurrenceAt(),
                row.lastOccurrenceAt(),
                row.occurrenceCount());
    }

    static List<Long> parseLongList(String csv, String paramName, int cap) {
        List<String> tokens = splitCsv(csv);
        if (tokens.isEmpty()) {
            return List.of();
        }
        if (tokens.size() > cap) {
            throw new BadRequestException(
                    "'" + paramName + "' must contain at most " + cap + " values (got: " + tokens.size() + ")");
        }
        List<Long> out = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            try {
                out.add(Long.parseLong(token));
            } catch (NumberFormatException e) {
                throw new BadRequestException(
                        "'" + paramName + "' must be a comma-separated list of longs (got: '" + token + "')");
            }
        }
        return out;
    }

    static List<String> parseStringList(String csv, String paramName, int cap) {
        List<String> tokens = splitCsv(csv);
        if (tokens.isEmpty()) {
            return List.of();
        }
        if (tokens.size() > cap) {
            throw new BadRequestException(
                    "'" + paramName + "' must contain at most " + cap + " values (got: " + tokens.size() + ")");
        }
        return tokens;
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        String[] parts = csv.split(",");
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static void validateSeverityVocabulary(List<String> severities) {
        for (String severity : severities) {
            if (!ALLOWED_SEVERITIES.contains(severity)) {
                throw new BadRequestException(
                        "'severity' must be one of WARN, ERROR (got: " + severity + ")");
            }
        }
    }

    private static void validateRange(String prefixBase, Long from, Long to) {
        if (from != null && to != null && from > to) {
            throw new BadRequestException(
                    "'" + prefixBase + "_from' must be <= '" + prefixBase + "_to' (got: "
                            + from + " > " + to + ")");
        }
    }

    static int validateAndClampLimit(int requestedLimit) {
        if (requestedLimit < 1) {
            throw new BadRequestException("'limit' must be >= 1 (got: " + requestedLimit + ")");
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    static int validateAndClampOffset(int requestedOffset) {
        if (requestedOffset < 0) {
            throw new BadRequestException("'offset' must be >= 0 (got: " + requestedOffset + ")");
        }
        return Math.min(requestedOffset, MAX_OFFSET);
    }

    static String buildTsquery(String search, boolean matchAll) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String[] rawTokens = search.trim().split("\\s+");
        List<String> sanitized = new ArrayList<>(rawTokens.length);
        for (String raw : rawTokens) {
            String stripped = stripMetacharacters(raw);
            if (!stripped.isEmpty()) {
                sanitized.add(stripped);
            }
        }
        if (sanitized.isEmpty()) {
            return null;
        }
        return String.join(matchAll ? " & " : " | ", sanitized);
    }

    private static String stripMetacharacters(String token) {
        StringBuilder sb = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!isTsqueryMetacharacter(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isTsqueryMetacharacter(char c) {
        for (char meta : TSQUERY_METACHARACTERS) {
            if (c == meta) {
                return true;
            }
        }
        return false;
    }

    // Test hook — array reference rather than the collection so callers can compare via assertArrayEquals.
    static char[] tsqueryMetacharacters() {
        return Arrays.copyOf(TSQUERY_METACHARACTERS, TSQUERY_METACHARACTERS.length);
    }

    static Set<String> allowedSeverities() {
        return new HashSet<>(ALLOWED_SEVERITIES);
    }
}
