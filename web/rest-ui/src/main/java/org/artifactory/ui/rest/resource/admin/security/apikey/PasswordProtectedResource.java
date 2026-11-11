package org.artifactory.ui.rest.resource.admin.security.apikey;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.security.UserInfo;
import org.artifactory.ui.rest.service.admin.security.user.userprofile.UserProfileHelperService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.ui.rest.service.admin.security.user.userprofile.UserProfileHelperService.BAD_CREDENTIALS_ERROR;

@Slf4j
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public abstract class PasswordProtectedResource extends BaseResource {

    protected UserProfileHelperService userProfileHelperService;
    protected AuthorizationService authorizationService;
    protected SecurityService securityService;
    @Context
    protected HttpServletRequest httpServletRequest;

    protected PasswordProtectedResource(UserProfileHelperService userProfileHelperService,
            AuthorizationService authorizationService, SecurityService securityService) {
        this.userProfileHelperService = userProfileHelperService;
        this.authorizationService = authorizationService;
        this.securityService = securityService;
    }

    @VisibleForTesting
    void setHttpServletRequest(HttpServletRequest httpServletRequest) {
        this.httpServletRequest = httpServletRequest;
    }

    @VisibleForTesting
    String isAllowAccess(@Nullable String overrideRealm, @Nullable String oauthProviderName) {
        UserInfo userInfo = userProfileHelperService.loadUserInfo();
        String errMsg = "";
        // requireProfileUnlock returns false if authentication method such as SAML , SSO are used.
        // in those cases we skip password verification.
        if (!securityService.requireProfileUnlock(overrideRealm, oauthProviderName)) {
            log.debug("Profile unlock is not required");
            return errMsg;
        }
        if (userInfo.isAnonymous()) {
            errMsg = "Unable to unlock settings for anonymous user";
        }
        String userPassword = userProfileHelperService.getUserPasswordFromHeader(httpServletRequest);
        if (isBlank(userPassword)) {
            log.debug("Auth header invalid");
            errMsg = BAD_CREDENTIALS_ERROR;
        }
        if (!userProfileHelperService.authenticate(userInfo, userPassword)) {
            log.debug("Auth failed");
            errMsg = BAD_CREDENTIALS_ERROR;
        }
        return errMsg;
    }

    protected Response runServiceWrapper(Supplier<Response> serviceRunnerFunction) {
        String overrideRealm = getArtifactoryRestRequest().getQueryParamByKey("realm");
        String providerName = getArtifactoryRestRequest().getQueryParamByKey("providerName");
        String errorMsg = isAllowAccess(overrideRealm, providerName);
        if (isBlank(errorMsg)) {
            return serviceRunnerFunction.get();
        }
        return artifactoryResponse.responseCode(HttpStatus.SC_UNAUTHORIZED).error(errorMsg).buildResponse();
    }
}
