package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.QueryBlockedException;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLTimeoutException;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class AqlExceptionMapperTest {

    private AqlExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AqlExceptionMapper();
    }

    private ErrorResponse errorEntity(Response response) {
        assertNotNull(response.getEntity(), "Response entity must not be null");
        return (ErrorResponse) response.getEntity();
    }

    private void assertJsonContentType(Response response) {
        assertEquals(MediaType.APPLICATION_JSON, response.getMediaType().toString());
    }

    @Test
    void testToResponseWhenExceptionHasNoCause() {
        final AqlException exception = new AqlException("Test AQL error");
        final Response response = mapper.toResponse(exception);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertJsonContentType(response);
        ErrorResponse error = errorEntity(response);
        assertEquals(1, error.getErrors().size());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), error.getErrors().get(0).getStatus());
        assertEquals("Test AQL error", error.getErrors().get(0).getMessage());
    }

    @Test
    void testToResponseWhenCauseIsSQLTimeoutException() {
        final SQLTimeoutException timeoutException = new SQLTimeoutException("Query timeout occurred");
        final AqlException exception = new AqlException("AQL query failed", timeoutException);
        final Response response = mapper.toResponse(exception);

        assertEquals(Response.Status.REQUEST_TIMEOUT.getStatusCode(), response.getStatus());
        assertJsonContentType(response);
        ErrorResponse error = errorEntity(response);
        assertEquals(1, error.getErrors().size());
        assertEquals(Response.Status.REQUEST_TIMEOUT.getStatusCode(), error.getErrors().get(0).getStatus());
        assertEquals("Query timeout occurred", error.getErrors().get(0).getMessage());
    }

    @Test
    void testToResponseWhenCauseMessageContainsCancel() {
        final RuntimeException cancelException = new RuntimeException("Operation was cancelled by user");
        final AqlException exception = new AqlException("AQL query failed", cancelException);
        final Response response = mapper.toResponse(exception);

        assertEquals(Response.Status.REQUEST_TIMEOUT.getStatusCode(), response.getStatus());
        assertJsonContentType(response);
        ErrorResponse error = errorEntity(response);
        assertEquals(1, error.getErrors().size());
        assertEquals(Response.Status.REQUEST_TIMEOUT.getStatusCode(), error.getErrors().get(0).getStatus());
        assertEquals("Operation was cancelled by user", error.getErrors().get(0).getMessage());
    }

    @Test
    void testToResponseWhenCauseIsGenericException() {
        final RuntimeException genericException = new RuntimeException("Database connection error");
        final AqlException exception = new AqlException("AQL execution failed", genericException);
        final String expectedMessage = "AQL execution failed\ncause: Database connection error";
        final Response response = mapper.toResponse(exception);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertJsonContentType(response);
        ErrorResponse error = errorEntity(response);
        assertEquals(1, error.getErrors().size());
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), error.getErrors().get(0).getStatus());
        assertEquals(expectedMessage, error.getErrors().get(0).getMessage());
    }

    @Test
    void testToResponseWhenCauseHasNullMessage() {
        final RuntimeException nullMessageException = new RuntimeException((String) null);
        final AqlException exception = new AqlException("AQL query failed", nullMessageException);
        final Response response = mapper.toResponse(exception);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertJsonContentType(response);
        ErrorResponse error = errorEntity(response);
        assertEquals(1, error.getErrors().size());
        assertEquals("AQL query failed\ncause: null", error.getErrors().get(0).getMessage());
    }

    @Test
    void testToResponseWhenCancelInMiddleOfMessage() {
        final RuntimeException cancelException = new RuntimeException("Query execution was cancelled due to timeout");
        final AqlException exception = new AqlException("AQL query failed", cancelException);
        final Response response = mapper.toResponse(exception);

        assertEquals(Response.Status.REQUEST_TIMEOUT.getStatusCode(), response.getStatus());
        assertJsonContentType(response);
        ErrorResponse error = errorEntity(response);
        assertEquals(1, error.getErrors().size());
        assertEquals("Query execution was cancelled due to timeout", error.getErrors().get(0).getMessage());
    }

    @Test
    void testToResponse_WhenExceptionMessageIsNull_ShouldHandleGracefully() {
        final AqlException exception = new AqlException((String) null);
        final Response response = mapper.toResponse(exception);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertJsonContentType(response);
        ErrorResponse error = errorEntity(response);
        assertEquals(1, error.getErrors().size());
        assertEquals("", error.getErrors().get(0).getMessage());
    }

    @Test
    void testToResponseWhenCauseIsBlockedQueryReturnsBadRequest() {
        final QueryBlockedException blocked =
                new QueryBlockedException(250000L, 100000, "SELECT * FROM nodes");
        final AqlException exception = new AqlException("AQL query failed", blocked);
        final Response response = mapper.toResponse(exception);

        assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
        assertJsonContentType(response);
        ErrorResponse error = errorEntity(response);
        assertEquals(1, error.getErrors().size());
        assertEquals(BAD_REQUEST.getStatusCode(), error.getErrors().get(0).getStatus());
        assertEquals(blocked.getMessage(), error.getErrors().get(0).getMessage());
    }

    @Test
    void testToResponseWhenBlockedQueryIsDeeperInCauseChainReturnsBadRequest() {
        final QueryBlockedException blocked =
                new QueryBlockedException(180000L, 100000, "SELECT * FROM nodes");
        final RuntimeException storage = new RuntimeException("Could not execute query", blocked);
        final AqlException exception = new AqlException("AQL query failed", storage);
        final Response response = mapper.toResponse(exception);

        assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
        ErrorResponse error = errorEntity(response);
        assertEquals(blocked.getMessage(), error.getErrors().get(0).getMessage());
    }

    @Test
    void testToResponseWhenBothExceptionAndCauseMessagesAreNull() {
        final RuntimeException nullCause = new RuntimeException((String) null);
        final AqlException exception = new AqlException(null, nullCause);

        final Response response = mapper.toResponse(exception);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertJsonContentType(response);
        ErrorResponse error = errorEntity(response);
        assertEquals(1, error.getErrors().size());
        assertEquals("null\ncause: null", error.getErrors().get(0).getMessage());
    }
}
