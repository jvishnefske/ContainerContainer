/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.service.setmeup;

import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NixSetMeUpUtils}.
 *
 * @author Natalie Berni
 */
class NixSetMeUpUtilsTest {

    private static final String ED25519_PEM = """
            -----BEGIN PUBLIC KEY-----
            MCowBQYDK2VwAyEAcNpfNfHQEZ+rzOkvB985IKlWFT4nxl+G3HwxV6t92EY=
            -----END PUBLIC KEY-----
            """;

    // Raw 32-byte Ed25519 public key extracted from the SPKI above (header stripped, re-encoded).
    private static final String EXPECTED_RAW_BASE64 = "cNpfNfHQEZ+rzOkvB985IKlWFT4nxl+G3HwxV6t92EY=";

    private static final String ALIAS = "test-ed-alias";

    @Test
    void toNixTrustedPublicKey_validEd25519_returnsAliasAndRawBase64() {
        Optional<String> result = NixSetMeUpUtils.toNixTrustedPublicKey(buildKeyPair(ALIAS, ED25519_PEM));
        assertTrue(result.isPresent());
        assertEquals(ALIAS + ":" + EXPECTED_RAW_BASE64, result.get());
    }

    @Test
    void toNixTrustedPublicKey_nullDescriptor_returnsEmpty() {
        assertFalse(NixSetMeUpUtils.toNixTrustedPublicKey((KeyPairDescriptor) null).isPresent());
    }

    @Test
    void toNixTrustedPublicKey_nonEd25519Type_returnsEmpty() {
        KeyPairDescriptor kp = buildKeyPair(ALIAS, ED25519_PEM);
        kp.setKeyType(KeyPairDescriptor.RSA_TYPE);
        assertFalse(NixSetMeUpUtils.toNixTrustedPublicKey(kp).isPresent());
    }

    @Test
    void toNixTrustedPublicKey_blankAlias_returnsEmpty() {
        KeyPairDescriptor kp = buildKeyPair("", ED25519_PEM);
        assertFalse(NixSetMeUpUtils.toNixTrustedPublicKey(kp).isPresent());
    }

    @Test
    void toNixTrustedPublicKey_blankPublicKey_returnsEmpty() {
        KeyPairDescriptor kp = buildKeyPair(ALIAS, "");
        assertFalse(NixSetMeUpUtils.toNixTrustedPublicKey(kp).isPresent());
    }

    @Test
    void toNixTrustedPublicKey_invalidPem_returnsEmpty() {
        KeyPairDescriptor kp = buildKeyPair(ALIAS, "not a valid PEM blob");
        assertFalse(NixSetMeUpUtils.toNixTrustedPublicKey(kp).isPresent());
    }

    @Test
    void toNixTrustedPublicKey_wrongAlgorithmKey_returnsEmpty() {
        // An RSA SPKI parsed as Ed25519 will have the wrong total length.
        String rsaPem = """
                -----BEGIN PUBLIC KEY-----
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxjFGVHFKR1nF8Q+VrYjr
                gJrHRWxc9CXPHzfNTd7eRcVCWNu3rNpZUlJlT4LmYDl8gp+rCsGcdz2yPnMRY6Jx
                hVJYx0PpJtFcVHcDM0qK4VPmJzD0RZi6QGr7jBy/8r0Y3vCLLnXVqLwk5W4lZb6P
                kBp5HpZUjN9hQyW+J8d3qBqLJlk5qPLkPMQI8ZQp1KZ3OawHwYO5x8VmQpqWVrCC
                XZdiPGdmrcZQQ6dAhWcaJjMu2dvLwFqQ6Vt8VuVfRJJI7Wgo8gpkX5RcwYGyEcCz
                lO9TgVqDxRTHs4dxhPRbf2dxa1XdfFFD6yPo2L7Fyv5C0aN2GfwGYjVlcF+Y6jbN
                vQIDAQAB
                -----END PUBLIC KEY-----
                """;
        assertFalse(NixSetMeUpUtils.toNixTrustedPublicKey(buildKeyPair(ALIAS, rsaPem)).isPresent());
    }

    // ---- extractRepoKeyFromUrl ----

    @Test
    void extractRepoKeyFromUrl_typicalArtifactoryUrl_returnsRepoKey() {
        assertEquals("nix-local", NixSetMeUpUtils.extractRepoKeyFromUrl(
                "https://upstream-art/artifactory/nix-local"));
    }

    @Test
    void extractRepoKeyFromUrl_trailingSlash_returnsRepoKey() {
        assertEquals("nix-local", NixSetMeUpUtils.extractRepoKeyFromUrl(
                "https://upstream-art/artifactory/nix-local/"));
    }

    @Test
    void extractRepoKeyFromUrl_noPath_returnsNull() {
        assertNull(NixSetMeUpUtils.extractRepoKeyFromUrl("https://cache.nixos.org"));
    }

    @Test
    void extractRepoKeyFromUrl_rootPathOnly_returnsNull() {
        assertNull(NixSetMeUpUtils.extractRepoKeyFromUrl("https://cache.nixos.org/"));
    }

    @Test
    void extractRepoKeyFromUrl_nullUrl_returnsNull() {
        assertNull(NixSetMeUpUtils.extractRepoKeyFromUrl(null));
    }

    @Test
    void extractRepoKeyFromUrl_blankUrl_returnsNull() {
        assertNull(NixSetMeUpUtils.extractRepoKeyFromUrl("   "));
    }

    @Test
    void extractRepoKeyFromUrl_malformedUrl_returnsNull() {
        assertNull(NixSetMeUpUtils.extractRepoKeyFromUrl("://not a url"));
    }

    private static KeyPairDescriptor buildKeyPair(String alias, String publicKeyPem) {
        KeyPairDescriptor kp = new KeyPairDescriptor();
        kp.setKey("test-pair");
        kp.setAlias(alias);
        kp.setKeyType(KeyPairDescriptor.ED25519_TYPE);
        kp.setPublicKey(publicKeyPem);
        return kp;
    }
}
