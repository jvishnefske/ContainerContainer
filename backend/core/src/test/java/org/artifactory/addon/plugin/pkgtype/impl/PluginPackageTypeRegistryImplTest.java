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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registry rules: name validation, all-or-nothing batches, ownership and collisions (R1 registry side). */
class PluginPackageTypeRegistryImplTest {

    private final PluginPackageTypeRegistryImpl registry = new PluginPackageTypeRegistryImpl();

    private static PluginPackageType type(String name, String plugin) {
        return PluginPackageType.builder(name, plugin).download((req, path, resp) -> false).build();
    }

    private Set<String> names() {
        return registry.all().stream().map(PluginPackageType::name).collect(Collectors.toSet());
    }

    @Test
    void registersValidTypesAndFindsThem() {
        assertEquals(List.of(), registry.replacePluginTypes("p1", List.of(type("alpha", "p1"), type("beta", "p1"))));
        assertEquals(Set.of("alpha", "beta"), names());
        assertEquals("p1", registry.find("alpha").orElseThrow().pluginName());
        assertTrue(registry.find("gamma").isEmpty());
        assertTrue(registry.find(null).isEmpty());
    }

    @Test
    void invalidNameRejectsWholeBatch() {
        registry.replacePluginTypes("p1", List.of(type("alpha", "p1")));
        List<String> reasons = registry.replacePluginTypes("p1", List.of(type("beta", "p1"), type("Bad_Name", "p1")));
        assertEquals(1, reasons.size());
        assertTrue(reasons.get(0).contains("Bad_Name"), reasons.toString());
        assertEquals(Set.of("alpha"), names(), "previous registration must stay untouched");
    }

    @Test
    void builtInNameCollisionRejected() {
        List<String> reasons = registry.replacePluginTypes("p1", List.of(type("maven", "p1"), type("ok", "p1")));
        assertFalse(reasons.isEmpty());
        assertTrue(reasons.get(0).contains("built-in"), reasons.toString());
        assertTrue(names().isEmpty());
    }

    @Test
    void duplicateNameWithinBatchRejected() {
        List<String> reasons = registry.replacePluginTypes("p1", List.of(type("alpha", "p1"), type("alpha", "p1")));
        assertEquals(1, reasons.size());
        assertTrue(reasons.get(0).contains("more than once"), reasons.toString());
        assertTrue(names().isEmpty());
    }

    @Test
    void nameOwnedByAnotherPluginRejected() {
        registry.replacePluginTypes("p1", List.of(type("alpha", "p1")));
        List<String> reasons = registry.replacePluginTypes("p2", List.of(type("beta", "p2"), type("alpha", "p2")));
        assertEquals(1, reasons.size());
        assertTrue(reasons.get(0).contains("already registered by plugin p1"), reasons.toString());
        assertEquals(Set.of("alpha"), names());
        assertEquals("p1", registry.find("alpha").orElseThrow().pluginName());
    }

    @Test
    void typeDeclaringAnotherPluginRejected() {
        List<String> reasons = registry.replacePluginTypes("p1", List.of(type("alpha", "p2")));
        assertEquals(1, reasons.size());
        assertTrue(names().isEmpty());
    }

    @Test
    void blankPluginNameRejected() {
        assertFalse(registry.replacePluginTypes(" ", List.of(type("alpha", " "))).isEmpty());
        assertTrue(names().isEmpty());
    }

    @Test
    void replaceSwapsOnlyThatPluginsTypes() {
        registry.replacePluginTypes("p1", List.of(type("alpha", "p1"), type("beta", "p1")));
        registry.replacePluginTypes("p2", List.of(type("gamma", "p2")));
        assertEquals(List.of(), registry.replacePluginTypes("p1", List.of(type("delta", "p1"))));
        assertEquals(Set.of("gamma", "delta"), names());
    }

    @Test
    void removeDropsOnlyThatPluginsTypes() {
        registry.replacePluginTypes("p1", List.of(type("alpha", "p1")));
        registry.replacePluginTypes("p2", List.of(type("gamma", "p2")));
        registry.removePluginTypes("p1");
        assertEquals(Set.of("gamma"), names());
    }

    @Test
    void snapshotIsImmutableAndUnaffectedByLaterWrites() {
        registry.replacePluginTypes("p1", List.of(type("alpha", "p1")));
        Map<String, PluginPackageType> before = registry.snapshot();
        registry.replacePluginTypes("p1", List.of(type("beta", "p1")));
        assertEquals(Set.of("alpha"), before.keySet());
        assertEquals(Set.of("beta"), registry.snapshot().keySet());
        try {
            before.clear();
        } catch (UnsupportedOperationException expected) {
            assertSame(UnsupportedOperationException.class, expected.getClass());
            return;
        }
        throw new AssertionError("snapshot must be immutable");
    }
}
