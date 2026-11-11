package org.artifactory.addon.ansible;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.jfrog.common.ExceptionLogUtils.debugOrError;

/**
 * Utility class for creating OAuth2 refresh-token grant requests.
 * Handles JWT parsing to extract the client_id ('azp' claim) and
 * constructs the HTTP POST request for the token endpoint.
 */
@Slf4j
@UtilityClass
public class OAuth2TokenRequestUtils {

    private static final String OAUTH2_GRANT_TYPE = "grant_type";
    private static final String OAUTH2_GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
    private static final String OAUTH2_CLIENT_ID = "client_id";
    private static final String OAUTH2_REFRESH_TOKEN = "refresh_token";

    // Standard OIDC claim (Keycloak, Azure AD, Auth0, RedHat SSO)
    private static final String JWT_CLAIM_AZP = "azp";

    @Nullable
    public static HttpPost createRefreshTokenRequest(String authUrl, String refreshToken) {
        String clientId = extractClientIdFromToken(refreshToken);
        if (clientId == null) {
            log.warn("Cannot create token request - unable to extract client_id from refresh token.");
            return null;
        }
        HttpPost request = new HttpPost(authUrl);
        request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType());
        request.setEntity(new UrlEncodedFormEntity(List.of(
                new BasicNameValuePair(OAUTH2_GRANT_TYPE, OAUTH2_GRANT_TYPE_REFRESH_TOKEN),
                new BasicNameValuePair(OAUTH2_CLIENT_ID, clientId),
                new BasicNameValuePair(OAUTH2_REFRESH_TOKEN, refreshToken)
        ), StandardCharsets.UTF_8));
        return request;
    }

    @Nullable
    private static String extractClientIdFromToken(String refreshToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(refreshToken);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            String azp = claims.getStringClaim(JWT_CLAIM_AZP);
            if (StringUtils.isNotBlank(azp)) {
                log.debug("Extracted client_id '{}' from JWT 'azp' claim", azp);
                return azp;
            }
            log.error("JWT refresh token does not contain 'azp' claim");
        } catch (Exception e) {
            debugOrError(log, "Failed to parse refresh token as JWT to extract client_id", e);
        }
        return null;
    }
}
