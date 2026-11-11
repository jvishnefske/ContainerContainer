package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * @author Anna Kotova
 */
class ConstraintViolationExceptionMapperTest {

    private final ConstraintViolationExceptionMapper mapper = new ConstraintViolationExceptionMapper();

    @ParameterizedTest
    @MethodSource("createConstraintViolationException")
    void shouldReturnBadRequestResponseWhenConstraintViolationExceptionOccurs(ConstraintViolationException exception) {
        Response response = mapper.toResponse(exception);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("error message", (((ErrorResponse) response.getEntity())).getErrors().get(0).getMessage());
    }

    @Test
    void shouldReturnBadRequestResponseWhenConstraintViolationExceptionWithoutDetailsOccurs() {
        ConstraintViolationException exception = new ConstraintViolationException("error message", null);

        Response response = mapper.toResponse(exception);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());
        assertEquals("error message", (((ErrorResponse) response.getEntity())).getErrors().get(0).getMessage());
    }

    static Stream<Arguments> createConstraintViolationException() {
        return Stream.of(
                Arguments.of(new ConstraintViolationException("error class: error message", null)),
                Arguments.of(new ConstraintViolationException("error message", null))
        );
    }
}
