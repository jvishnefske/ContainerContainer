/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.util;

import org.artifactory.descriptor.repo.ReverseProxyMethod;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/** A2: how {@code /v2} requests reach the plugin HTTP API. */
public class DockerInternalRewritePluginApiTest extends ArtifactoryHomeBoundTest {

    @Test
    public void pingWithAndWithoutTrailingSlashLandsOnTheDockerPing() {
        ReverseProxyMethod method = ReverseProxyMethod.REPOPATHPREFIX;
        assertEquals("/api/docker/v2/", DockerInternalRewrite.getInternalRewrite("localhost", "/v2/", method));
        assertEquals("/api/docker/v2/", DockerInternalRewrite.getInternalRewrite("localhost",
                DockerInternalRewrite.pingPath("/v2", true), method));
        assertNull(DockerInternalRewrite.getInternalRewrite("localhost",
                DockerInternalRewrite.pingPath("/v2", false), method), "stock: /v2 is not rewritten");
        assertEquals(DockerInternalRewrite.pingPath("/v2/x", true), "/v2/x");
        assertNull(DockerInternalRewrite.getInternalRewrite("localhost", "/v2x", method));
        assertEquals("/api/docker/images/v2/library/alpine/manifests/3.20", DockerInternalRewrite.getInternalRewrite(
                "reg.example.com", "/v2/images/library/alpine/manifests/3.20", method));
    }

    @Test
    public void withoutReverseProxyConfigurationAPluginApiSwitchesToRepoPathPrefix() {
        AtomicInteger configuredLookups = new AtomicInteger();
        ReverseProxyMethod chosen = DockerInternalRewrite.effectiveMethod(false, () -> true, () -> {
            configuredLookups.incrementAndGet();
            return ReverseProxyMethod.SUBDOMAIN;
        });
        assertEquals(ReverseProxyMethod.REPOPATHPREFIX, chosen);
        assertEquals(0, configuredLookups.get());
        assertEquals(ReverseProxyMethod.SUBDOMAIN,
                DockerInternalRewrite.effectiveMethod(false, () -> false, () -> ReverseProxyMethod.SUBDOMAIN),
                "no plugin HTTP API: stock default");
        assertEquals(ReverseProxyMethod.SUBDOMAIN,
                DockerInternalRewrite.effectiveMethod(true, () -> true, () -> ReverseProxyMethod.SUBDOMAIN),
                "a configured reverse proxy wins");
    }

    @Test
    public void stockSubdomainDefaultWouldMisrouteDottedHostsAndSkipLocalhost() {
        ReverseProxyMethod stockDefault = ReverseProxyMethod.SUBDOMAIN;
        assertEquals("/api/docker/reg/v2/images/alpine/manifests/1",
                DockerInternalRewrite.getInternalRewrite("reg.example.com", "/v2/images/alpine/manifests/1",
                        stockDefault));
        assertNull(DockerInternalRewrite.getInternalRewrite("localhost", "/v2/", stockDefault));
    }
}
