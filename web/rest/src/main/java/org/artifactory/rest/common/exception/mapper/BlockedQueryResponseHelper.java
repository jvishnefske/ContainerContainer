package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.aql.QueryBlockedException;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

/**
 * Shared logic for mapping a {@link QueryBlockedException} to an HTTP 400 response.
 * <p>
 * A blocked query never reaches a JAX-RS mapper directly: it is a checked {@link java.sql.SQLException}
 * that is wrapped (e.g. in {@code StorageException}, {@code AqlException}) before it escapes the storage
 * layer. The two mappers that can receive it — {@link AqlExceptionMapper} (AQL path) and
 * {@link GlobalExceptionMapper} (everything else) — both delegate here so the cause-chain detection
 * and the 400 response live in one place.
 * <p>
 * HTTP 400 (Bad Request) is used to signal that the request cannot be fulfilled as written — the query
 * would exceed the server's universal row limit. The client must narrow the query; a plain retry will
 * not help. This is consistent with how other AQL errors are surfaced in {@link AqlExceptionMapper}.
 *
 * @author Eli Fishman
 */
@Slf4j
@UtilityClass
public class BlockedQueryResponseHelper {

    /**
     * Guards against pathological or self-referential cause chains.
     */
    private static final int MAX_CAUSE_DEPTH = 20;

    /**
     * Builds an HTTP 400 response if a {@link QueryBlockedException} is present anywhere in the cause
     * chain of the given exception.
     *
     * @param exception the exception thrown out of the REST layer (possibly wrapping the blocked query)
     * @return a 400 {@link Response} when the chain contains a blocked-query exception, otherwise {@code null}
     */
    public static Response blockedQueryResponseOrNull(Throwable exception) {
        QueryBlockedException blocked = findBlockedQuery(exception);
        if (blocked == null) {
            return null;
        }
        log.warn("Query blocked by the universal row limit of {} (rows seen: {}); query: {}; returning HTTP 400.",
                blocked.getLimit(), blocked.getRowsCount(), blocked.getQuery());
        ErrorResponse errorResponse = new ErrorResponse(BAD_REQUEST.getStatusCode(), blocked.getMessage());
        return Response.status(BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(errorResponse).build();
    }

    private static QueryBlockedException findBlockedQuery(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < MAX_CAUSE_DEPTH) {
            if (current instanceof QueryBlockedException blocked) {
                return blocked;
            }
            current = current.getCause();
        }
        return null;
    }
}
