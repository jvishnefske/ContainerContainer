package org.artifactory.rest.common.exception.mapper;

import com.jfrog.ph.acl.exception.PackageCancelException;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.ext.Provider;

/**
 * Similar behaviour of {@link CancelExceptionMapper} only for PH specific exception
 */

@Component
@Provider
public class PackageCancelExceptionMapper extends CancelExceptionMapperBase<PackageCancelException> {

    @Override
    int getErrorCode(PackageCancelException exception) {
        return exception.getStatus();
    }
}
