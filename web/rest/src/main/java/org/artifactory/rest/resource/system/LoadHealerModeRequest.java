package org.artifactory.rest.resource.system;

import org.artifactory.throttling.lh.mode.LoadHealerMode;

/**
 * @author Amir Avidan
 */
public record LoadHealerModeRequest(
        LoadHealerMode mode,
        int durationMinutes
) {
    private static final int MAX_DURATION_MINUTES = 4320; // 3 days
    private static final int DEFAULT_DURATION_MINUTES = 1440; // 1 day

    public LoadHealerModeRequest {
        if (durationMinutes > MAX_DURATION_MINUTES) {
            durationMinutes = MAX_DURATION_MINUTES;
        }
        if (durationMinutes <= 0) {
            durationMinutes = DEFAULT_DURATION_MINUTES;
        }
    }
}
