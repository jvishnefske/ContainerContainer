package org.artifactory.util;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.exceptions.LoginDisabledException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.auth.AuthClient;
import org.jfrog.access.client.auth.exception.PasswordPolicyException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Dvir Twina
 */
class SecurityUtilsTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private SecurityService securityService;
    private AutoCloseable closeable;

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        Mockito.when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        ArtifactoryHomeStub.get().getArtifactoryProperties()
                .setProperty(ConstantValues.securityAuthenticationAllowEmptyLoginIdentifier.getPropertyName(), "true");
    }

    @AfterEach
    public void cleanup() throws Exception {
        unbindArtifactoryHome();
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    public void testRemoveIllegalChars() {
        String input = "Hello\nWorld\r!";
        String expected = "HelloWorld!";
        String actual = SecurityUtils.removeIllegalChars(input);
        assertEquals(expected, actual, "The illegal characters should be removed from the string");
        assertNull(SecurityUtils.removeIllegalChars(null));
        assertEquals("", SecurityUtils.removeIllegalChars(""));
    }

    @Test
    void testValidatePasswordPolicyWhenDisabled() {
        setMocksForPasswordValidation(false);
        assertTrue(SecurityUtils.validatePasswordPolicy(null, null));
    }

    @ParameterizedTest
    @CsvSource({"admin,password", "anonymous,", "'',"})
    void testValidatePasswordPolicyWithSpecialUsers(String username, String password) {
        setMocksForPasswordValidation(true);
        assertTrue(SecurityUtils.validatePasswordPolicy(username, password));
    }

    @Test
    void testValidatePasswordPolicyWithFailedValidation() {
        AccessClient accessClient = setMocksForPasswordValidation(true);
        AuthClient authClient = mock(AuthClient.class);
        when(accessClient.cachedAuth()).thenReturn(authClient);
        doThrow(PasswordPolicyException.class).when(authClient).verifyPassword(anyString());

        assertFalse(SecurityUtils.validatePasswordPolicy("user", "invalidpass"));
    }


    @ParameterizedTest
    @ValueSource(strings = {""})
    @NullSource
    void testEnsureLoginAttemptAllowedWithBlankLoginIdentifier(String loginIdentifier) throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        BasicAuthenticationEntryPoint entryPoint = mock(BasicAuthenticationEntryPoint.class);
        assertTrue(SecurityUtils.ensureLoginAttemptAllowed(request, response, loginIdentifier, entryPoint));
    }

    @Test
    void testEnsureLoginAttemptAllowed() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        BasicAuthenticationEntryPoint entryPoint = mock(BasicAuthenticationEntryPoint.class);
        assertTrue(SecurityUtils.ensureLoginAttemptAllowed(request, response, "identifier", entryPoint));
        // delayed user
        doThrow(LoginDisabledException.class).when(securityService).ensureLoginShouldNotBeDelayed(anyString());
        assertFalse(SecurityUtils.ensureLoginAttemptAllowed(request, response, "identifier", entryPoint));
        // locked user
        doThrow(LockedException.class).when(securityService).ensureUserIsNotLocked(anyString());
        assertFalse(SecurityUtils.ensureLoginAttemptAllowed(request, response, "identifier", entryPoint));
    }

    private AccessClient setMocksForPasswordValidation(boolean isValidationEnabled) {
        AccessClient accessClient = mock(AccessClient.class, Answers.RETURNS_DEEP_STUBS);
        AccessService accessService = mock(AccessService.class);
        when(context.beanForType(AccessService.class)).thenReturn(accessService);
        when(accessService.getAccessClient()).thenReturn(accessClient);
        when(accessClient.config().getPasswordPolicyEnabled().isEnabled()).thenReturn(isValidationEnabled);

        return accessClient;
    }
}