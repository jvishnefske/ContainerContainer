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

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.artifactory.addon.plugin.groovy.CredentialRouting.Kind.ACCESS_OR_OTHER;
import static org.artifactory.addon.plugin.groovy.CredentialRouting.Kind.FOREIGN_JWT;
import static org.artifactory.addon.plugin.groovy.CredentialRouting.Route.REALM_BASIC;
import static org.artifactory.addon.plugin.groovy.CredentialRouting.Route.REALM_BEARER;
import static org.artifactory.addon.plugin.groovy.CredentialRouting.Route.STOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2: which credentials plugin realms may see; Access tokens never. */
class CredentialRoutingTest {

    static String jwt(String headerJson, String payloadJson) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return enc.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)) + ".c2ln";
    }

    static String foreignJwt() {
        return jwt("{\"alg\":\"RS256\",\"kid\":\"k\"}", "{\"iss\":\"https://login.example.com/\",\"sub\":\"a\"}");
    }

    static String accessJwt() {
        return jwt("{\"alg\":\"RS256\",\"kid\":\"k\"}",
                "{\"iss\":\"jfac@01h7example\",\"sub\":\"jfac@01h7example/users/admin\"}");
    }

    @Test
    void jwtsWithAUrlIssuerAreForeign() {
        assertEquals(FOREIGN_JWT, CredentialRouting.classify(foreignJwt()));
        assertEquals(FOREIGN_JWT, CredentialRouting.classify(jwt("{\"alg\":\"none\"}", "{\"iss\":\"x y\"}")));
    }

    @Test
    void accessIssuedTokensAndNonJwtsKeepTheStockPath() {
        assertEquals(ACCESS_OR_OTHER, CredentialRouting.classify(accessJwt()));
        assertEquals(ACCESS_OR_OTHER, CredentialRouting.classify(jwt("{\"alg\":\"RS256\"}",
                "{\"iss\":\"jfrt@01h7example/users/admin\"}")));
        assertEquals(ACCESS_OR_OTHER, CredentialRouting.classify(jwt("{\"alg\":\"RS256\"}", "{\"sub\":\"a\"}")));
        assertEquals(ACCESS_OR_OTHER, CredentialRouting.classify(jwt("{\"alg\":\"RS256\"}", "{\"iss\":5}")));
        assertEquals(ACCESS_OR_OTHER, CredentialRouting.classify(jwt("[1]", "{\"iss\":\"https://x/\"}")));
        assertEquals(ACCESS_OR_OTHER, CredentialRouting.classify("password"));
        assertEquals(ACCESS_OR_OTHER, CredentialRouting.classify("cmVmdGtu" + "A".repeat(56)));
        assertEquals(ACCESS_OR_OTHER, CredentialRouting.classify("a.b.c"));
        assertEquals(ACCESS_OR_OTHER, CredentialRouting.classify(null));
        assertEquals(ACCESS_OR_OTHER, CredentialRouting.classify(foreignJwt() + "x".repeat(70_000)));
        assertTrue(CredentialRouting.isJfrogIssuer("jfac@01h7"));
        assertFalse(CredentialRouting.isJfrogIssuer("https://login.example.com/"));
    }

    @Test
    void routeOnlyForeignJwtsToRealmsAndOnlyWhenRealmsAreLoaded() {
        assertEquals(REALM_BEARER, CredentialRouting.route(true, true, false, FOREIGN_JWT));
        assertEquals(REALM_BASIC, CredentialRouting.route(true, false, true, FOREIGN_JWT));
        assertEquals(STOCK, CredentialRouting.route(true, false, false, FOREIGN_JWT));
        assertEquals(STOCK, CredentialRouting.route(true, true, false, ACCESS_OR_OTHER));
        assertEquals(STOCK, CredentialRouting.route(true, false, true, ACCESS_OR_OTHER));
        assertEquals(STOCK, CredentialRouting.route(false, true, false, FOREIGN_JWT));
        assertEquals(STOCK, CredentialRouting.route(false, false, true, FOREIGN_JWT));
    }
}
