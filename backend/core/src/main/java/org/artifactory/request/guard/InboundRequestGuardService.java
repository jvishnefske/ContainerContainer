package org.artifactory.request.guard;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.request.guard.model.InboundRequestGuardTriggersConfig;
import org.artifactory.spring.ContextCreationListener;

/**
 * Blocks inbound HTTP requests that match an admin-configured header-name list or User-Agent substring list. Modes:
 * {@code OFF} (no checks), {@code SHADOW} (log would-be blocks), {@code ACTIVE} (return 403 to the client). The
 * config is persisted, cached in memory, and propagated across HA nodes after admin updates so peers enforce the
 * same rules immediately.
 *
 * @author Hussein Alshqerat
 */
public interface InboundRequestGuardService extends ContextCreationListener {

    /** Returns the current triggers config (mode + header names + user-agent substrings) from memory. */
    InboundRequestGuardTriggersConfig getInboundRequestGuardTriggersConfig();

    /** Persists the given triggers config, updates the in-memory cache, and propagates to peer HA nodes. */
    void updateTriggers(InboundRequestGuardTriggersConfig triggers);

    /**
     * Reloads the triggers config from the DB into the in-memory cache. Invoked on peer HA nodes after the writer
     * propagates an admin update.
     */
    void reloadTriggersFromDb();

    /**
     * Returns {@code true} when the request matches a configured trigger and the guard mode is {@code ACTIVE} —
     * callers should reject with a 403 and skip further processing. In {@code SHADOW} mode this method logs the
     * would-be block and returns {@code false}; in {@code OFF} mode it returns {@code false} without inspecting
     * anything.
     */
    boolean shouldBlock(HttpServletRequest request);
}
