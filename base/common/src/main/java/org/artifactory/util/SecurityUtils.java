package org.artifactory.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.security.SecurityInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.exceptions.LoginDisabledException;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.auth.exception.PasswordPolicyException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.artifactory.api.security.SecurityService.DEFAULT_ADMIN_PASSWORD;
import static org.artifactory.api.security.SecurityService.DEFAULT_ADMIN_USER;

/**
 * @author Mor Merhav
 */
@Slf4j
@UtilityClass
public class SecurityUtils {

    public void forceEnableBasicAuthIfNeeded() {
        AccessService accessService = getAccessService();
        if (!SecurityUtils.isAnyExternalAuthEnabled() && !accessService.isBasicAuthEnabled()) {
            log.warn("Force enabling basic auth due to 0 external authentication providers");
            AccessConfigHolder.getInstance().addBasicAuthenticationEnabled(true);
            accessService.updateConfigurationModel(AccessConfigHolder.getInstance().drainConfig());
            getSecurityService().setForceEnabledBasicAuth(true);
        }
    }

    public String removeIllegalChars(String str) {
        if (str == null) {
            return null;
        }
        return str.replaceAll("[\n\r]", "");
    }

    public boolean isAnyExternalAuthEnabled() {
        return getCentralConfigService().getDescriptor().getSecurity().isAnyExternalAuthEnabled() ||
                getAccessService().isAnyExternalAuthEnabled();
    }

    public boolean ensureLoginAttemptAllowed(HttpServletRequest request, HttpServletResponse response,
            String loginIdentifier, BasicAuthenticationEntryPoint authenticationEntryPoint) throws IOException {
        try {
            long accessTime = HttpUtils.getSessionAccessTime(request);
            String remoteClientAddress = HttpUtils.getRemoteClientAddress(request);
            if (loginIdentifier == null) {
                return true;
            }
            if (loginIdentifier.isEmpty()) {
                return ConstantValues.securityAuthenticationAllowEmptyLoginIdentifier.getBoolean();
            }
            getSecurityService().ensureUserIsNotLocked(loginIdentifier);
            getSecurityService().ensureLoginShouldNotBeDelayed(loginIdentifier);
            getSecurityService().updateUserLastAccess(loginIdentifier, remoteClientAddress, accessTime);
        } catch (URISyntaxException e) {
            log.info("Login attempt isn't allowed, failed to get remote client address, cause: {}", e.getMessage(), e);
            authenticationEntryPoint.commence(request, response, new DisabledException(e.getMessage()));
            return false;
        } catch (LockedException | LoginDisabledException e) {
            log.info("Login attempt isn't allowed, cause: {}", e.getMessage(), e);
            authenticationEntryPoint.commence(request, response, e);
            return false;
        }
        return true;
    }

    /**
     * This function is used for encrypting sensitive data that should not be displayed as part of the security
     * descriptor (that should be removed in the future)
     *
     * @return SecurityInfo with encrypted values of sensitive data
     */
    public SecurityInfo getEncryptSecurityInfo() {
        SecurityInfo securityInfo = getSecurityService().getSecurityData(false);
        SecurityInfo encryptSecurityInfo = InfoFactoryHolder.get().createSecurityInfo(
                securityInfo.getUsers(),
                securityInfo.getGroups(),
                securityInfo.getRepoAcls(),
                securityInfo.getBuildAcls(),
                securityInfo.getReleaseBundleAcls()
        );
        encryptSecurityInfo.setVersion(securityInfo.getVersion());
        return encryptSecurityInfo;
    }

    private SecurityService getSecurityService() {
        return ContextHelper.get().beanForType(SecurityService.class);
    }

    private CentralConfigService getCentralConfigService() {
        return ContextHelper.get().getCentralConfig();
    }

    private AccessService getAccessService() {
        return ContextHelper.get().beanForType(AccessService.class);
    }

    public boolean validatePasswordPolicy(String username, String password) {
        AccessClient accessClient = getAccessService().getAccessClient();
        boolean isPolicyEnforcementEnabled =
                accessClient.config().getPasswordPolicyEnabled().isEnabled();
        if (isPolicyEnforcementEnabled) {
            if (isSkipPasswordPolicyValidation(username, password)) {
                return true;
            }

            try {
                accessClient.cachedAuth().verifyPassword(password);
            } catch (PasswordPolicyException e) {
                log.debug("Password Policy violated for user '{}': {}", username, e.getMessage());
                return false;
            }
        }

        return true;
    }

    private boolean isSkipPasswordPolicyValidation(String username, String password) {
        return isDefaultAdmin(username, password) || isDefaultAnonymous(username) || EMPTY.equals(username);
    }

    private boolean isDefaultAnonymous(String username) {
        return UserInfo.isAnonymous(username);
    }

    private boolean isDefaultAdmin(String username, String password) {
        return DEFAULT_ADMIN_USER.equals(username) && DEFAULT_ADMIN_PASSWORD.equals(password);
    }
}
