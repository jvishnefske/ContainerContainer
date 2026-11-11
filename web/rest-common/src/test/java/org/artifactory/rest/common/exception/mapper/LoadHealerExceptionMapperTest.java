package org.artifactory.rest.common.exception.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.exception.LoadHealerException;
import org.junit.jupiter.api.Test;

import static org.approvaltests.Approvals.verify;

/**
 * Tests for {@link LoadHealerExceptionMapper}.
 *
 * @author Hussein Alshqerat
 */
class LoadHealerExceptionMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toResponseTest() throws JsonProcessingException {
        LoadHealerException e = new LoadHealerException("cool-exception");
        LoadHealerExceptionMapper mapper = new LoadHealerExceptionMapper();
        try (Response response = mapper.toResponse(e)) {
            verify(responseToJson(response));
        }
    }

    private String responseToJson(Response response) throws JsonProcessingException {
        ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
        return objectMapper.writeValueAsString(errorResponse);
    }
}