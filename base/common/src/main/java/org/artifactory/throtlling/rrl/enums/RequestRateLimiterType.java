package org.artifactory.throtlling.rrl.enums;

import lombok.Getter;
import org.artifactory.throtlling.common.enums.IRateLimiterType;

/**
 * @author Igor Gumush
 */
public enum RequestRateLimiterType implements IRateLimiterType {

    OTHER_REQUEST_OUT("Other", "none"),
    DOCKERHUB_REQUEST_REMOTE_REPO("Dockerhub", "docker.io"),
    QUAY_REQUEST_REMOTE_REPO("Quay", "quay.io"),
    GCR_REQUEST_REMOTE_REPO("GCR", "gcr.io");

    @Getter
    private final String message;

    @Getter
    private final String domain;

    RequestRateLimiterType(String message, String domain) {
        this.message = message;
        this.domain = domain;
    }
}
