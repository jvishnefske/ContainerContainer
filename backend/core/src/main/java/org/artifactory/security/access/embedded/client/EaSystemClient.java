/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.jfrog.access.client.system.federation.FederationClient;
import org.jfrog.access.common.ServiceId;
import org.jfrog.common.platform.SystemServiceInfo;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * The {@code system()} surface. Embedded access is always up, so there is no ping retry budget, and it identifies
 * itself by {@link EmbeddedAccessClient#SERVER_NAME} and {@link EmbeddedAccessClient#VERSION}.
 * <p>
 * Export and import of the Access server are not implemented and fail closed: embedded access data lives in the
 * Artifactory database and is backed up with it. Federation and support bundles fail closed too.
 */
final class EaSystemClient {

    private final EaRuntime rt;
    private final FederationClient federation = FailClosed.of(FederationClient.class, "system.federation");

    EaSystemClient(EaRuntime rt) {
        this.rt = rt;
    }

    /**
     * Returns the server name.
     *
     * @return {@link EmbeddedAccessClient#SERVER_NAME}
     */
    public String getAccessServerName() {
        return EmbeddedAccessClient.SERVER_NAME;
    }

    /**
     * Returns the Access service id.
     *
     * @return {@code jfac@<id>}
     */
    public ServiceId getAccessServiceId() {
        return rt.accessServiceId();
    }

    /**
     * Describes the service: the Access service id, type {@code jfac}, the URL {@code embedded:} plus
     * {@link EmbeddedAccessClient#VERSION}, and the time zone of the client's clock.
     *
     * @return the service information
     */
    public SystemServiceInfo getServiceInfo() {
        ZonedDateTime now = ZonedDateTime.now(rt.clock());
        ZoneOffset offset = now.getOffset();
        return new SystemServiceInfo(rt.accessServiceId().getFormattedName(), rt.accessServiceId().getServiceType(),
                "embedded:" + EmbeddedAccessClient.VERSION, offset.getId(), now.getZone().getId());
    }

    /**
     * Returns the federation surface, which refuses every call.
     *
     * @return the refusing surface
     */
    public FederationClient federation() {
        return federation;
    }

    /**
     * Returns the startup ping retries: none, because embedded access is in process.
     *
     * @return {@code 0}
     */
    public int getStartupPingRetries() {
        return 0;
    }
}
