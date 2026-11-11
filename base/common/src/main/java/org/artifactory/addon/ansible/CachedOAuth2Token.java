package org.artifactory.addon.ansible;

import lombok.Getter;
import org.artifactory.security.props.auth.model.OAuth2TokenModel;

import java.util.concurrent.TimeUnit;

/**
 * Wrapper for {@link OAuth2TokenModel} that tracks when the token was fetched
 * to support dynamic expiry based on the OAuth2 response's expires_in value.
 */
@Getter
public class CachedOAuth2Token {

    /**
     * Buffer time before token expiry to trigger a proactive refresh.
     */
    private static final long EXPIRY_BUFFER_MILLIS =
            TimeUnit.SECONDS.toMillis(60);

    /**
     * Default expiry when OAuth2 response omits or returns zero for
     * expires_in. Prevents refetch loop when provider does not specify expiry.
     */
    private static final long DEFAULT_EXPIRY_MILLIS =
            TimeUnit.SECONDS.toMillis(3600);

    private final OAuth2TokenModel tokenModel;
    private final long fetchedAtMillis;

    public CachedOAuth2Token(OAuth2TokenModel tokenModel) {
        this.tokenModel = tokenModel;
        this.fetchedAtMillis = System.currentTimeMillis();
    }

    public String getAccessToken() {
        return tokenModel.getAccessToken();
    }

    /**
     * Returns {@code true} when the token is expired or will expire within
     * the {@link #EXPIRY_BUFFER_MILLIS buffer}. Negative {@code expires_in}
     * values are treated as already expired.
     */
    public boolean isExpiredOrExpiringSoon() {
        if (tokenModel.getExpiresIn() < 0) {
            return true;
        }
        return System.currentTimeMillis()
                >= computeExpiresAtMillis() - EXPIRY_BUFFER_MILLIS;
    }

    /**
     * Returns {@code true} only when the token has passed its actual expiry
     * time (no buffer). Used to decide whether a fallback to this token is
     * meaningful -- tokens that are merely "expiring soon" may still be
     * accepted by the remote server.
     */
    public boolean isDefinitivelyExpired() {
        if (tokenModel.getExpiresIn() < 0) {
            return true;
        }
        return System.currentTimeMillis() >= computeExpiresAtMillis();
    }

    private long computeExpiresAtMillis() {
        int expiresIn = tokenModel.getExpiresIn();
        return expiresIn == 0
                ? fetchedAtMillis + DEFAULT_EXPIRY_MILLIS
                : fetchedAtMillis + TimeUnit.SECONDS.toMillis(expiresIn);
    }
}
