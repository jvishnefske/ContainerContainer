package org.artifactory.addon.ansible;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.apache.commons.codec.binary.Hex.encodeHexString;

/**
 * Cache key for SSO tokens, based on repository key, auth URL,
 * and SHA-256 digest of the refresh token (to avoid storing the raw
 * token in a structure whose toString may appear in logs or heap dumps).
 */
public record SsoTokenCacheKey(String repoKey, String authUrl,
        String refreshTokenDigest) {

    public static SsoTokenCacheKey of(String repoKey,
            String authUrl, String refreshToken) {
        return new SsoTokenCacheKey(
                repoKey, authUrl, sha256Hex(refreshToken));
    }

    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return encodeHexString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 not available", e);
        }
    }
}
