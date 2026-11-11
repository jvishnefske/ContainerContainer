package org.artifactory.ph;

import org.artifactory.security.props.auth.model.TokenType;
import org.artifactory.security.providermgr.ArtifactoryCacheKey;
import org.artifactory.security.providermgr.OAuthProviderMgr;
import org.artifactory.security.providermgr.ProviderMgr;

import java.util.Objects;

/**
 * Cache key implementation for package handler.
 * @author Volodymyr Shulga
 */

public class PackageHandlerCacheKey extends ArtifactoryCacheKey {
    private final TokenType tokenType;

    public PackageHandlerCacheKey(String user, String authHeader, String repoKey, TokenType tokenType) {
        super(user, authHeader, repoKey, null);
        this.tokenType = tokenType;
    }

    @Override
    public ProviderMgr getProviderMgr() {
        return new OAuthProviderMgr();
    }

    @Override
    public TokenType getTokenType() {
        return tokenType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        PackageHandlerCacheKey that = (PackageHandlerCacheKey) o;
        return tokenType == that.tokenType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tokenType);
    }
}
