package org.artifactory.throttling.lh.mode;

import java.time.Instant;

/**
 * @author Amir Avidan
 */
public record LoadHealerModeConfig(
        String currentMode,
        long expiresAtMs
) {
    public static LoadHealerModeConfig of(String currentMode, Instant expiresAt) {
        return new LoadHealerModeConfig(currentMode, expiresAt.toEpochMilli());
    }

    public boolean isExpired() {
        return expiresAtMs <= 0 || !Instant.now().isBefore(expiresAt());
    }

    public Instant expiresAt() {
        return Instant.ofEpochMilli(expiresAtMs);
    }
}
