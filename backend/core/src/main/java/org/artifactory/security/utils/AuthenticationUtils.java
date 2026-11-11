package org.artifactory.security.utils;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.jfrog.access.common.ServiceType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.artifactory.addon.xray.XrayAddon.ARTIFACTORY_XRAY_USER;

/**
 * @author Alexei Vainshtein
 */
public class AuthenticationUtils {
    private static final String TOKEN_PREFIX = "token:";

    private AuthenticationUtils() {
    }

    /**
     * Returns if the user is an xray user/token
     *
     * @param currentUser    - The user that performed the request.
     * @param authentication - The authentication object
     * @return true if the user/authentication is from Xray, false otherwise.
     */
    public static boolean isXrayAuthenticationUser(String currentUser, Authentication authentication) {
        return ARTIFACTORY_XRAY_USER.equals(currentUser) ||
                isXrayTokenAuthentication(authentication);
    }

    /**
     * Returns if the user is an xray token
     *
     * @param authentication - The authentication object
     * @return true if the authentication is Xray token, false otherwise
     */
    public static boolean isXrayTokenAuthentication(Authentication authentication) {
        if (authentication instanceof AccessTokenAuthentication accessToken) {
            String subject = accessToken.getAccessToken().getSubject();
            if (StringUtils.isBlank(subject)) {
                return false;
            }

            return subject.startsWith(ServiceType.XRAY + "@")
                    || subject.startsWith(ServiceType.XRAY_INDEXER + "@")
                    || subject.startsWith(ServiceType.XRAY_ANALYSIS + "@")
                    || subject.startsWith(ServiceType.XRAY_PERSIST + "@");

        }
        return false;
    }

    public static boolean isPipelinesAuthentication(Authentication authentication) {
        if (authentication instanceof AccessTokenAuthentication) {
            String issuer = ((AccessTokenAuthentication) authentication).getAccessToken().getIssuer();
            if (StringUtils.isBlank(issuer)) {
                return false;
            }

            return issuer.startsWith("jfpip@");
        }
        return false;
    }

    /**
     * Returns if the user is an access token
     *
     * @return true if the authentication is access token, false otherwise
     */
    public static boolean isAccessTokenAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof AccessTokenAuthentication;
    }

    /**
     * Checks if the current authentication is an access service token.
     *
     * @return true if the current authentication is an access service token, false otherwise.
     */
    public static boolean isAccessServiceTokenAuthentication() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AccessTokenAuthentication accessToken) {
            String subject = accessToken.getAccessToken().getSubject();
            if (StringUtils.isBlank(subject)) {
                return false;
            }
            return subject.startsWith(ServiceType.ACCESS + "@")
                    || subject.startsWith(ServiceType.ACCESS_LEGACY + "@") ||
                    subject.startsWith(TOKEN_PREFIX + ServiceType.ACCESS + "@");
        }
        return false;
    }
}
