package org.artifactory.ph;

import org.artifactory.security.props.auth.model.TokenType;
import org.artifactory.security.providermgr.OAuthProviderMgr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Volodymyr Shulga
 */
class PackageHandlerCacheKeyTest {
    @Test
    void equals_sameObject() {
        PackageHandlerCacheKey key = new PackageHandlerCacheKey("user", "authHeader", "repoKey", TokenType.NIM);
        assertTrue(key.equals(key));
    }

    @Test
    void equals_differentObjectSameValues() {
        PackageHandlerCacheKey key1 = new PackageHandlerCacheKey("user", "authHeader", "repoKey", TokenType.NIM);
        PackageHandlerCacheKey key2 = new PackageHandlerCacheKey("user", "authHeader", "repoKey", TokenType.NIM);
        assertTrue(key1.equals(key2));
    }

    @Test
    void equals_differentObjectDifferentValues() {
        PackageHandlerCacheKey key1 = new PackageHandlerCacheKey("user1", "authHeader1", "repoKey1", TokenType.NIM);
        PackageHandlerCacheKey key2 = new PackageHandlerCacheKey("user2", "authHeader2", "repoKey2", TokenType.NPM);
        assertFalse(key1.equals(key2));
    }

    @Test
    void hashCode_sameObject() {
        PackageHandlerCacheKey key = new PackageHandlerCacheKey("user", "authHeader", "repoKey", TokenType.NIM);
        assertEquals(key.hashCode(), key.hashCode());
    }

    @Test
    void hashCode_differentObjectSameValues() {
        PackageHandlerCacheKey key1 = new PackageHandlerCacheKey("user", "authHeader", "repoKey", TokenType.NIM);
        PackageHandlerCacheKey key2 = new PackageHandlerCacheKey("user", "authHeader", "repoKey", TokenType.NIM);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void hashCode_differentObjectDifferentValues() {
        PackageHandlerCacheKey key1 = new PackageHandlerCacheKey("user1", "authHeader1", "repoKey1", TokenType.NIM);
        PackageHandlerCacheKey key2 = new PackageHandlerCacheKey("user2", "authHeader2", "repoKey2", TokenType.NPM);
        assertNotEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void getProviderMgr_returnsOAuthProviderMgr() {
        PackageHandlerCacheKey key = new PackageHandlerCacheKey("user", "authHeader", "repoKey", TokenType.NIM);
        assertTrue(key.getProviderMgr() instanceof OAuthProviderMgr);
    }

    @Test
    void getTokenType_returnsCorrectTokenType() {
        PackageHandlerCacheKey key = new PackageHandlerCacheKey("user", "authHeader", "repoKey", TokenType.NIM);
        assertEquals(TokenType.NIM, key.getTokenType());
    }
}