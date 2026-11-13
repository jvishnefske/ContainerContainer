package org.artifactory.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.AccessConfigHolder;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.security.PasswordPolicyChecker;
import org.artifactory.security.SecurityInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.exceptions.LoginDisabledException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Security helpers shared by the web and service layers. The password policy check
 * ({@link #validatePasswordPolicy}) is the local IA6 policy; no password, including a default administrator
 * password, is exempt from it.
 *
 * @author Mor Merhav
 */
@Slf4j
@UtilityClass
public class SecurityUtils {

    private static final String POLICY_UNAVAILABLE = "the password policy is not available";

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

    /**
     * Tells whether a new password passes the local password policy (IA6): at least 12 characters, not equal to or
     * containing the username, not in the bundled common-password list. The check is delegated to the
     * {@link PasswordPolicyChecker} bean and needs no network access. It fails closed: without an application
     * context or checker bean the password is refused.
     * <p>
     * Use it where a password is set (changes, administrator resets); it is not a login-time gate.
     *
     * @param username the account name, or {@code null} when unknown
     * @param password the candidate password
     * @return {@code true} if the password is acceptable
     */
    public boolean validatePasswordPolicy(String username, String password) {
        return passwordPolicyViolation(username, password).isEmpty();
    }

    /**
     * Checks a new password against the local password policy, see {@link #validatePasswordPolicy}.
     *
     * @param username the account name, or {@code null} when unknown
     * @param password the candidate password
     * @return empty when acceptable, otherwise a message naming the violated rules (never the password)
     */
    public Optional<String> passwordPolicyViolation(String username, String password) {
        ArtifactoryContext context = ContextHelper.get();
        PasswordPolicyChecker checker = null;
        if (context != null) {
            try {
                checker = context.beanForType(PasswordPolicyChecker.class);
            } catch (RuntimeException e) {
                log.debug("Password policy checker is not available", e);
            }
        }
        if (checker == null) {
            log.warn("Password policy checker is not available; refusing the password of user '{}'", username);
            return Optional.of(POLICY_UNAVAILABLE);
        }
        return checker.violation(username, password);
    }
}
