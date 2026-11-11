/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.artifactory.addon.plugin.pkgtype.impl.PluginTypeTestRepos.config;
import static org.artifactory.addon.plugin.pkgtype.impl.PluginTypeTestRepos.localRepo;
import static org.artifactory.addon.plugin.pkgtype.impl.PluginTypeTestRepos.nonLocalRepo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Request-time lookup: registered, unregistered (R6), non-plugin and non-local repositories. */
class PluginPackageTypeResolverTest {

    private final PluginPackageTypeRegistryImpl registry = new PluginPackageTypeRegistryImpl();
    private final PluginPackageTypeResolver resolver = new PluginPackageTypeResolver(registry);

    @Test
    void registeredTypeIsRoutableForLocalRepo() {
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("simple", "p").build()));
        assertEquals("simple", resolver.routableTypeOf(localRepo("r", "simple")).orElseThrow().name());
    }

    @Test
    void unregisteredTypeServesAsGenericAndIsReportedOnce() {
        assertTrue(resolver.routableTypeOf(localRepo("r", "gone")).isEmpty());
        assertTrue(resolver.routableTypeOf(localRepo("r", "gone")).isEmpty());
        assertInstanceOf(PluginTypeResolution.Unregistered.class, resolver.resolve(config("r", RepoType.Generic,
                "gone")));
        assertEquals(1, resolver.reportedUnregisteredCount());
    }

    @Test
    void nonGenericOrUntypedRepoIsNotPluginTyped() {
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("simple", "p").build()));
        assertInstanceOf(PluginTypeResolution.NotPluginTyped.class,
                resolver.resolve(config("m", RepoType.Maven, "simple")));
        assertInstanceOf(PluginTypeResolution.NotPluginTyped.class,
                resolver.resolve(config("g", RepoType.Generic, null)));
        assertInstanceOf(PluginTypeResolution.NotPluginTyped.class, resolver.resolve(null));
    }

    @Test
    void remoteOrVirtualRepoWithTheKeyIsNotRouted() {
        registry.replacePluginTypes("p", List.of(PluginPackageType.builder("simple", "p").build()));
        assertTrue(resolver.routableTypeOf(nonLocalRepo("remote", "simple")).isEmpty());
        assertTrue(resolver.routableTypeOf(null).isEmpty());
    }
}
