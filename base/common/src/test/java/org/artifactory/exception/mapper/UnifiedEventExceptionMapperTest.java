package org.artifactory.exception.mapper;

import jakarta.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.extension.MediaType.APPLICATION_JSON;

public class UnifiedEventExceptionMapperTest {
    UnifiedEventExceptionMapper mapper = new UnifiedEventExceptionMapper();

    @ParameterizedTest
    @MethodSource("exceptionProvider")
    void testToResponse(UnifiedEventException exception) {
        try (Response response = mapper.toResponse(exception)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(exception.getMessage(), errorResponse.getErrors().get(0).getMessage());
            assertEquals(APPLICATION_JSON.toString(), response.getMediaType().toString());
        }
    }

    private static Stream<Arguments> exceptionProvider() {
        return Stream.of(
                Arguments.of(new UnifiedEventException("Another test error message")),
                Arguments.of(new UnifiedEventException(HttpStatus.SC_BAD_REQUEST, false, "Another test error message"))
        );
    }

}
