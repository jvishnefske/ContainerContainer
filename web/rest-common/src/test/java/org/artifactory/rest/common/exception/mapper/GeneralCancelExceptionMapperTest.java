package org.artifactory.rest.common.exception.mapper;

import com.jfrog.ph.acl.exception.PackageCancelException;
import org.apache.http.HttpStatus;
import org.artifactory.exception.CancelException;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import java.util.stream.Stream;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * @author Oded Balazada
 */
class GeneralCancelExceptionMapperTest {

    @ParameterizedTest
    @MethodSource("requestScenarios")
    void packageCancelExceptionToResponseTest(String expectedPrefix, String path) {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getContextPath()).thenReturn("");
        when(requestMock.getRequestURI()).thenReturn(path);
        PackageCancelException e =
                new PackageCancelException(HttpStatus.SC_FORBIDDEN, "some message", "internal message");
        PackageCancelExceptionMapper mapper = new PackageCancelExceptionMapper();
        mapper.setRequest(requestMock);
        try (Response response = mapper.toResponse(e)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(expectedPrefix + e.getMessage(),
                    errorResponse.getErrors().get(0).getMessage());
            assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }

    @ParameterizedTest
    @MethodSource("requestScenarios")
    void cancelExceptionToResponseTest(String expectedPrefix, String path) {
        HttpServletRequest requestMock = mock(HttpServletRequest.class);
        when(requestMock.getContextPath()).thenReturn("");
        when(requestMock.getRequestURI()).thenReturn(path);
        CancelException e =
                new CancelException("some message", HttpStatus.SC_FORBIDDEN);
        CancelExceptionMapper mapper = new CancelExceptionMapper();
        mapper.setRequest(requestMock);
        try (Response response = mapper.toResponse(e)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(expectedPrefix + e.getMessage(),
                    errorResponse.getErrors().get(0).getMessage());
            assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }

    static Stream<Arguments> requestScenarios() {
        return Stream.of(
                Arguments.of("Download request has been canceled: ", "repoKey/somePath/"),
                Arguments.of("Request has been canceled: ", "/api/plugins/execute/muym"),
                Arguments.of("Request has been canceled: ", "/api/plugins/build/promote/ehqt"),
                Arguments.of("Request has been canceled: ", "/api/storage/zvfd"),
                Arguments.of("Request has been canceled: ", "/api/copy/adfg"),
                Arguments.of("Request has been canceled: ", "/api/copy/asd")
        );
    }
}
