package org.artifactory.rest.resource.devopsagent.requestlog.dto;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.storage.db.devopsagent.requestlog.model.Cursor;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogException;
import org.artifactory.storage.db.devopsagent.requestlog.service.SearchQueryTokenizer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Parses raw HTTP query parameters into a {@link SearchCriteria}, with deterministic validation prefixes. */
public final class RequestLogQueryParamParser {

    static final int DEFAULT_LIMIT = 100;
    static final int SEARCH_MAX_LEN = 256;
    static final int LIMIT_MAX = 2000;
    static final int OFFSET_MAX = 10_000;

    static final String SEARCH_QUERY_TOO_LONG_MESSAGE =
            "'search' length exceeds " + SEARCH_MAX_LEN;
    static final String PARAM_MUST_BE_INTEGER_MESSAGE = "'%s' must be an integer";
    static final String MATCH_ALL_INVALID_MESSAGE = "'match_all' must be true or false";
    static final String LIMIT_OUT_OF_RANGE_MESSAGE =
            "'limit' must be between 1 and " + LIMIT_MAX;
    static final String OFFSET_EXCEEDS_MAX_MESSAGE =
            "'offset' exceeds maximum " + OFFSET_MAX + " — use 'cursor' parameter to page deeper";
    static final String OFFSET_OUT_OF_RANGE_MESSAGE =
            "'offset' must be between 0 and " + OFFSET_MAX;
    static final String MALFORMED_CURSOR_MESSAGE = "'cursor' is malformed";

    private RequestLogQueryParamParser() {
        // utility
    }

    public static SearchCriteria parse(String timeFrom, String timeTo, String minDurationMs,
            String repoNameRaw, String search, String matchAll,
            String limitRaw, String offsetRaw, String cursorRaw) {
        Long fromMs = parseOptionalLong(timeFrom, "time_from");
        Long toMs = parseOptionalLong(timeTo, "time_to");
        Long parsedMinDur = parseOptionalLong(minDurationMs, "min_duration_ms");
        long minDur = parsedMinDur != null ? parsedMinDur : 0L;
        boolean matchAllFlag = parseBoolean(matchAll);
        int limit = parseLimit(limitRaw);
        int offset = parseOffset(offsetRaw);
        Cursor cursor = parseCursor(cursorRaw);
        if (search != null && search.length() > SEARCH_MAX_LEN) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, SEARCH_QUERY_TOO_LONG_MESSAGE);
        }
        String rawSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String tokenized = SearchQueryTokenizer.tokenize(search, matchAllFlag);
        String tsquery = (tokenized == null || tokenized.isEmpty()) ? null : tokenized;
        List<String> repoNames = parseCsv(repoNameRaw);

        return SearchCriteria.builder()
                .timeFrom(fromMs)
                .timeTo(toMs)
                .minDurationMs(minDur)
                .repoNames(repoNames)
                .searchQuery(tsquery)
                .rawSearchText(rawSearch)
                .limit(limit)
                .offset(offset)
                .cursor(cursor)
                .build();
    }

    public static Long parseOptionalLong(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST,
                    String.format(PARAM_MUST_BE_INTEGER_MESSAGE, paramName));
        }
    }

    static boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String norm = raw.trim().toLowerCase();
        if ("true".equals(norm)) {
            return true;
        }
        if ("false".equals(norm)) {
            return false;
        }
        throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, MATCH_ALL_INVALID_MESSAGE);
    }

    static int parseLimit(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            if (v < 1 || v > LIMIT_MAX) {
                throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, LIMIT_OUT_OF_RANGE_MESSAGE);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, LIMIT_OUT_OF_RANGE_MESSAGE);
        }
    }

    static int parseOffset(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            if (v > OFFSET_MAX) {
                throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, OFFSET_EXCEEDS_MAX_MESSAGE);
            }
            if (v < 0) {
                throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, OFFSET_OUT_OF_RANGE_MESSAGE);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, OFFSET_OUT_OF_RANGE_MESSAGE);
        }
    }

    static Cursor parseCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Cursor.decode(raw);
        } catch (IllegalArgumentException e) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, MALFORMED_CURSOR_MESSAGE);
        }
    }

    static List<String> parseCsv(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static Optional<String> encodeCursor(Cursor cursor) {
        return cursor == null ? Optional.empty() : Optional.of(cursor.encode());
    }
}
