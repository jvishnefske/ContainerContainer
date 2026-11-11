package org.artifactory.ui.rest.model.subscriptions;

import lombok.Data;
import org.artifactory.customer.updates.UsageUpdateModel;

import jakarta.annotation.Nonnull;

/**
 * @author Omri Ziv
 */
@Data
public class BlockedUsage {
    private boolean trafficBlocked;
    private boolean storageBlocked;
    private boolean buildTimeBlocked;

    public BlockedUsage() {
    }

    public static BlockedUsage fromUsageModel(@Nonnull UsageUpdateModel usageUpdateModel) {
        boolean isTrafficBlocked = usageUpdateModel.getTraffic() != null &&
                UsageUpdateModel.StatusType.BLOCKED.getType()
                        .equalsIgnoreCase(usageUpdateModel.getTraffic().getStatus());
        boolean isStorageBlocked = usageUpdateModel.getStorage() != null &&
                UsageUpdateModel.StatusType.BLOCKED.getType()
                        .equalsIgnoreCase(usageUpdateModel.getStorage().getStatus());
        boolean isBuildTimeBlocked = usageUpdateModel.getBuildTime() != null &&
                UsageUpdateModel.StatusType.BLOCKED.getType()
                        .equalsIgnoreCase(usageUpdateModel.getBuildTime().getStatus());

        return new BlockedUsage(isTrafficBlocked, isStorageBlocked, isBuildTimeBlocked);
    }

    private BlockedUsage(boolean trafficBlocked, boolean storageBlocked, boolean buildTimeBlocked) {
        this.trafficBlocked = trafficBlocked;
        this.storageBlocked = storageBlocked;
        this.buildTimeBlocked = buildTimeBlocked;
    }
}
