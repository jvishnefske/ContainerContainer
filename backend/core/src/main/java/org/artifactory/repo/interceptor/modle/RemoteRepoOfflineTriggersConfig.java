package org.artifactory.repo.interceptor.modle;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * Rules that mark a remote repository offline: any URL containing one of {@code urlSubstrings},
 * or any upstream response that exposes one of {@code headerNames} (case-insensitive).
 * <ul>
 *     <li>{@link Mode#PFW3_OFF} — guard disabled.</li>
 *     <li>{@link Mode#PFW3_PROBE} — detection only, logs would-be actions, no state change.</li>
 *     <li>{@link Mode#PFW3_NEW} — URL writes (create/update) with an unsupported URL are rejected for all repos;
 *     the cron offlines only repos created on or after {@code initEpochMillis} whose upstream headers match. URL
 *     is not considered by the cron in this mode (no retroactive URL offlining).</li>
 *     <li>{@link Mode#PFW3_ALL} — URL writes with an unsupported URL are rejected for all repos; the cron
 *     offlines any repo whose URL or upstream headers match, regardless of creation date.</li>
 * </ul>
 * {@code initEpochMillis} is auto-stamped to {@code System.currentTimeMillis()} on the first transition into
 * {@link Mode#PFW3_NEW} when null; an admin-supplied value in the payload is respected and never overwritten.
 * Fresh installs default to {@link Mode#PFW3_PROBE} until an admin persists a different mode via REST.
 *
 * @author Hussein Alshqerat
 */
@Data
public class RemoteRepoOfflineTriggersConfig {

    public enum Mode {
        PFW3_OFF,
        PFW3_PROBE,
        PFW3_NEW,
        PFW3_ALL
    }

    /**
     * Default header names seeded into a fresh config (matched case-insensitively). Applied only when no triggers
     * config has been persisted yet — once an admin saves any config, their values fully replace these.
     */
    public static final Set<String> DEFAULT_HEADER_NAMES = Set.of(
            "x-socket-decision",
            "x-socket-request-id",
            "x-socket-block-reason",
            "x-socket-warn-reason",
            "x-socket-monitor-reason",
            "x-socket-unscanned"
    );

    /**
     * Default URL substrings seeded into a fresh config (matched case-insensitively against the remote repo URL).
     * Applied only when no triggers config has been persisted yet — once an admin saves any config, their values
     * fully replace these.
     */
    public static final Set<String> DEFAULT_URL_SUBSTRINGS = Set.of(
            "firewall.socket.dev",
            "endorlabs.com"
    );

    private Mode mode = Mode.PFW3_PROBE;
    private Set<String> urlSubstrings = new HashSet<>(DEFAULT_URL_SUBSTRINGS);
    private Set<String> headerNames = new HashSet<>(DEFAULT_HEADER_NAMES);
    /**
     * Repos in this set are fully exempt from the guard: they will not be flipped offline on URL or header match,
     * and URL create/update writes pointing at an unsupported URL are also allowed through in
     * {@link Mode#PFW3_NEW}/{@link Mode#PFW3_ALL}. Repo keys are case-sensitive, unlike URL substrings and header
     * names.
     */
    private Set<String> whitelistedRepoKeys = new HashSet<>();

    /**
     * Epoch milliseconds dividing "existing" repos (created before this instant) from "new" repos (created at or
     * after). Used only in {@link Mode#PFW3_NEW} to scope the header-driven offline action to new repos. Auto-set
     * on the first transition into {@link Mode#PFW3_NEW} when null; once set it is preserved unless the admin
     * supplies an explicit value in a triggers update payload.
     */
    private Long initEpochMillis;

    /** Wire-format mode name for call home and admin APIs ({@code null} when {@link #mode} is unset). */
    public String getModeName() {
        return mode != null ? mode.name() : null;
    }
}
