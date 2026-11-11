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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.http.HttpStatus;

import java.util.Map;

/**
 * Mapper from Http Status Code to PackageException
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PhExceptionMapper {

    public static void throwPackageException(int statusCode, String statusMsg) throws PackageException {
        throw switch (statusCode) {
            case HttpStatus.SC_NOT_MODIFIED -> new PackageNotModifiedException(Map.of());
            case HttpStatus.SC_BAD_REQUEST -> new PackageBadRequestException(statusMsg);
            case HttpStatus.SC_UNAUTHORIZED -> new PackageUnauthorizedException(statusMsg);
            case HttpStatus.SC_FORBIDDEN -> new PackageForbiddenException(statusMsg);
            case HttpStatus.SC_NOT_FOUND -> new PackageNotFoundException(statusMsg);
            case HttpStatus.SC_CONFLICT -> new PackageConflictException(statusMsg);
            case HttpStatus.SC_INTERNAL_SERVER_ERROR -> new PackageInternalServerError(statusMsg);
            case HttpStatus.SC_NOT_IMPLEMENTED -> new PackageUnsupportedOperationException(statusMsg);
            default -> new PackageException(statusCode, statusMsg, statusMsg);
        };
    }
}
