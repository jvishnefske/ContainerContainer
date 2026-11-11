package org.artifactory.ph.mapper;

import com.jfrog.ph.acl.exception.PackageBadRequestException;
import com.jfrog.ph.acl.exception.PackageConflictException;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageForbiddenException;
import com.jfrog.ph.acl.exception.PackageInternalServerError;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.exception.PackageNotModifiedException;
import com.jfrog.ph.acl.exception.PackageUnauthorizedException;
import com.jfrog.ph.acl.exception.PackageUnsupportedOperationException;
import org.apache.http.HttpStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.artifactory.ph.mapper.PhExceptionMapper.throwPackageException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhExceptionMapperTest {

    @ParameterizedTest
    @MethodSource("testData")
    void testHttpStatusCodes(int statusCode, String statusMsg, Class<PackageException> expectedException) {
        PackageException packageException =
                assertThrows(expectedException, () -> throwPackageException(statusCode, statusMsg));
        assertEquals(statusMsg, packageException.getInternalReason());
    }

    private static Stream<Arguments> testData() {
        return Stream.of(
                Arguments.of(HttpStatus.SC_NOT_MODIFIED, "", PackageNotModifiedException.class),
                Arguments.of(HttpStatus.SC_BAD_REQUEST, "Bad Request", PackageBadRequestException.class),
                Arguments.of(HttpStatus.SC_UNAUTHORIZED, "Unauthorized", PackageUnauthorizedException.class),
                Arguments.of(HttpStatus.SC_FORBIDDEN, "Forbidden", PackageForbiddenException.class),
                Arguments.of(HttpStatus.SC_NOT_FOUND, "Not Found", PackageNotFoundException.class),
                Arguments.of(HttpStatus.SC_CONFLICT, "Conflict", PackageConflictException.class),
                Arguments.of(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error",
                        PackageInternalServerError.class),
                Arguments.of(HttpStatus.SC_NOT_IMPLEMENTED, "Not Implemented",
                        PackageUnsupportedOperationException.class),
                Arguments.of(HttpStatus.SC_METHOD_NOT_ALLOWED, "Not Allowed", PackageException.class)
        );
    }
}