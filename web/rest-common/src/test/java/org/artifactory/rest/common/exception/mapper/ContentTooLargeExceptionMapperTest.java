package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.exception.http.ContentTooLargeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContentTooLargeExceptionMapperTest {

    @Test
    void mapsExceptionTo413JsonErrorResponse() {
        ContentTooLargeExceptionMapper mapper = new ContentTooLargeExceptionMapper();
        String message = "Payload too large for build promotion";
        Response response = mapper.toResponse(new ContentTooLargeException(message));

        assertNotNull(response);
        assertEquals(413, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, response.getMediaType().toString());

        Object entity = response.getEntity();
        assertNotNull(entity);
        assertInstanceOf(ErrorResponse.class, entity);
        ErrorResponse error = (ErrorResponse) entity;
        assertEquals(1, error.getErrors().size());
        assertEquals(413, error.getErrors().getFirst().getStatus());
        assertEquals(message, error.getErrors().getFirst().getMessage());
    }
}



