package com.jfrog.build.gateway;

import com.jfrog.build.acl.model.BuildAccessLogType;
import lombok.NonNull;

import static org.artifactory.security.AccessLogger.buildCreate;
import static org.artifactory.security.AccessLogger.buildCreateDenied;
import static org.artifactory.security.AccessLogger.buildDelete;
import static org.artifactory.security.AccessLogger.buildDeleteDenied;
import static org.artifactory.security.AccessLogger.buildPromote;
import static org.artifactory.security.AccessLogger.buildPromoteDenied;

public class BuildAccessGateway {

    public void logAccessEvent(
            @NonNull BuildAccessLogType type,
            @NonNull String buildRef) {
        switch (type) {
            case BUILD_CREATE -> buildCreate(buildRef);
            case BUILD_CREATE_DENIED -> buildCreateDenied(buildRef);
            case BUILD_PROMOTE -> buildPromote(buildRef);
            case BUILD_PROMOTE_DENIED -> buildPromoteDenied(buildRef);
            case BUILD_DELETE -> buildDelete(buildRef);
            case BUILD_DELETE_DENIED -> buildDeleteDenied(buildRef);
        }
    }
}
