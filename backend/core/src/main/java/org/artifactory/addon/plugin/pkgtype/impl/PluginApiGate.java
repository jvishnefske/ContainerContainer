/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype.impl;

import org.artifactory.addon.plugin.pkgtype.PluginApiHandler;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The pure decisions in front of a plugin package type's HTTP API: which permission a method needs, whether a
 * request may reach the handler, and how the registry ping {@code GET /v2/} answers.
 *
 * <p>Nothing here performs I/O; the facts (the resolved repository, whether a permission is granted, whether the
 * caller is anonymous) are inputs. {@link PluginApiService} gathers them and the REST resource sends the result.
 * Every path that is not an explicit {@link Decision.Dispatch} refuses: the gate fails closed.
 */
public final class PluginApiGate {

    /** Realm of the Basic challenge, the one {@code ArtifactoryBasicAuthenticationEntryPoint} sends. */
    public static final String REALM = "Artifactory Realm";

    /** {@code WWW-Authenticate} value of a 401 from the plugin HTTP API. */
    public static final String BASIC_CHALLENGE = "Basic realm=\"" + REALM + "\"";

    private PluginApiGate() {
    }

    /** A repository permission the API checks on the repository root. */
    public enum Permission {
        READ, DEPLOY, DELETE
    }

    /**
     * The permission {@code method} needs: GET and HEAD read, PUT, POST and PATCH deploy, DELETE delete.
     *
     * @return empty for any other method, which the API does not serve
     */
    public static Optional<Permission> requiredPermission(String method) {
        if (method == null) {
            return Optional.empty();
        }
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "GET", "HEAD" -> Optional.of(Permission.READ);
            case "PUT", "POST", "PATCH" -> Optional.of(Permission.DEPLOY);
            case "DELETE" -> Optional.of(Permission.DELETE);
            default -> Optional.empty();
        };
    }

    /** What the request names, as resolved by the shell. */
    public sealed interface Target {

        /** No repository has the key, or it is not a local, non-cache repository. */
        record NoSuchRepo(String repoKey) implements Target {
            public NoSuchRepo {
                Objects.requireNonNull(repoKey, "repoKey");
            }
        }

        /** A local repository without a registered plugin package type that has an API handler. */
        record NoApi(String repoKey) implements Target {
            public NoApi {
                Objects.requireNonNull(repoKey, "repoKey");
            }
        }

        /** A local repository whose registered type has an API handler. */
        record Api(String repoKey, PluginPackageType type, PluginApiHandler handler, boolean blackedOut)
                implements Target {
            public Api {
                Objects.requireNonNull(repoKey, "repoKey");
                Objects.requireNonNull(type, "type");
                Objects.requireNonNull(handler, "handler");
            }
        }

        /**
         * Classifies a repository. Pure.
         *
         * @param localRepo whether {@code repoKey} names a local, non-cache repository
         * @param type      its registered plugin package type, if any
         */
        static Target of(String repoKey, boolean localRepo, Optional<PluginPackageType> type, boolean blackedOut) {
            if (!localRepo) {
                return new NoSuchRepo(repoKey);
            }
            Optional<PluginApiHandler> handler = type.flatMap(PluginPackageType::api);
            if (handler.isEmpty()) {
                return new NoApi(repoKey);
            }
            return new Api(repoKey, type.get(), handler.get(), blackedOut);
        }
    }

    /** The outcome of the gate. */
    public sealed interface Decision {

        /** Call {@code handler} of {@code type}. */
        record Dispatch(PluginPackageType type, PluginApiHandler handler) implements Decision {
            public Dispatch {
                Objects.requireNonNull(type, "type");
                Objects.requireNonNull(handler, "handler");
            }
        }

        /**
         * Do not call the handler; answer {@code status} with {@code message}, adding the Basic challenge when
         * {@code challenge} is set (401 only).
         */
        record Refuse(int status, String message, boolean challenge) implements Decision {
            public Refuse {
                Objects.requireNonNull(message, "message");
                if (challenge && status != 401) {
                    throw new IllegalArgumentException("only a 401 carries a challenge, got " + status);
                }
            }
        }
    }

    /**
     * Decides whether a request reaches the handler. Pure as long as {@code granted} is. Checked in this order, the
     * first failing check refusing: the method is one the API serves (405); the repository is a local repository
     * whose type has an API handler (404); the caller has the method's permission on the repository (401 with the
     * Basic challenge for an anonymous caller, 403 otherwise); the repository is not blacked out (404, as for a
     * normal download or upload of a blacked-out repository).
     *
     * @param granted   whether the caller holds a permission on the repository root; consulted at most once
     * @param anonymous whether the caller is the anonymous user
     */
    public static Decision decide(String method, Target target, Predicate<Permission> granted, boolean anonymous) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(granted, "granted");
        Optional<Permission> required = requiredPermission(method);
        if (required.isEmpty()) {
            return new Decision.Refuse(405, "Method " + method + " is not supported by the plugin HTTP API", false);
        }
        return switch (target) {
            case Target.NoSuchRepo missing -> new Decision.Refuse(404,
                    "No local repository '" + missing.repoKey() + "'", false);
            case Target.NoApi noApi -> new Decision.Refuse(404, "Repository '" + noApi.repoKey() +
                    "' has no plugin package type with an HTTP API", false);
            case Target.Api api -> afterPermission(api, required.get(), granted.test(required.get()), anonymous);
        };
    }

    private static Decision afterPermission(Target.Api api, Permission required, boolean granted, boolean anonymous) {
        if (!granted) {
            String action = required.name().toLowerCase(Locale.ROOT);
            return anonymous
                    ? new Decision.Refuse(401, "Authentication is required to " + action + " repository '" +
                    api.repoKey() + "'", true)
                    : new Decision.Refuse(403, "Missing " + action + " permission on repository '" +
                    api.repoKey() + "'", false);
        }
        if (api.blackedOut()) {
            return new Decision.Refuse(404, "The repository '" + api.repoKey() + "' is blacked out", false);
        }
        return new Decision.Dispatch(api.type(), api.handler());
    }

    /** The answer to the registry ping {@code GET /v2/}. */
    public enum Ping {
        /** 200 {@code {}} with {@code Docker-Distribution-API-Version: registry/2.0}. */
        OK,
        /** 401 with the Basic challenge, so clients send credentials. */
        CHALLENGE,
        /** 404: no plugin package type serves an HTTP API, so there is no registry to ping. */
        NOT_FOUND
    }

    /**
     * Decides the ping answer. Pure.
     *
     * @param apiTypeRegistered  whether at least one registered plugin package type has an API handler
     * @param anonymous          whether the caller is the anonymous user
     * @param anonAccessEnabled  whether anonymous access is enabled
     */
    public static Ping ping(boolean apiTypeRegistered, boolean anonymous, boolean anonAccessEnabled) {
        if (!apiTypeRegistered) {
            return Ping.NOT_FOUND;
        }
        if (anonymous && !anonAccessEnabled) {
            return Ping.CHALLENGE;
        }
        return Ping.OK;
    }
}
