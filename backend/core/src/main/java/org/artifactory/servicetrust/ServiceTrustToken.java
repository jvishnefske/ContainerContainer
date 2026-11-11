package org.artifactory.servicetrust;

import lombok.Builder;

/**
 * @author Tomer Slutsky
 */
@Builder(toBuilder = true)
public record ServiceTrustToken(String accessToken, String baseUrl, String exchangeUrl, long expirationInMillis,
                                long lastRefreshDateMillis, String tokenId, String tokenType, String scope) {
}
