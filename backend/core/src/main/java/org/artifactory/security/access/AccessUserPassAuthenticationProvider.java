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

package org.artifactory.security.access;

import org.artifactory.api.security.SecurityService;
import org.artifactory.security.AnonymousAuthenticationToken;
import org.artifactory.security.ArtifactoryUserStatus;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.RealmAwareAuthenticationProvider;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.UserPassAuthenticationProvider;
import org.artifactory.security.access.util.AccessAuthProviderUtils;
import org.artifactory.storage.db.security.service.access.UserMapper;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.util.SecurityUtils;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.model.MessageModelCode;
import org.jfrog.access.model.Realm;
import org.jfrog.access.rest.user.LoginRequest;
import org.jfrog.access.rest.user.UserBase;
import org.jfrog.access.rest.user.UserWithGroups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * @author Noam Shemesh
 */
@Service("accessAuthenticationProvider")
public class AccessUserPassAuthenticationProvider implements RealmAwareAuthenticationProvider,
        UserPassAuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(AccessUserPassAuthenticationProvider.class);
    public static final String ACCESS_REALM = "access";

    private final AccessService accessService;
    private final UserGroupStoreService userGroupStoreService;
    private final SecurityService securityService;

    @Autowired
    public AccessUserPassAuthenticationProvider(AccessService accessService,
            UserGroupStoreService userGroupStoreService,
            SecurityService securityService) {
        this.accessService = accessService;
        this.userGroupStoreService = userGroupStoreService;
        this.securityService = securityService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getPrincipal().toString();
        String password = (String) authentication.getCredentials();

        if (SecurityUtils.isAnyExternalAuthEnabled() &&
                !accessService.isBasicAuthEnabled() && !UserInfo.isAnonymous(username)) {
            log.debug("Attempt to log in with disabled basic authentication");
            throw new AuthenticationServiceException("Basic authentication is disabled");
        }
        try {
            // RTDEV-24978 - Do not call Access if anon access is disabled
            if (!securityService.isAnonAccessEnabled() && UserInfo.isAnonymous(username)) {
                return new AnonymousAuthenticationToken();
            } else {
                UserWithGroups userRes = getUserLoggedIn(username, password);
                return new AccessResponseAuthentication(true, simpleUserFromModel(userRes, password));
            }
        } catch (AccessClientHttpException e) {
            // Case we got response from Access server and the user can't authenticate
            AccessAuthProviderUtils.handleAccessClientHttpException(authentication, e);
            throw e;
        }
    }

    @Override
    public boolean canUserLogin(String username, String password) {
        try {
            getUserLoggedIn(username, password);
            return true;
        } catch (AccessClientHttpException e) {
            // TODO [NS] Is this really the best way to find out if the user can login?
            // If all the errors are password expiration error it means that the user would've logged in if the
            //password is ok
            long numberOfExpiredPasswordErrors = e.getErrorsModel().getErrors().stream()
                    .filter(model -> model.getCode().equals(MessageModelCode.CODE_PASSWORD_EXPIRED)).count();
            return e.getErrorsModel() != null && e.getErrorsModel().getErrors().size() == numberOfExpiredPasswordErrors;
        }
    }

    private UserWithGroups getUserLoggedIn(String username, String password) {
        LoginRequest model = new LoginRequest().username(username).password(password);
        return accessService.getAccessClient().cachedAuth().authenticate(model);
    }

    private SimpleUser simpleUserFromModel(UserBase user, String password) {
        MutableUserInfo userInfo = UserMapper
                .toArtifactoryUser(user); // No need to decrypt properties as SimpleUser doesn't hold user properties
        userInfo.setPassword(new SaltedPassword(password, null));
        return new SimpleUser(userInfo);
    }

    @Override
    public String getRealm() {
        return ACCESS_REALM;
    }

    @Override
    public void addExternalGroups(String username, Set<UserGroupInfo> groups) {
        // Noop
    }

    @Override
    public boolean userExists(String username) {
        return userGroupStoreService.userExists(username);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return !accessService.isRealmAuthenticationEnabled(Realm.INTERNAL);
    }

    @Override
    @Nonnull
    public SimpleUser reauthenticateRememberMe(String username) {
        UserInfo user = userGroupStoreService.findUser(username);
        if (user == null) {
            String errorMessage = "The user: '" + username + "' does not exist";
            log.debug(errorMessage);
            throw new UsernameNotFoundException(errorMessage);
        }
        boolean lockPolicyEnabled = accessService.getUserLockPolicyConfig().getAttempts() > 0;
        if (lockPolicyEnabled && ArtifactoryUserStatus.LOCKED.equals(user.getStatus())) {
            String errorMessage = "The user: '" + username + "' is locked";
            log.debug(errorMessage);
            throw new RememberMeAuthenticationException(errorMessage);
        }
        return new SimpleUser(user);
    }

}
