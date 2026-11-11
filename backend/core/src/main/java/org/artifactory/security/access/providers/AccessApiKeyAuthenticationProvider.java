package org.artifactory.security.access.providers;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.security.RealmAwareAuthenticationProvider;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.util.AccessAuthProviderUtils;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.auth.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class AccessApiKeyAuthenticationProvider implements RealmAwareAuthenticationProvider {

    private final AccessService accessService;

    public AccessApiKeyAuthenticationProvider(AccessService accessService) {
        this.accessService = accessService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Assert.isInstanceOf(PropsAuthenticationToken.class,
                authentication, "Only Props Authentication Token is supported");
        log.debug("Props authentication is being performed via Access service.");
        try {
            PropsAuthenticationToken propsAuthenticationToken = (PropsAuthenticationToken) authentication;
            String username = authentication.getName();
            String password = (String) authentication.getCredentials();
            LoginResult loginResult = accessService.ensureAuth(() -> accessService.getGrpcAuthClient()
                    .authenticate(username, password));
            SimpleUser simpleUser = AccessAuthProviderUtils.toArtifactoryUser((UserPrincipal) Objects.requireNonNull(
                    loginResult.getPrincipal()), password);
            PropsAuthenticationToken propsAuthentication =
                    new PropsAuthenticationToken(simpleUser, propsAuthenticationToken.getPropsKey(),
                            password, propsAuthenticationToken.getAuthorities());
            propsAuthentication.setDetails(authentication.getDetails());
            propsAuthentication.setAuthenticated(true);
            return propsAuthentication;
        } catch (AccessClientHttpException e) {
            AccessAuthProviderUtils.handleAccessClientHttpException(authentication, e);
            throw e;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return accessService.shouldDelegateTokenOrApiKeyAuthenticationToAccess() &&
                authentication.isAssignableFrom(PropsAuthenticationToken.class);
    }

    @Override
    public String getRealm() {
        return null;
    }

    @Override
    public void addExternalGroups(String username, Set<UserGroupInfo> groups) {
        //Not relevant
    }

    @Override
    public boolean userExists(String userName) {
        return false;
    }
}
