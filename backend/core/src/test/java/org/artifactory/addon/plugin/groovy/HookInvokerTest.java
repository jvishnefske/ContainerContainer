/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import org.artifactory.exception.CancelException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure helpers of {@link HookInvoker} and {@link GroovyPluginCompiler} (G7 classification). */
class HookInvokerTest {

    @Test
    void fitArgsTruncatesOnlyWhenTheClosureTakesFewer() {
        Object[] args = {"a", "b", "c"};
        assertSame(args, HookInvoker.fitArgs(args, 3));
        assertSame(args, HookInvoker.fitArgs(args, 5));
        assertArrayEquals(new Object[]{"a"}, HookInvoker.fitArgs(args, 1));
        assertArrayEquals(new Object[0], HookInvoker.fitArgs(args, 0));
    }

    @Test
    void findCancelWalksTheCauseChain() {
        CancelException cancel = new CancelException("stop", 403);
        RuntimeException wrapped = new RuntimeException(new IllegalStateException(cancel));
        assertSame(cancel, HookInvoker.findCancel(wrapped).orElseThrow());
        assertTrue(HookInvoker.findCancel(new RuntimeException("plain")).isEmpty());
    }

    @Test
    void scriptClassNamesAreValidIdentifiers() {
        assertEquals("UserPlugin_my_plugin", GroovyPluginCompiler.scriptClassName("my-plugin"));
        assertEquals("UserPlugin_1st", GroovyPluginCompiler.scriptClassName("1st"));
        assertEquals("UserPlugin_short", GroovyPluginCompiler.scriptClassName("short"));
        assertEquals("UserPlugin__", GroovyPluginCompiler.scriptClassName("é"));
    }

    @Test
    void pluginSourceNameStripsTheExtension() {
        assertEquals("a.b", PluginSource.nameOf("a.b.groovy"));
        assertEquals("plain", PluginSource.nameOf("plain"));
    }
}
