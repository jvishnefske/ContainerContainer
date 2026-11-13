/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.cert.CertClient;
import org.jfrog.access.client.notify.NotifyClient;
import org.jfrog.access.client.user.UsersClient;
import org.jfrog.access.common.ResourceType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The refusal proxy: dispatch, refusal, identity methods and construction-time checks. */
class FailClosedTest {

    /** A small interface with a default method, to test dispatch rules without the Access jars. */
    interface Greeter {
        String greet(String name);

        String shout(String name);

        default String greetTwice(String name) {
            return greet(name) + greet(name);
        }
    }

    /** Implements only {@code greet}. */
    static final class HalfGreeter {
        final List<String> seen = new ArrayList<>();

        public String greet(String name) {
            seen.add(name);
            if ("boom".equals(name)) {
                throw new IllegalStateException("boom");
            }
            return "hi " + name;
        }
    }

    /** Has a public method the interface does not declare. */
    static final class TypoGreeter {
        public String greet(String name) {
            return name;
        }

        public String gret(String name) {
            return name;
        }
    }

    /** Returns the wrong type. */
    static final class WrongReturnGreeter {
        public Integer greet(String name) {
            return 1;
        }
    }

    @Test
    void refusingProxyThrowsTheFixedMessage() {
        CertClient cert = FailClosed.of(CertClient.class, "cert");
        Method any = CertClient.class.getMethods()[0];
        EmbeddedAccessUnsupportedException e = assertThrows(EmbeddedAccessUnsupportedException.class,
                () -> invoke(cert, any));
        assertEquals("not supported by embedded access: cert." + any.getName(), e.getMessage());
        assertEquals("cert." + any.getName(), e.operation());
        assertInstanceOf(AccessClientException.class, e);
        assertTrue(FailClosed.refusesEverything(cert));
        assertEquals("cert", FailClosed.surfaceOf(cert).orElseThrow());
    }

    @Test
    void identityMethodsAreAnsweredByTheProxy() {
        CertClient a = FailClosed.of(CertClient.class, "cert");
        CertClient b = FailClosed.of(CertClient.class, "cert");
        assertEquals("EmbeddedAccess[cert]", a.toString());
        assertEquals(System.identityHashCode(a), a.hashCode());
        assertEquals(a, a);
        assertNotEquals(a, b);
        assertFalse(a.equals(null));
    }

    @Test
    void backedProxyDispatchesAndRefusesTheRest() {
        HalfGreeter delegate = new HalfGreeter();
        Greeter g = FailClosed.backedBy(Greeter.class, "greeter", delegate);
        assertEquals("hi bob", g.greet("bob"));
        assertEquals("hi xhi x", g.greetTwice("x"), "default methods run over the delegate");
        EmbeddedAccessUnsupportedException e = assertThrows(EmbeddedAccessUnsupportedException.class,
                () -> g.shout("bob"));
        assertEquals("not supported by embedded access: greeter.shout", e.getMessage());
        assertEquals(List.of("bob", "x", "x"), delegate.seen);
    }

    @Test
    void delegateExceptionsAreNotWrapped() {
        Greeter g = FailClosed.backedBy(Greeter.class, "greeter", new HalfGreeter());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> g.greet("boom"));
        assertEquals("boom", e.getMessage());
    }

    @Test
    void delegateSignatureMistakesFailAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> FailClosed.backedBy(Greeter.class, "greeter", new TypoGreeter()));
        assertThrows(IllegalArgumentException.class,
                () -> FailClosed.backedBy(Greeter.class, "greeter", new WrongReturnGreeter()));
        assertThrows(IllegalArgumentException.class,
                () -> FailClosed.of(HalfGreeter.class, "x"));
    }

    @Test
    void isImplementedReflectsTheDelegate() throws Exception {
        Greeter g = FailClosed.backedBy(Greeter.class, "greeter", new HalfGreeter());
        assertTrue(FailClosed.isImplemented(g, Greeter.class.getMethod("greet", String.class)));
        assertFalse(FailClosed.isImplemented(g, Greeter.class.getMethod("shout", String.class)));
        assertTrue(FailClosed.isImplemented(g, Object.class.getMethod("toString")));
        assertFalse(FailClosed.refusesEverything(g));
        assertThrows(IllegalArgumentException.class, () -> FailClosed.isImplemented("not a proxy",
                Object.class.getMethod("toString")));
    }

    @Test
    void accessClientBaseAuthCallsNeedNoDelegate() {
        UsersClient users = FailClosed.of(UsersClient.class, "users");
        assertSame(users, users.useAuth(null));
        assertSame(users, users.useAuth(null, () -> null));
        assertTrue(users.hasAuth());
        assertThrows(EmbeddedAccessUnsupportedException.class, users::findUsers);
    }

    @Test
    void loggedNoOpSurfaceSucceeds() {
        NotifyClient n = FailClosed.backedBy(NotifyClient.class, "notifications", new EaLoggedNoOps.Notifications());
        n.deleteResource(ResourceType.REPO, "libs");
    }

    @Test
    void everyEmbeddedSurfaceBuilds() {
        try (EaFixture fx = new EaFixture()) {
            EmbeddedAccessClient c = fx.client;
            List<Object> surfaces = List.of(c.token(), c.auth(), c.grpcAuth(null), c.users(), c.groups(),
                    c.permissions(), c.grpcPermissions(), c.cachedGrpcPlatformConfigs(), c.config(), c.system(),
                    c.cachedLicense(), c.cachedGrpcProject(), c.registry(), c.notifications(), c.router(),
                    c.cachedAuthSettingClient(null));
            for (Object s : surfaces) {
                assertFalse(FailClosed.refusesEverything(s), () -> s + " has no delegate");
            }
            for (Object s : List.of(c.cert(), c.grpcSystem(), c.grpcUsers(), c.grpcGroups(), c.grpcPermissionsV2(),
                    c.imports(), c.topology(), c.proxies(), c.vaults(), c.cachedGrpcProxy(null, "k"))) {
                assertTrue(FailClosed.refusesEverything(s), () -> s + " should refuse everything");
            }
            assertThrows(EmbeddedAccessUnsupportedException.class, () -> c.restCall(null));
            assertThrows(EmbeddedAccessUnsupportedException.class, () -> c.lock("a", "b", null));
            assertSame(c, c.useAuth(null));
            c.ping();
            c.readiness();
            c.liveness();
        }
    }

    private static Object invoke(Object target, Method m) throws Throwable {
        Object[] args = new Object[m.getParameterCount()];
        Class<?>[] types = m.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            args[i] = defaultValue(types[i]);
        }
        try {
            return m.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        return (char) 0;
    }
}
