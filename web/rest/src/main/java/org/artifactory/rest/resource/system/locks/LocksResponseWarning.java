package org.artifactory.rest.resource.system.locks;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured warning entry in the {@code warnings[]} array of a locks API response.
 * <p>
 * Known codes:
 * <ul>
 *   <li>{@code HA_AUTH_FAN_OUT_SKIPPED} — cluster fan-out is unavailable in HA-Authentication mode;
 *       only the responding node's locks are included.</li>
 * </ul>
 */
public class LocksResponseWarning {

    public static final String CODE_HA_AUTH_FAN_OUT_SKIPPED = "HA_AUTH_FAN_OUT_SKIPPED";

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    public LocksResponseWarning() {
    }

    public LocksResponseWarning(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
