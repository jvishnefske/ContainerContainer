package org.artifactory.logging;

import lombok.Getter;

/**
 * @author Tomer Slutsky
 */
@Getter
public enum SaasLogNames {
    ACCESS_LOG("artifactory-access.log"),
    REQUEST_LOG("artifactory-request.log"),
    OUTBOUND_REQUEST_LOG("artifactory-request-out.log"),
    STORAGE_OUTBOUND_REQUEST_LOG("artifactory-storage-request-out.log"),
    SERVICE_FEDERATION_LOG("artifactory-service-federation.log"),
    SERVICE_LOG("artifactory-service.log"),
    CLEANUP_AUDIT_LOG("artifactory-cleanup-audit.log");

    private final String logKey;
    private final String logName;

    SaasLogNames(String logName) {
        this.logKey = "log_name";
        this.logName = logName;
    }
}
