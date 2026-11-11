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

import groovy.lang.Closure;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.api.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** G4: running named executions through {@code PluginsAddon.execute}, admin-only unless users/groups allow. */
class PluginExecutionTest {

    @TempDir
    Path dir;

    private PluginTestRig rig;

    @BeforeEach
    void setUp() throws Exception {
        rig = new PluginTestRig(dir);
        rig.write("ops.groovy", """
                executions {
                    adminOnly { params -> message = 'admin ran it'; status = 200 }
                    noStatus { params -> message = 'implicit ok' }
                    forBob(users: ['bob']) { params -> message = 'hi ' + security.currentUsername }
                    forDeployers(groups: 'deployers') { params -> message = 'deployed' }
                    echoBody(users: '*nobody*') { params, body -> message = body?.toString() }
                    broken { params -> throw new IllegalArgumentException('bad input') }
                    refuses { params -> throw new CancelException('not today', 409) }
                    background { params -> calls << 'ran ' + params['x'] }
                }
                """);
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.currentUsername()).thenAnswer(invocation -> rig.caller.username());
        rig.extraBindings.put("security", new PluginSecurity(securityService));
        rig.reload();
    }

    private ResponseCtx run(String name) {
        return rig.addon.execute(name, "POST", Map.of(), null, false);
    }

    @Test
    void adminRunsADefaultExecutionAndGetsItsStatusAndMessage() {
        ResponseCtx ctx = run("adminOnly");
        assertEquals(200, ctx.getStatus());
        assertEquals("admin ran it", ctx.getMessage());
    }

    @Test
    void unsetStatusDefaultsTo200() {
        assertEquals(200, run("noStatus").getStatus());
    }

    @Test
    void nonAdminIsRefusedByDefault() {
        rig.caller = new Caller("alice", false, Set.of("readers"));
        ResponseCtx ctx = run("adminOnly");
        assertEquals(403, ctx.getStatus());
        assertTrue(ctx.getMessage().contains("alice"), ctx.getMessage());
    }

    @Test
    void declaredUserMayRun() {
        rig.caller = new Caller("bob", false, Set.of());
        ResponseCtx ctx = run("forBob");
        assertEquals(200, ctx.getStatus());
        assertEquals("hi bob", ctx.getMessage());
        assertEquals(403, run("adminOnly").getStatus());
    }

    @Test
    void declaredGroupMemberMayRun() {
        rig.caller = new Caller("carol", false, Set.of("deployers"));
        assertEquals(200, run("forDeployers").getStatus());
        rig.caller = new Caller("dave", false, Set.of("readers"));
        assertEquals(403, run("forDeployers").getStatus());
    }

    @Test
    void unknownExecutionIs404() {
        assertEquals(404, run("nope").getStatus());
    }

    @Test
    void failingExecutionIs500WithTheReasonAndIsReported() {
        ResponseCtx ctx = run("broken");
        assertEquals(500, ctx.getStatus());
        assertTrue(ctx.getMessage().contains("bad input"), ctx.getMessage());
        assertEquals("executions.broken", rig.reports.get(0).what());
    }

    @Test
    void cancelledExecutionReturnsTheCancelStatus() {
        ResponseCtx ctx = run("refuses");
        assertEquals(409, ctx.getStatus());
        assertEquals("not today", ctx.getMessage());
    }

    @Test
    void bodyIsPassedAsSecondArgument() {
        ResponseCtx ctx = rig.addon.execute("echoBody", "POST", Map.of(), null, false);
        assertNull(ctx.getMessage());
    }

    @Test
    void asyncExecutionIsAcceptedAndRunsOnTheExecutor() {
        ResponseCtx ctx = rig.addon.execute("background", "POST", Map.of("x", List.of("1")), null, true);
        assertEquals(202, ctx.getStatus());
        assertTrue(rig.calls.isEmpty());
        rig.asyncTasks.forEach(Runnable::run);
        assertEquals(List.of("ran [1]"), rig.calls);
    }

    @Test
    void asyncExecutionIsAuthorizedBeforeBeingQueued() {
        rig.caller = new Caller("alice", false, Set.of());
        assertEquals(403, rig.addon.execute("background", "POST", Map.of(), null, true).getStatus());
        assertTrue(rig.asyncTasks.isEmpty());
    }

    @Test
    void wrongHttpMethodIsRefusedWith405AndDoesNotRun() {
        ResponseCtx ctx = rig.addon.execute("background", "GET", Map.of(), null, false);
        assertEquals(405, ctx.getStatus());
        assertTrue(ctx.getMessage().contains("accepts only POST"));
        assertTrue(rig.calls.isEmpty());
    }

    @Test
    void wrongHttpMethodIsRefusedBeforeAsyncQueueing() {
        assertEquals(405, rig.addon.execute("background", "get", Map.of(), null, true).getStatus());
        assertTrue(rig.asyncTasks.isEmpty());
    }

    @Test
    void httpMethodMatchIgnoresCase() {
        rig.addon.execute("background", "post", Map.of("x", List.of("1")), null, false);
        assertEquals(List.of("ran [1]"), rig.calls);
    }

    @Test
    void authorizerRules() {
        PluginExecution open = PluginExecution.fromOptions("p", "e", Map.of("users", List.of("u"), "groups", "g"),
                new Closure<Object>(null) {
                });
        assertTrue(ExecutionAuthorizer.isPermitted(open, new Caller("admin", true, Set.of())));
        assertTrue(ExecutionAuthorizer.isPermitted(open, new Caller("u", false, Set.of())));
        assertTrue(ExecutionAuthorizer.isPermitted(open, new Caller("x", false, Set.of("g"))));
        assertFalse(ExecutionAuthorizer.isPermitted(open, new Caller("x", false, Set.of("h"))));
    }
}
