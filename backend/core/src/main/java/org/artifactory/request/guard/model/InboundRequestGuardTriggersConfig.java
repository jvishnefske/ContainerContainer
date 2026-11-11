package org.artifactory.request.guard.model;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * Rules that block an inbound HTTP request: any request that carries one of {@code headerNames} (case-insensitive
 * presence; the value is irrelevant) or whose {@code User-Agent} header contains one of {@code userAgentSubstrings}
 * (case-insensitive substring). {@link Mode#PFW3_OFF} disables the guard, {@link Mode#PFW3_PROBE} only logs the
 * would-be block, and {@link Mode#PFW3_ACTIVE} returns a 403 to the client.
 *
 * @author Hussein Alshqerat
 */
@Data
public class InboundRequestGuardTriggersConfig {

    public enum Mode {
        PFW3_OFF,
        PFW3_PROBE,
        PFW3_ACTIVE
    }

    /**
     * Default header names seeded into a fresh config (matched case-insensitively). Applied only when no triggers
     * config has been persisted yet — once an admin saves any config, their values fully replace these.
     */
    public static final Set<String> DEFAULT_HEADER_NAMES = Set.of(
            "x-socket-request-id"
    );

    private Mode mode = Mode.PFW3_PROBE;
    private Set<String> headerNames = new HashSet<>(DEFAULT_HEADER_NAMES);
    private Set<String> userAgentSubstrings = new HashSet<>();
    /**
     * Client IP addresses or CIDR ranges to block. Accepts both single IPs ({@code 192.0.2.5},
     * {@code 2001:db8::1}) and CIDR notation ({@code 10.0.0.0/8}, {@code 2001:db8::/32}). The client IP is resolved
     * via {@code HttpUtils.getRemoteClientAddress}, so {@code X-Forwarded-For} is honored when present.
     */
    private Set<String> ipAddresses = new HashSet<>();

    /** Wire-format mode name for call home and admin APIs ({@code null} when {@link #mode} is unset). */
    public String getModeName() {
        return mode != null ? mode.name() : null;
    }
}
