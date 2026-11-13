/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.auth.AuthClient;
import org.jfrog.access.client.auth.GrpcAuthClient;
import org.jfrog.access.client.auth.MTlsClient;
import org.jfrog.access.client.auth.ldap.AuthSettingsClient;
import org.jfrog.access.client.config.ConfigClient;
import org.jfrog.access.client.group.GroupsClient;
import org.jfrog.access.client.imports.ImportClient;
import org.jfrog.access.client.notify.NotifyClient;
import org.jfrog.access.client.permission.GrpcPermissionsClient;
import org.jfrog.access.client.permission.PermissionsClient;
import org.jfrog.access.client.platformconfig.GrpcPlatformConfigClient;
import org.jfrog.access.client.platformconfig.LicenseClient;
import org.jfrog.access.client.project.GrpcProjectClient;
import org.jfrog.access.client.registry.RegistryClient;
import org.jfrog.access.client.system.SystemClient;
import org.jfrog.access.client.token.TokenClient;
import org.jfrog.access.client.user.UsersClient;
import org.jfrog.access.router.RouterGrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks the committed operations inventory against {@link EmbeddedAccessClient}: every critical or feature row is
 * implemented (it reaches an adapter, not {@link FailClosed}'s refusal), every unsupported row really is refused,
 * and every row names a real interface method.
 */
class AccessLiteOperationsCoverageTest {

    private static final String RESOURCE = "/embedded-access/access-lite-operations.tsv";
    private static final Set<String> CLASSES = Set.of("critical", "feature", "optional", "unsupported");

    private record Row(int line, String surface, String method, String cls, String note) {
    }

    private record Surface(Class<?> iface, Function<EmbeddedAccessClient, Object> get) {
    }

    private static final Map<String, Surface> SURFACES = Map.ofEntries(
            Map.entry("token", new Surface(TokenClient.class, AccessClient::token)),
            Map.entry("auth", new Surface(AuthClient.class, AccessClient::auth)),
            Map.entry("cachedAuth", new Surface(AuthClient.class, AccessClient::cachedAuth)),
            Map.entry("mtls", new Surface(MTlsClient.class, c -> c.cachedAuth().mtls())),
            Map.entry("grpcAuth", new Surface(GrpcAuthClient.class, c -> c.grpcAuth(null))),
            Map.entry("users", new Surface(UsersClient.class, AccessClient::users)),
            Map.entry("cachedUsers", new Surface(UsersClient.class, AccessClient::cachedUsers)),
            Map.entry("groups", new Surface(GroupsClient.class, AccessClient::groups)),
            Map.entry("permissions", new Surface(PermissionsClient.class, AccessClient::permissions)),
            Map.entry("grpcPermissions", new Surface(GrpcPermissionsClient.class, AccessClient::grpcPermissions)),
            Map.entry("platformConfigs", new Surface(GrpcPlatformConfigClient.class,
                    AccessClient::cachedGrpcPlatformConfigs)),
            Map.entry("config", new Surface(ConfigClient.class, AccessClient::config)),
            Map.entry("authSettings", new Surface(AuthSettingsClient.class, c -> c.cachedAuthSettingClient(null))),
            Map.entry("system", new Surface(SystemClient.class, AccessClient::system)),
            Map.entry("license", new Surface(LicenseClient.class, AccessClient::cachedLicense)),
            Map.entry("project", new Surface(GrpcProjectClient.class, AccessClient::cachedGrpcProject)),
            Map.entry("registry", new Surface(RegistryClient.class, AccessClient::registry)),
            Map.entry("notifications", new Surface(NotifyClient.class, AccessClient::notifications)),
            Map.entry("router", new Surface(RouterGrpcClient.class, AccessClient::router)),
            Map.entry("imports", new Surface(ImportClient.class, AccessClient::imports)));

    private EaFixture fx;

    @BeforeEach
    void setUp() {
        fx = new EaFixture();
    }

    @AfterEach
    void tearDown() {
        fx.close();
    }

    @Test
    void inventoryIsWellFormedAndFreeOfVendorPayloads() throws IOException {
        List<Row> rows = rows();
        assertTrue(rows.size() > 100, "inventory looks truncated: " + rows.size());
        Set<String> seen = new HashSet<>();
        for (Row r : rows) {
            assertTrue(CLASSES.contains(r.cls()), () -> "line " + r.line() + ": bad class " + r.cls());
            assertFalse(r.note().isBlank(), () -> "line " + r.line() + ": empty note");
            assertTrue(seen.add(r.surface() + " " + r.method()), () -> "line " + r.line() + ": duplicate");
            assertTrue(r.surface().equals("client") || SURFACES.containsKey(r.surface()),
                    () -> "line " + r.line() + ": unknown surface " + r.surface());
        }
        String text = new String(Objects.requireNonNull(getClass().getResourceAsStream(RESOURCE)).readAllBytes(),
                StandardCharsets.UTF_8);
        assertFalse(text.contains("{\""), "no JSON payloads");
    }

    @Test
    void criticalAndFeatureRowsAreImplementedAndUnsupportedRowsFailClosed() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Row r : rows()) {
            boolean mustWork = r.cls().equals("critical") || r.cls().equals("feature");
            boolean mustFail = r.cls().equals("unsupported");
            boolean works = r.surface().equals("client") ? clientMethodWorks(r) : surfaceMethodImplemented(r);
            if (mustWork && !works) {
                problems.add("line " + r.line() + ": " + r.surface() + "." + r.method() + " is " + r.cls()
                        + " but not implemented");
            }
            if (mustFail && works) {
                problems.add("line " + r.line() + ": " + r.surface() + "." + r.method()
                        + " is unsupported but implemented: reclassify it");
            }
        }
        if (!problems.isEmpty()) {
            fail(String.join("\n", problems));
        }
    }

    private boolean surfaceMethodImplemented(Row r) {
        Surface s = SURFACES.get(r.surface());
        Object proxy = s.get().apply(fx.client);
        assertNotNull(proxy, r.surface());
        return FailClosed.isImplemented(proxy, resolve(s.iface(), r));
    }

    /** Client-level methods are implemented directly; invoke them with default arguments. */
    private boolean clientMethodWorks(Row r) {
        Method m = resolve(AccessClient.class, r);
        Object[] args = Arrays.stream(m.getParameterTypes()).map(FailClosedTest::defaultValue).toArray();
        try {
            Object result = m.invoke(fx.client, args);
            return !(result != null && FailClosed.surfaceOf(result).isPresent() && r.cls().equals("unsupported")
                    && FailClosed.refusesEverything(result));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof EmbeddedAccessUnsupportedException) {
                return false;
            }
            throw new AssertionError("line " + r.line() + ": " + r.method() + " threw " + e.getCause(), e);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Method resolve(Class<?> iface, Row r) {
        String name = r.method().substring(0, r.method().indexOf('('));
        String params = r.method().substring(r.method().indexOf('(') + 1, r.method().length() - 1);
        List<String> wanted = params.isEmpty() ? List.of() : List.of(params.split(","));
        List<Method> matches = Arrays.stream(iface.getMethods())
                .filter(m -> m.getName().equals(name))
                .filter(m -> Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName).toList().equals(wanted))
                .toList();
        if (matches.isEmpty() && iface == AccessClient.class && name.equals("hashCode")) {
            try {
                return Object.class.getMethod("hashCode");
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }
        assertEquals(1, matches.stream().map(m -> m.getName() + Arrays.toString(m.getParameterTypes()))
                .collect(Collectors.toSet()).size(), () -> "line " + r.line() + ": " + iface.getSimpleName() + "."
                + r.method() + " matches " + matches);
        return matches.get(0);
    }

    private List<Row> rows() throws IOException {
        List<Row> out = new ArrayList<>();
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream(RESOURCE), RESOURCE);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int n = 0;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                n++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] cols = line.split("\t", -1);
                assertEquals(4, cols.length, "line " + n + " must have 4 columns");
                if (header) {
                    assertEquals(List.of("surface", "method", "class", "note"), List.of(cols));
                    header = false;
                    continue;
                }
                out.add(new Row(n, cols[0], cols[1], cols[2], cols[3]));
            }
        }
        return out;
    }
}
