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

package org.artifactory.addon.plugin.wasm;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A validated WASM plugin manifest ({@code <name>.wasm.yaml}), as {@link ManifestParser} builds it.
 *
 * @param realms     realms the module implements, in declaration order
 * @param executions executions the module implements, in declaration order
 * @param httpHosts  hosts {@code http_get} effects may reach ({@code capabilities.httpGet.hosts})
 * @param limits     resource limits
 * @param config     the opaque {@code config} value, handed to every step; {@code null} when absent
 */
public record WasmManifest(List<RealmDecl> realms, List<ExecutionDecl> executions, Set<AllowedHost> httpHosts,
        PluginLimits limits, JsonText config) {

    /** The only ABI version this host speaks. */
    public static final int ABI_VERSION = 1;

    public WasmManifest {
        realms = List.copyOf(realms);
        executions = List.copyOf(executions);
        httpHosts = Set.copyOf(httpHosts);
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(config, "config");
    }

    /** Which realm hooks a module implements. */
    public enum RealmHookKind {
        /** {@code realm.authenticate}: user name and password. */
        AUTHENTICATE("authenticate"),
        /** {@code realm.bearer}: the token of an {@code Authorization: Bearer} header. */
        BEARER("bearer");

        private final String key;

        RealmHookKind(String key) {
            this.key = key;
        }

        /** @return the manifest spelling */
        public String key() {
            return key;
        }
    }

    /**
     * A realm entry. Realms of WASM plugins are always {@code exclusive}: ABI v1 has no way to call
     * {@code realm.authenticate} without a password, which additive realms need.
     *
     * @param name            realm name, as for Groovy realms
     * @param autoCreateUsers whether results may create just-in-time users
     * @param hooks           the hooks the module implements, at least one
     */
    public record RealmDecl(String name, boolean autoCreateUsers, Set<RealmHookKind> hooks) {
        public RealmDecl {
            Objects.requireNonNull(name, "name");
            hooks = Set.copyOf(hooks);
        }
    }

    /**
     * An execution entry; authorized by the same rules as a Groovy execution's options.
     *
     * @param name       execution name
     * @param users      user names allowed besides administrators; {@code *} for every authenticated caller
     * @param groups     group names whose members may run it
     * @param httpMethod the only HTTP method it answers to
     */
    public record ExecutionDecl(String name, Set<String> users, Set<String> groups, String httpMethod) {
        public ExecutionDecl {
            Objects.requireNonNull(name, "name");
            users = Set.copyOf(users);
            groups = Set.copyOf(groups);
            Objects.requireNonNull(httpMethod, "httpMethod");
        }
    }

    /**
     * A host {@code http_get} may reach.
     *
     * @param host lower-case host name or IPv4 address
     * @param port the only port allowed; 443 when the manifest entry names no port
     */
    public record AllowedHost(String host, int port) {
        public AllowedHost {
            Objects.requireNonNull(host, "host");
        }
    }
}
