package org.artifactory.throtlling.qrl.enums;

import lombok.Getter;
import org.artifactory.throtlling.common.enums.IRateLimiterType;

/**
 * @author Andrey Tashlitsky
 */
@Getter
public enum QueryRateLimiterType implements IRateLimiterType {

    DEFAULT("Artifactory database queries have reached the set limit ({} per {} ms)." +
            " Throttling has been applied ({}% of the time) to protect system health."),
    LOW_PRIORITY("Artifactory database queries of low priority tasks have reached the set limit ({} per {} ms)." +
            " Throttling has been applied ({}% of the time) to protect system health."),
    SYSTEM("");

    private final String message;

    QueryRateLimiterType(String message) {
        this.message = message;
    }
}
