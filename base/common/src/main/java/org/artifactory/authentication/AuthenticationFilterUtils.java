/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
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

package org.artifactory.authentication;

import jakarta.servlet.http.Cookie;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.security.props.auth.OauthManager;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.util.SessionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;

/**
 * @author Chen Keinan
 */
@UtilityClass
public class AuthenticationFilterUtils {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilterUtils.class);

    public static TokenKeyValue getTokenKeyValue(HttpServletRequest request) {
        TokenKeyValue tokenKeyValue = getTokenKeyValueFromHeader(request);
        return tokenKeyValue == null ? getTokenKeyValueFromCookies(request) : tokenKeyValue;
    }

    public static TokenKeyValue getTokenKeyValueFromHeader(HttpServletRequest request) {
        TokenKeyValue tokenKeyValue;
        // 1st check if api key token exist
        if ((tokenKeyValue = getApiKeyTokenKeyValue(request)) != null) {
            return tokenKeyValue;
            // 2nd check if oauth token exist
        }
        if ((tokenKeyValue = getOauthTokenKeyValue(request)) != null) {
            return tokenKeyValue;
        }
        return null;
    }

    public static TokenKeyValue getTokenKeyValueFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (StringUtils.equals(cookie.getName(), OauthManager.OAUTH_IDN_TOKEN_NAME)) {
                String token = cookie.getValue();
                if (StringUtils.isNotBlank(token)) {
                    return new TokenKeyValue(OauthManager.OAUTH_KEY, token);
                }
            }
        }
        return null;
    }

    /**
     * check weather api key is found on request
     *
     * @param request - http servlet request
     * @return Token key value
     */
    private static TokenKeyValue getApiKeyTokenKeyValue(HttpServletRequest request) {
        String apiKeyValue = request.getHeader(ApiKeyManager.API_KEY_HEADER);
        if (StringUtils.isBlank(apiKeyValue)) {
            apiKeyValue = request.getHeader(ApiKeyManager.OLD_API_KEY_HEADER);
        }
        if (apiKeyValue != null) {
            return new TokenKeyValue(ApiKeyManager.API_KEY, apiKeyValue);
        }
        return null;
    }

    /**
     * check weather oauth key is found on request
     *
     * @param request - http servlet request
     * @return Token key value
     */
    private static TokenKeyValue getOauthTokenKeyValue(HttpServletRequest request) {
        String oauthToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        int prefixLength = OauthManager.OAUTH_TOKEN_PREFIX.length();
        int lengthAddition =  ConstantValues.oldCheckForOathTokenLength.getBoolean() ? 1 : 0;
        if (oauthToken != null && oauthToken.startsWith(OauthManager.OAUTH_TOKEN_PREFIX) &&
                oauthToken.length() > prefixLength + lengthAddition) {
            oauthToken = oauthToken.substring(prefixLength);
            return new TokenKeyValue(OauthManager.OAUTH_KEY, oauthToken);
        }
        return null;
    }

    public static boolean isAcceptedByPropsFilter(HttpServletRequest request) {
        return getTokenKeyValue(request) != null;
    }

    public static boolean isAuthHeaderPresent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.AUTHORIZATION) != null;
    }

    public static boolean isAcceptSsoFilter(HttpServletRequest request,
            boolean httpSsoProxied, String requestVariable) {
        if (!httpSsoProxied) {
            return false;
        }
        String ssoUserName = getRemoteUserName(requestVariable, request);

        //Accept the request if the header contains an SSO username
        return StringUtils.isNotBlank(ssoUserName);
    }

    /**
     * Checks whether the request has any way of authentication attached, or http session is already authenticated.
     *
     * It might be one of the following:
     * API token is present OR OAuth token is present OR Authorization header is present OR SSO username is present
     */
    public static boolean isRequestContainsAuthentication(HttpServletRequest request, SecurityService securityService,
            AccessService accessService) {
        return isAuthHeaderPresent(request) || isAcceptedByPropsFilter(request) ||
                isAcceptSsoFilter(request, securityService.isHttpSsoProxied(),
                        securityService.getHttpSsoRemoteUserRequestVariable()) ||
                isHttpSessionAuthenticated(request) ||
                isMtlsSessionAuthenticated(request, accessService);
    }

    private static boolean isHttpSessionAuthenticated(HttpServletRequest request) {
        Authentication authentication = SessionUtils.getAuthentication(request);
        return authentication != null && authentication.isAuthenticated();
    }

    private static boolean isMtlsSessionAuthenticated(HttpServletRequest request, AccessService accessService) {
        return accessService.extractMtlsUsername(request) != null;

    }

    /**
     * Extracts the remote user name from the request, either from an attribute/header whose name was defined in the
     * SSO config or from the {@link javax.servlet.http.HttpServletRequest#getRemoteUser()} method.
     *
     * @param request   HTTP request
     * @param headerKey the header key with which to extract the username from the request
     * @return Remote user name if found. Null or blank if not.
     */
    public static String getRemoteUserName(String headerKey, HttpServletRequest request) {
        log.debug("Entering ArtifactorySsoAuthenticationFilter.getRemoteUserName");

        String ssoUserName = null;

        if (StringUtils.isNotBlank(headerKey)) {
            log.debug("Remote user request variable = '{}'.", headerKey);
            // first attempt to read from attribute (to support custom filters)
            Object userAttribute = request.getAttribute(headerKey);
            if (userAttribute != null) {
                ssoUserName = userAttribute.toString();
                log.debug("Remote user attribute: '{}'.", ssoUserName);
            }

            if (StringUtils.isBlank(ssoUserName)) {
                // check if the container got the remote user (e.g., using ajp)
                ssoUserName = request.getRemoteUser();
                log.debug("Remote user: '{}' from request.", ssoUserName);
            }

            if (StringUtils.isBlank(ssoUserName)) {
                // check if the request header contains the remote user
                ssoUserName = request.getHeader(headerKey);
                log.debug("Remote user: '{}' from header.", ssoUserName);
            }
        }
        return ssoUserName != null ? ssoUserName.toLowerCase() : null;
    }

    public static void populateLdapUserEmail(Attributes ldapUserAttributes, MutableUserInfo mutableUserInfo,
            String emailAttributeName, UserGroupService userGroupService, Logger log) {
        if (ldapUserAttributes != null) {
            Attribute emailAttribute = ldapUserAttributes.get(emailAttributeName);
            if (emailAttribute != null) {
                try {
                    String userEmail = emailAttribute.get().toString();
                    mutableUserInfo.setEmail(userEmail);
                    userGroupService.updateUser(mutableUserInfo, false);
                } catch (NamingException e) {
                    log.warn("Failed to extract user: '{}' email, due to: {}", mutableUserInfo.getUsername(),
                            e.getMessage());
                    log.debug("", e);
                } catch (NoSuchElementException e) {
                    log.warn("The user: '{}' have no email in his attributes.", mutableUserInfo.getUsername());
                    log.debug("", e);
                }
            }
        }
    }
}
