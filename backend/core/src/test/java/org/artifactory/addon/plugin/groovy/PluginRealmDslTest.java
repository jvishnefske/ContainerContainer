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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P1: the {@code realms} DSL, its explicit results and fail-closed invocation. */
class PluginRealmDslTest {

    @TempDir
    Path dir;

    private PluginTestRig rig;

    @BeforeEach
    void setUp() {
        rig = new PluginTestRig(dir);
    }

    private PluginLoadResult load(String file, String text) throws Exception {
        rig.write(file, text);
        PluginSnapshot snapshot = rig.reload();
        return snapshot.results().stream().filter(r -> r.fileName().equals(file)).findFirst().orElseThrow();
    }

    private PluginRealm onlyRealm() {
        List<PluginRealm> realms = rig.engine.snapshot().realms();
        assertEquals(1, realms.size(), String.valueOf(realms));
        return realms.get(0);
    }

    private RealmResult call(PluginRealm realm, Object... args) {
        HookFailureReporter reporter = (p, w, e) -> rig.reports.add(new PluginTestRig.Report(p, w, e));
        return RealmInvoker.result(realm, args.length == 1 ? realm.bearer().orElseThrow()
                : realm.authenticate().orElseThrow(), "test", reporter, args);
    }

    @Test
    void realmsBlockDeclaresARealmWithOptionsAndClosures() throws Exception {
        PluginLoadResult result = load("corp.groovy", """
                realms {
                    corp(autoCreateUsers: true, realmPolicy: 'exclusive') {
                        authenticate { username, credentials -> realm.notMine() }
                        bearer { token -> realm.authenticated('alice', groups: ['readers'], jit: true,
                                expiresAt: 2000000000L, email: 'a@example.com') }
                        userExists { username -> username == 'alice' }
                    }
                }
                """);
        assertInstanceOf(PluginLoadResult.Loaded.class, result, String.valueOf(result));
        PluginRealm realm = onlyRealm();
        assertEquals("corp", realm.name());
        assertEquals("corp", realm.pluginName());
        assertEquals(PluginRealm.Policy.EXCLUSIVE, realm.policy());
        assertTrue(realm.autoCreateUsers());
        assertEquals("plugin:corp", realm.userRealm());
        assertEquals(new RealmResult.NotMine(), call(realm, "u", "p"));
        assertEquals(new RealmResult.Authenticated("alice", Set.of("readers"), true, OptionalLong.of(2000000000L),
                Optional.of("a@example.com")), call(realm, "token"));
        assertEquals("corp", PluginReports.describe(rig.engine.snapshot()).get("corp").get("realms"));
    }

    @Test
    void defaultsAreExclusiveAndNoJustInTimeUsers() throws Exception {
        load("p.groovy", "realms { r { bearer { t -> realm.rejected('no') } } }");
        PluginRealm realm = onlyRealm();
        assertEquals(PluginRealm.Policy.EXCLUSIVE, realm.policy());
        assertFalse(realm.autoCreateUsers());
        assertEquals(new RealmResult.Rejected("no"), call(realm, "t"));
    }

    @Test
    void invalidDeclarationsFailTheLoadAndRegisterNoRealm() throws Exception {
        String[] bad = {
            "realms { r(realmPolicy: 'sometimes') { bearer { t -> null } } }",
            "realms { r(autoCreate: true) { bearer { t -> null } } }",
            "realms { r(autoCreateUsers: 'yes') { bearer { t -> null } } }",
            "realms { r { userExists { u -> true } } }",
            "realms { r(realmPolicy: 'additive') { bearer { t -> null } } }",
            "realms { r { bearer { t -> null }; bearer { t -> null } } }",
            "realms { 'bad name' { bearer { t -> null } } }",
            "realms { r { bearer { t -> null } }; r { bearer { t -> null } } }",
        };
        for (String text : bad) {
            PluginLoadResult result = load("bad.groovy", text);
            assertInstanceOf(PluginLoadResult.Failed.class, result, text);
            assertTrue(rig.engine.snapshot().realms().isEmpty(), text);
        }
    }

    @Test
    void aRealmNameDeclaredByAnEarlierPluginFailsTheLaterOne() throws Exception {
        rig.write("a.groovy", "realms { shared { bearer { t -> realm.notMine() } } }");
        rig.write("b.groovy", "realms { shared { bearer { t -> realm.notMine() } } }");
        PluginSnapshot snapshot = rig.reload();
        assertInstanceOf(PluginLoadResult.Loaded.class, snapshot.results().get(0));
        PluginLoadResult.Failed failed = assertInstanceOf(PluginLoadResult.Failed.class, snapshot.results().get(1));
        assertTrue(failed.error().contains("already declared by plugin 'a'"), failed.error());
        assertEquals("a", onlyRealm().pluginName());
    }

    @Test
    void realmsAreOrderedByPluginFileThenDeclaration() throws Exception {
        rig.write("b.groovy", "realms { b1 { bearer { t -> null } }; b2 { bearer { t -> null } } }");
        rig.write("a.groovy", "realms { a1 { bearer { t -> null } } }");
        assertEquals(List.of("a1", "b1", "b2"),
                rig.reload().realms().stream().map(PluginRealm::name).toList());
    }

    @Test
    void anythingButARealmResultIsNotMineAndIsReported() throws Exception {
        load("p.groovy", """
                realms {
                    r {
                        bearer { token ->
                            switch (token) {
                                case 'null': return null
                                case 'true': return true
                                case 'map': return [username: 'admin']
                                case 'throw': throw new IllegalStateException('boom')
                                case 'cancel': throw new CancelException('stop', 403)
                                case 'badOption': return realm.authenticated('alice', admin: true)
                                case 'badGroups': return realm.authenticated('alice', groups: 'admins')
                                default: return realm.authenticated('alice')
                            }
                        }
                    }
                }
                """);
        PluginRealm realm = onlyRealm();
        for (String token : List.of("null", "true", "map", "throw", "cancel", "badOption", "badGroups")) {
            rig.reports.clear();
            assertEquals(new RealmResult.NotMine(), call(realm, token), token);
            assertEquals(1, rig.reports.size(), token);
            assertEquals("p", rig.reports.get(0).pluginName());
        }
        assertInstanceOf(RealmResult.Authenticated.class, call(realm, "ok"));
    }

    @Test
    void realmClosuresRunInTheRealmScope() throws Exception {
        load("p.groovy", """
                realms { r { bearer { t -> calls << org.artifactory.addon.plugin.groovy.PluginCallScope.current()
                        .orElse(null); realm.notMine() } } }
                """);
        call(onlyRealm(), "t");
        assertEquals(List.of(PluginCallScope.Kind.REALM), rig.calls);
        assertTrue(PluginCallScope.current().isEmpty());
    }

    @Test
    void scriptsSeeTheRealmAndPluginDirBindings() throws Exception {
        load("p.groovy", "calls << realm; calls << pluginDir");
        assertSame(RealmResults.INSTANCE, rig.calls.get(0));
        assertEquals(dir, rig.calls.get(1));
    }

    @Test
    void userExistsIsTrueOnlyForAnExplicitTrue() throws Exception {
        load("p.groovy", """
                realms { r { bearer { t -> null }; userExists { u -> u == 'yes' ? true : (u == 'boom' ?
                        { throw new RuntimeException('x') }() : 'truthy') } } }
                """);
        PluginRealm realm = onlyRealm();
        HookFailureReporter reporter = (p, w, e) -> rig.reports.add(new PluginTestRig.Report(p, w, e));
        assertTrue(RealmInvoker.userExists(realm, realm.userExists().orElseThrow(), reporter, "yes"));
        assertFalse(RealmInvoker.userExists(realm, realm.userExists().orElseThrow(), reporter, "other"));
        assertFalse(RealmInvoker.userExists(realm, realm.userExists().orElseThrow(), reporter, "boom"));
        assertEquals(1, rig.reports.size());
    }
}
