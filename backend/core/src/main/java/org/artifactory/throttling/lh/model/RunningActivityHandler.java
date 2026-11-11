package org.artifactory.throttling.lh.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.artifactory.throttling.lh.service.LoadHealerService;

/**
 * Running activity handler model that represents the activity name, resource, and block status.
 * The handler is used to end the activity.
 *
 * @author Hussein Alshqerat
 */
@Data
@AllArgsConstructor
public class RunningActivityHandler implements AutoCloseable {

    public static final RunningActivityHandler nonBlockingActivityHandler =
            new RunningActivityHandler(null, null, null, false, false);

    private LoadHealerService loadHealer;
    private String activityName;
    private String resource;
    private boolean isBlocked;
    private boolean shouldDecreaseRunningActivities;


    @Override
    public void close() {
        if (loadHealer != null) {
            loadHealer.endActivity(this);
        }
    }
}