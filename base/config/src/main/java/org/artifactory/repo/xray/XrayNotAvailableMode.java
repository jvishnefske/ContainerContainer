package org.artifactory.repo.xray;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.xml.bind.annotation.XmlEnum;
import lombok.Getter;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * @author Amir Avidan
 */
@Getter
@XmlEnum
@GenerateDiffFunction(typeMethod = "getValue")
public enum XrayNotAvailableMode {
    /**
     * Xray is not available, and Artifactory will allow all requests to the repository
     */
    FAIL_OPEN("fail_open"),

    /**
     * Xray is not available, and Artifactory will block all requests to the repository
     */
    FAIL_CLOSE("fail_close"),

    /**
     * Xray is not available, and Artifactory will determine the behavior based on the system property
     * allowOperationsXrayUnavailable.
     */
    USE_SYSTEM_PROPERTY("use_system_property");

    private final String value;

    XrayNotAvailableMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static XrayNotAvailableMode fromValue(String value) {
        for (XrayNotAvailableMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }

    public static final String XRAY_NOT_AVAILABLE_MODE = "xrayNotAvailableMode";
}
