package org.artifactory.rest.common.exception.mapper;

import com.jfrog.bh.acl.error.BundleBadRequestException;
import com.jfrog.bh.acl.error.base.BundleException;
import com.jfrog.bh.acl.error.group.BundleMultiException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BundleExceptionMapperTest {

    private final BundleExceptionMapper exceptionMapper = new BundleExceptionMapper();

    @Test
    void toResponse_Success() {
        String message = "test";
        BundleException e = new BundleBadRequestException(message);
        Response response = exceptionMapper.toResponse(e);

        assertEquals(SC_BAD_REQUEST, response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());

        ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
        assertEquals(1, errorResponse.getErrors().size());

        ErrorResponse.Error error = errorResponse.getErrors().getFirst();
        assertEquals(message, error.getMessage());
        assertEquals(SC_BAD_REQUEST, error.getStatus());
    }

    @Test
    void toResponse_Success_MultiException() {
        String messageA = "test-A";
        String messageB = "test-B";
        List<Pair<Integer, String>> statusesWithMessages = List.of(
                Pair.of(SC_BAD_REQUEST, messageA),
                Pair.of(SC_FORBIDDEN, messageB)
        );
        BundleMultiException e = mock(BundleMultiException.class);
        when(e.getStatusesWithMessages()).thenReturn(statusesWithMessages);
        when(e.getStatusCode()).thenReturn(SC_BAD_REQUEST);
        Response response = exceptionMapper.toResponse(e);

        assertEquals(SC_BAD_REQUEST, response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());

        ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
        assertEquals(2, errorResponse.getErrors().size());

        ErrorResponse.Error errorA = errorResponse.getErrors().getFirst();
        assertEquals(messageA, errorA.getMessage());
        assertEquals(SC_BAD_REQUEST, errorA.getStatus());

        ErrorResponse.Error errorB = errorResponse.getErrors().get(1);
        assertEquals(messageB, errorB.getMessage());
        assertEquals(SC_FORBIDDEN, errorB.getStatus());
    }
}