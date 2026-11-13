package org.artifactory.util;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.PasswordPolicyChecker;
import org.artifactory.security.exceptions.LoginDisabledException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    void testValidatePasswordPolicyDelegatesToTheCheckerWithTheUsername() {
        PasswordPolicyChecker checker = mock(PasswordPolicyChecker.class);
        when(context.beanForType(PasswordPolicyChecker.class)).thenReturn(checker);
        when(checker.violation("alice", "a-long-unusual-passphrase-42")).thenReturn(Optional.empty());
        when(checker.violation("alice", "short")).thenReturn(Optional.of("too short"));

        assertTrue(SecurityUtils.validatePasswordPolicy("alice", "a-long-unusual-passphrase-42"));
        assertFalse(SecurityUtils.validatePasswordPolicy("alice", "short"));
        assertEquals(Optional.of("too short"), SecurityUtils.passwordPolicyViolation("alice", "short"));
        verify(checker, times(2)).violation("alice", "short");
    }

    @ParameterizedTest
    @CsvSource({"admin,password", "anonymous,", "'',"})
    void testValidatePasswordPolicyHasNoExemptions(String username, String password) {
        PasswordPolicyChecker checker = mock(PasswordPolicyChecker.class);
        when(context.beanForType(PasswordPolicyChecker.class)).thenReturn(checker);
        when(checker.violation(username, password)).thenReturn(Optional.of("refused"));
        assertFalse(SecurityUtils.validatePasswordPolicy(username, password));
    }

    @Test
    void testValidatePasswordPolicyFailsClosedWithoutAChecker() {
        when(context.beanForType(PasswordPolicyChecker.class)).thenThrow(new IllegalStateException("no bean"));
        assertFalse(SecurityUtils.validatePasswordPolicy("alice", "a-long-unusual-passphrase-42"));
        ArtifactoryContextThreadBinder.unbind();
        assertFalse(SecurityUtils.validatePasswordPolicy("alice", "a-long-unusual-passphrase-42"));
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
}
