package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.jfrog.storage.hqc.exception.HeavyQueryCacheException;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.REQUEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Andrey Tashlitsky
 */
class HeavyQueryCacheExceptionMapperTest {

    @Test
    void toResponseTest() {
        HeavyQueryCacheException e = new HeavyQueryCacheException("cool-exception");
        HeavyQueryCacheExceptionMapper mapper = new HeavyQueryCacheExceptionMapper();
        try (Response response = mapper.toResponse(e)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(e.getMessage(), errorResponse.getErrors().get(0).getMessage());
            assertEquals(REQUEST_TIMEOUT.getStatusCode(), errorResponse.getErrors().get(0).getStatus());
            assertEquals(REQUEST_TIMEOUT.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }
}
