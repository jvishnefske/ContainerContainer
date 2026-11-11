package org.artifactory.repo.packagetype;

import jakarta.xml.bind.annotation.XmlEnumValue;

/**
 * @author Tamar Rosen
 */
public enum PomCleanupPolicy {
    @XmlEnumValue("discard_active_reference") discard_active_reference("discard_active_reference",
            "Discard Active Reference"),
    @XmlEnumValue("discard_any_reference") discard_any_reference("discard_any_reference", "Discard Any Reference"),
    @XmlEnumValue("nothing") nothing("nothing", "Nothing");

    private final String type;
    private final String message;

    PomCleanupPolicy(String type, String message) {
        this.type = type;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public String getType() {
        return type;
    }
}
