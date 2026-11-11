package org.artifactory.throttling.lh.mode;

import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum representing the operational modes of the Load Healer.
 * {@link #PROACTIVE} - The Load Healer is fully enabled and operational.
 * {@link #REACTIVE} - The Load Healer operates in reactive mode with outage detection.
 * {@link #SIMULATION} - The Load Healer simulates actions without actually blocking requests.
 *
 * @author Hussein Alshqerat
 */
public enum LoadHealerMode {

    PROACTIVE,
    REACTIVE,
    SIMULATION;

    private static final Map<String, LoadHealerMode> MODE_MAP = new HashMap<>();

    static {
        for (LoadHealerMode mode : LoadHealerMode.values()) {
            MODE_MAP.put(mode.name().toLowerCase(), mode);
        }
        MODE_MAP.put("enabled", PROACTIVE); // backward-compatible alias
    }

    /**
     * Converts a string representation of the mode to the corresponding {@link LoadHealerMode} enum value.
     * The conversion is case-insensitive.
     *
     * @param mode the string representation of the mode
     * @return the corresponding {@link LoadHealerMode} enum value
     */
    public static LoadHealerMode fromString(@Nonnull String mode) {
        return MODE_MAP.getOrDefault(mode.toLowerCase(), REACTIVE);
    }
}