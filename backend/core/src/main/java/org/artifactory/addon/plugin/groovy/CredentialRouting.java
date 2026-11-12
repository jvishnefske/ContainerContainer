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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Decides which credentials Artifactory offers to plugin realms before its own token handling (P2). Pure.
 *
 * <p>Artifactory treats every value that parses as a signed JWT as one of its own Access tokens: Bearer tokens and
 * Basic passwords that look like JWTs go straight to Access verification, never to the provider chain. Plugin
 * realms therefore see a JWT only when this class classifies it as {@link Kind#FOREIGN_JWT}: three base64url
 * segments whose header and payload decode to JSON objects and whose {@code iss} claim is a string that is not a
 * JFrog service id. Access issues tokens with an {@code iss} of the form {@code <type>@<instance>}, optionally
 * followed by {@code /users/<name>} (for example {@code jfac@01h...} or {@code jfrt@01h.../users/admin}); such
 * tokens, JWTs without a string {@code iss}, and everything that is not a JWT (reference tokens, API keys,
 * passwords) are {@link Kind#ACCESS_OR_OTHER} and keep their stock path, so Access tokens are never shown to plugin
 * code. A forged token claiming a JFrog issuer goes to Access, which rejects it.
 */
public final class CredentialRouting {

    /** Values longer than this are not inspected and keep the stock path. */
    public static final int MAX_INSPECTED_LENGTH = 64 * 1024;

    private static final Pattern JFROG_ISSUER = Pattern.compile("[A-Za-z0-9_-]+@[A-Za-z0-9_-]+(/.*)?",
            Pattern.DOTALL);
    private static final ObjectMapper JSON = new ObjectMapper();

    private CredentialRouting() {
    }

    /** How a credential value is routed. */
    public enum Kind {
        /** A JWT whose issuer is not a JFrog service id; plugin realms are asked first. */
        FOREIGN_JWT,
        /** Anything else, including Access tokens; the stock path handles it. */
        ACCESS_OR_OTHER
    }

    /** Where an incoming credential goes. */
    public enum Route {
        /** Offer the raw token to the {@code bearer} closures of exclusive realms. */
        REALM_BEARER,
        /** Offer user name and password to the {@code authenticate} closures of exclusive realms. */
        REALM_BASIC,
        /** Leave it to the stock path. */
        STOCK
    }

    /**
     * The routing rule.
     *
     * @param realmsLoaded whether any exclusive realm is loaded; with none, everything is {@link Route#STOCK} (P6)
     * @param bearer       the credential came from {@code Authorization: Bearer} (or the equivalent cookie)
     * @param basic        the credential is the password of a user name and password authentication
     * @param kind         the classification of the credential value
     */
    public static Route route(boolean realmsLoaded, boolean bearer, boolean basic, Kind kind) {
        if (!realmsLoaded || kind != Kind.FOREIGN_JWT) {
            return Route.STOCK;
        }
        if (bearer) {
            return Route.REALM_BEARER;
        }
        return basic ? Route.REALM_BASIC : Route.STOCK;
    }

    /** Classifies {@code value}; never throws. */
    public static Kind classify(String value) {
        if (value == null || value.length() > MAX_INSPECTED_LENGTH) {
            return Kind.ACCESS_OR_OTHER;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return Kind.ACCESS_OR_OTHER;
        }
        JsonNode header = decodeObject(parts[0]);
        JsonNode payload = decodeObject(parts[1]);
        if (header == null || payload == null) {
            return Kind.ACCESS_OR_OTHER;
        }
        JsonNode iss = payload.get("iss");
        if (iss == null || !iss.isTextual() || iss.asText().isEmpty()) {
            return Kind.ACCESS_OR_OTHER;
        }
        return isJfrogIssuer(iss.asText()) ? Kind.ACCESS_OR_OTHER : Kind.FOREIGN_JWT;
    }

    /** @return whether {@code issuer} has the form Access gives its own tokens. Pure. */
    public static boolean isJfrogIssuer(String issuer) {
        return JFROG_ISSUER.matcher(issuer).matches();
    }

    private static JsonNode decodeObject(String segment) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(segment);
            JsonNode node = JSON.readTree(new String(bytes, StandardCharsets.UTF_8));
            return node != null && node.isObject() ? node : null;
        } catch (IllegalArgumentException | IOException e) {
            return null;
        }
    }
}
