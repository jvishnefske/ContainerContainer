package org.artifactory.security.access.providers;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.UiAuthenticationDetails;
import org.artifactory.api.security.SecurityService;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.response.ResponseThreadLocal;
import org.artifactory.security.AnonymousAuthenticationToken;
import org.artifactory.security.EncryptedPasswordAuthenticationToken;
import org.artifactory.security.InternalUsernamePasswordAuthenticationToken;
import org.artifactory.security.RealmAwareAuthenticationProvider;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessResponseAuthentication;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.util.AccessAuthProviderUtils;
import org.artifactory.storage.db.security.service.access.UserMapper;
import org.artifactory.util.SecurityUtils;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.auth.UserPrincipal;
import org.jfrog.access.model.Realm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author Mor Merhav
 */
@Slf4j
@Component
public class AccessInternalAuthenticationProvider implements RealmAwareAuthenticationProvider {

    private final AccessService accessService;
    private final SecurityService securityService;

    @Autowired
    public AccessInternalAuthenticationProvider(AccessService accessService,
            SecurityService securityService) {
        this.accessService = accessService;
        this.securityService = securityService;
    }

    @Override
    public String getRealm() {
        return Realm.INTERNAL.getName();
    }

    @Override
    public void addExternalGroups(String username, Set<UserGroupInfo> groups) {
        // Noop
    }

    @Override
    public boolean userExists(String userName) {
        return false; // Not called for internal
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = (String) authentication.getCredentials();

        SecurityUtils.forceEnableBasicAuthIfNeeded();

        // RTDEV-24978 - Do not call Access if anon access is disabled
        if (!securityService.isAnonAccessEnabled() && UserInfo.isAnonymous(authentication.getName())) {
            return new AnonymousAuthenticationToken();
        }
        log.debug("Internal authentication is being performed via Access service.");

        try {/**/
            Object details = authentication.getDetails();
            LoginResult loginResult;
            if (details instanceof UiAuthenticationDetails ||
                    authentication instanceof InternalUsernamePasswordAuthenticationToken) {
                loginResult = accessService.ensureAuth(() -> accessService.getGrpcAuthClient()
                        .uiLogin(username, password));
            } else {
                String updatedPassword =
                        authentication instanceof EncryptedPasswordAuthenticationToken authenticationToken ?
                                authenticationToken.getEncryptedPassword() : password;
                loginResult = accessService.ensureAuth(() -> accessService.getGrpcAuthClient()
                        .authenticate(username, updatedPassword));
            }

            if (!loginResult.isSuccessful() || loginResult.getPrincipal() == null) {
                throw new AccessClientException("Authentication is not successful");
            }

            Optional<Cookie> optionalCookie = Optional.ofNullable(AccessCookieParser.parse(loginResult.getCookie()));
            Optional<HttpServletResponse> optionalResponse = Optional.ofNullable(ResponseThreadLocal.getResponse());
            Optional<HttpServletRequest> optionalRequest = Optional.ofNullable(RequestThreadLocal.getRequest());

            optionalCookie.ifPresent(cookie -> {
                optionalResponse.ifPresent(response -> response.addCookie(cookie));
                optionalRequest.ifPresent(request -> request.setAttribute(cookie.getName(), cookie.getValue()));
            });

            SimpleUser simpleUser = UserMapper.toArtifactoryUser((UserPrincipal) Objects.requireNonNull(
                    loginResult.getPrincipal()), password);

            return new AccessResponseAuthentication(true, simpleUser);
        } catch (AccessClientHttpException e) {
            // Case we got response from Access server and the user can't authenticate
            AccessAuthProviderUtils.handleAccessClientHttpException(authentication, e);
            throw e;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return accessService.isRealmAuthenticationEnabled(Realm.INTERNAL);
    }
}
