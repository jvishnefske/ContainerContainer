package org.artifactory.repo.packagetype;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.artifactory.common.config.metadata.MetadataEnum;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlEnumValue;

/**
 * @author Yoav Landman
 */
@GenerateDiffFunction
public enum SnapshotVersionBehavior implements MetadataEnum {
    @XmlEnumValue("unique") @JsonProperty("UNIQUE") UNIQUE("Unique"),
    @XmlEnumValue("non-unique") @JsonProperty("NONUNIQUE") NONUNIQUE("Non-unique"),
    @XmlEnumValue("deployer") @JsonProperty("DEPLOYER") DEPLOYER("Deployer");

    //The name to display when used in different components
    private String displayName;

    /**
     * Sets the display name of the element
     *
     * @param displayName The display name
     */
    SnapshotVersionBehavior(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the display name of the element
     *
     * @return String - Element display name
     */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName.toLowerCase();
    }

    @Override
    public String getConfigName() {
        return displayName;
    }

    public static SnapshotVersionBehavior fromString(String message) {
        for (SnapshotVersionBehavior snapshotVersionBehavior : SnapshotVersionBehavior.values()) {
            if (snapshotVersionBehavior.displayName.equalsIgnoreCase(message)) {
                return snapshotVersionBehavior;
            }
        }
        return null;
    }
}