package org.artifactory.service;

import org.artifactory.repo.interceptor.modle.AllowedRemoteUrlsConfig;
import org.artifactory.spring.ContextCreationListener;

/**
 * Service responsible for managing the allowed remote URLs configuration.
 * It fetches the allowlist, persists it in the database, and exposes it
 * for validation during repository create/edit and runtime downloads.
 *
 * @author Yuval Ohayon
 */
public interface ProhibitedUrlsService extends ContextCreationListener {

    /**
     * Fetches the latest allowed remote URLs configuration from
     * the remote source, updates the DB and in-memory cache,
     * and enforces offline mode on violating repositories.
     */
    void fetchAllowedRemoteUrls();

    /**
     * Reloads the allowed remote URLs config from the DB into memory.
     * Called on HA nodes when another node has fetched and persisted an update.
     */
    void syncAllowedRemoteUrls();

    /**
     * Returns the current in-memory allowed remote URLs config.
     *
     * @return the config, or {@code null} if not yet loaded
     */
    AllowedRemoteUrlsConfig getAllowedRemoteUrlsConfig();
}
