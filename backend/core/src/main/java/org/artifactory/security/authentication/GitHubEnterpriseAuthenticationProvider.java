package org.artifactory.security.authentication;

import org.apache.http.HttpHeaders;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.oauth.OAuthHandler;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.security.ExternalProviderBase;
import org.artifactory.security.RealmAwareAuthenticationProvider;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.OauthManager;
import org.artifactory.security.props.auth.model.AuthenticationModel;
import org.artifactory.security.props.auth.model.OauthErrorModel;
import org.artifactory.security.props.auth.model.OauthModel;
import org.artifactory.webapp.servlet.RequestUtils;
import org.jfrog.access.client.model.OAuthSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Set;

/**
 * @author Alexei Vainshtein
 */
@Component
public class GitHubEnterpriseAuthenticationProvider extends ExternalProviderBase
        implements RealmAwareAuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubEnterpriseAuthenticationProvider.class);
    private final UserGroupService userGroupService;
    private final AccessService accessService;

    @Autowired
    public GitHubEnterpriseAuthenticationProvider(AddonsManager addonsManager, UserGroupService userGroupService,
            AccessService accessService) {
        super(addonsManager);
        this.userGroupService = userGroupService;
        this.accessService = accessService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        log.debug("Trying to authenticate using GitHub enterprise.");
        HttpServletRequest request = RequestThreadLocal.getRequest();
        if (request == null) {
            return null;
        }
        OAuthHandler oAuthHandler = ContextHelper.get().beanForType(OAuthHandler.class);
        List<OAuthSetting> oauthSettings = accessService.getAllOAuthSettings();
        var oAuthGeneralSettings = accessService.getOAuthGeneralSettings();
        if (!oAuthGeneralSettings.isEnable() || oauthSettings == null || oauthSettings.isEmpty()) {
            log.debug("Oauth settings were removed. Can't use authentication provider.");
            return null;
        }
        String defaultProvider = oAuthGeneralSettings.getDefaultSetting();
        OauthModel model = oAuthHandler.getCreateToken(defaultProvider, request.getHeader(HttpHeaders.AUTHORIZATION));
        if (model instanceof OauthErrorModel) {
            log.debug("Authentication with GitHubEnterprise failed with status code: {} and message: {}",
                    ((OauthErrorModel) model).getStatusCode(), ((OauthErrorModel) model).getInternalErrorMsg());
            return null;
        }
        OAuthSetting defaultOAuthSetting = oauthSettings.stream()
                .filter(oAuthSetting -> oAuthSetting.getName().equals(defaultProvider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Default OAuth setting not found"));

        UserInfo userInfo = userGroupService
                .findOrCreateExternalAuthUser(authentication.getPrincipal().toString(),
                        !defaultOAuthSetting.isAutoUserCreation(), defaultOAuthSetting.isAllowUserToAccessProfile());
        userGroupService.createPropsToken(authentication.getPrincipal().toString(), OauthManager.OAUTH_KEY,
                ((AuthenticationModel) model).getToken());
        log.debug("Successfully authenticated using GitHub enterprise.");
        return new OauthAuthentication(new SimpleUser(userInfo), authentication.getCredentials(),
                authentication.getAuthorities(), model);
    }

    @Override
    public boolean supports(Class<?> aClass) {
        HttpServletRequest request = RequestThreadLocal.getRequest();
        if (request == null) {
            return false;
        }

        String url = request.getRequestURI();
        if (!url.contains("api/docker/") && !url.contains("api/oci/")) {
            log.debug("Non docker request. Github provider supports only docker requests.");
            return false;
        }

        if (!url.endsWith("v2/token")) {
            log.debug("Non token request. Github provider supports only docker token requests");
            return false;
        }

        if (!RequestUtils.isBasicAuthHeaderPresent(request)) {
            log.debug("No basic auth header. Github provider supports only docker token requests with auth header");
            return false;
        }
        return accessService.isOAuthEnabled();
    }

    @Override
    public String getRealm() {
        return OAuthHandler.REALM;
    }

    @Override
    public void addExternalGroups(String username, Set<UserGroupInfo> groups) {
        // Not required
    }

    @Override
    public boolean userExists(String userName) {
        return userGroupService.findUser(userName, false) != null;
    }
}
