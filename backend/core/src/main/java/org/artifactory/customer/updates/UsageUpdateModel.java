package org.artifactory.customer.updates;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Omri Ziv
 */
@Data
@NoArgsConstructor
public class UsageUpdateModel {
    UsageUpdateItem storage;
    UsageUpdateItem traffic;
    UsageUpdateItem buildTime;

    public enum StatusType {
        NORMAL("normal"),
        WARNING("warning"),
        BLOCKED("blocked");

        private String type;

        StatusType(String statusType) {
            this.type = statusType;
        }

        public String getType() {
            return type;
        }
    }

    public boolean hasWarningReached() {
        return (traffic != null && traffic.getStatus().equalsIgnoreCase(StatusType.WARNING.type)) ||
                (storage != null && storage.getStatus().equalsIgnoreCase(StatusType.WARNING.type)) ||
                (buildTime != null && buildTime.getStatus().equalsIgnoreCase(StatusType.WARNING.type));
    }

    public boolean hasLimitReached() {
        return (traffic != null && traffic.getStatus().equalsIgnoreCase(StatusType.BLOCKED.type)) ||
                (storage != null && storage.getStatus().equalsIgnoreCase(StatusType.BLOCKED.type)) ||
                (buildTime != null && buildTime.getStatus().equalsIgnoreCase(StatusType.BLOCKED.type));
    }
}
