package org.artifactory.service;

import org.artifactory.repo.interceptor.modle.RemoteRepoOfflineTriggersConfig;
import org.artifactory.spring.ContextCreationListener;

/**
 * Sets remote repositories offline when their URL contains a configured substring, or when the upstream response
 * exposes a configured header (sampled by
 * {@link org.artifactory.repo.remote.interceptor.RemoteRepoOutboundHeaderAuditService}). URL substring matching is
 * additionally rejected synchronously at create/update time by the config-change interceptor; the background scan
 * catches repos whose URL became unsupported after the substring was added or whose upstream began returning bad
 * headers. The guard only ever drives offline state in one direction (online → offline); restoring a
 * guard-offlined repo back to online is always an explicit admin action.
 *
 * @author Hussein Alshqerat
 */
public interface RemoteRepoOfflineGuardService extends ContextCreationListener {

    /** Returns the current triggers config (URL substrings and header names) from memory. */
    RemoteRepoOfflineTriggersConfig getRemoteRepoOfflineGuardTriggersConfig();

    /** Persists the given triggers config and updates the in-memory cache. */
    void updateTriggers(RemoteRepoOfflineTriggersConfig triggers);

    /**
     * Reloads the triggers config from the DB into the in-memory cache. Invoked on peer HA nodes after the writer
     * propagates an admin update so they pick up the new mode/triggers without waiting for the guard cron tick.
     */
    void reloadTriggersFromDb();

    /** Scans every remote repo and offlines any in-scope repo whose URL or upstream headers match the triggers. */
    void evaluate();

    /**
     * Hook for the repo update path: when a remote repo's URL changes, drop the audit-header samples tied to the
     * old URL so the cron header-detection logic starts fresh on the new upstream. Does not change the repo's
     * offline/online state — admin must re-online a guard-offlined repo explicitly.
     */
    void onRemoteRepoUrlChanged(String repoKey, String newUrl);

    /**
     * Returns {@code true} when an admin attempt to flip a remote repo from offline back to online must be rejected:
     * the guard had previously marked it offline (it is in the marked-repos set) and the active triggers (URL
     * substrings or recently-seen response headers) would still re-offline it. Returns {@code false} when the guard
     * is in a non-enforcing mode (PFW3_OFF / PFW3_PROBE), when the repo was not offlined by the guard, or when no
     * trigger still matches.
     *
     * @param repoKey the remote repository key being updated
     * @param newUrl  the URL the repo will have after this update completes
     */
    boolean isReonlineBlocked(String repoKey, String newUrl);

    /**
     * Returns {@code true} when this guard previously set the remote repo offline (i.e. the repo key is in the
     * marked-repos set). Used by the download path to differentiate guard-driven offline (which responds 403 to
     * upstream requests) from admin-driven offline (which keeps the existing 404 behavior). Does not re-evaluate
     * triggers and does not consider guard mode — the marked set is only ever populated in enforcing modes
     * ({@code PFW3_NEW} / {@code PFW3_ALL}).
     *
     * @param repoKey the remote repository key
     */
    boolean isOfflinedByGuard(String repoKey);

    /**
     * Returns {@code true} when a remote repo create operation must be rejected because the supplied URL matches a
     * configured URL substring and the guard is in an enforcing mode ({@code PFW3_NEW} / {@code PFW3_ALL}). A repo
     * key that appears in the configured whitelist is fully exempt from URL writes and returns {@code false}.
     * Returns {@code false} in {@code PFW3_OFF} / {@code PFW3_PROBE} or when the URL does not match.
     *
     * @param repoKey the remote repository key being created
     * @param url     the URL the new remote repo would point at
     */
    boolean isCreateBlocked(String repoKey, String url);

    /**
     * Returns {@code true} when a remote repo update that changes the URL must be rejected because the new URL
     * matches a configured URL substring and the guard is in an enforcing mode ({@code PFW3_NEW} / {@code PFW3_ALL}).
     * Returns {@code false} when the URL is unchanged, when the guard is non-enforcing, when the new URL does not
     * match, or when the repo is whitelisted.
     *
     * @param repoKey the remote repository key being updated
     * @param oldUrl  the URL before this update
     * @param newUrl  the URL after this update
     */
    boolean isUpdateUrlBlocked(String repoKey, String oldUrl, String newUrl);

    /**
     * Returns the number of remote repos currently marked offline by this guard, read fresh from the DB so call-home
     * collectors observe the cluster-wide picture rather than a local cache. Returns {@code 0} when no marker set
     * has been persisted yet.
     */
    int getOfflinedRepoCount();
}
