package org.artifactory.authentication;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.http.HttpHeaders;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.props.auth.OauthManager;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Test class for AuthenticationFilterUtils
 * 
 * @author Eli Fishman
 */
class AuthenticationFilterUtilsTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private HttpServletRequest request;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * Test valid Bearer tokens that should return TokenKeyValue
     */
    @ParameterizedTest
    @MethodSource("validBearerTokenProvider")
    void testGetOauthTokenKeyValueWithValidBearerTokens(String authorizationHeader, String expectedToken) {
        // Given
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorizationHeader);

        // When
        TokenKeyValue result = AuthenticationFilterUtils.getTokenKeyValueFromHeader(request);

        // Then
        assertNotNull(result);
        assertEquals(OauthManager.OAUTH_KEY, result.getKey());
        assertEquals(expectedToken, result.getToken());
    }

    /**
     * Test invalid authorization headers that should return null
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "", // Empty string
        "Bearer ", // Bearer with empty token
        "Basic dXNlcjpwYXNz", // Basic auth
        "Digest username=\"user\"", // Digest auth
        "Bearer", // Bearer without space
        "bearer token", // lowercase bearer
        "BEARER token", // uppercase bearer
        "Bearer\t", // tab instead of space
        "Bearer\n", // newline instead of space
        "Beerer TT", // Misspelled Bearer
        "BearerT", // No space after Bearer
    })
    void testGetOauthTokenKeyValueWithInvalidFormats(String authorizationHeader) {
        // Given
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorizationHeader);

        // When
        TokenKeyValue result = AuthenticationFilterUtils.getTokenKeyValueFromHeader(request);

        // Then
        assertNull(result);
    }

    /**
     * Test null authorization header
     */
    @Test
    void testGetOauthTokenKeyValueWithNullAuthorization() {
        // Given
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        // When
        TokenKeyValue result = AuthenticationFilterUtils.getTokenKeyValueFromHeader(request);

        // Then
        assertNull(result);
    }

    /**
     * Test configuration-dependent behavior with oldCheckForOathTokenLength setting
     */
    @ParameterizedTest
    @MethodSource("configurationDependentProvider")
    void testGetOauthTokenKeyValueDependOnConfiguration(boolean oldCheckEnabled, String authorizationHeader,
            boolean shouldReturnNull) {
        // Given
        boolean originalValue = ConstantValues.oldCheckForOathTokenLength.getBoolean();
        try {
            homeStub.setProperty(ConstantValues.oldCheckForOathTokenLength, String.valueOf(oldCheckEnabled));
            when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorizationHeader);

            // When
            TokenKeyValue result = AuthenticationFilterUtils.getTokenKeyValueFromHeader(request);

            // Then
            if (shouldReturnNull) {
                assertNull(result);
            } else {
                assertNotNull(result);
                assertEquals(OauthManager.OAUTH_KEY, result.getKey());
                assertEquals(authorizationHeader.substring(OauthManager.OAUTH_TOKEN_PREFIX.length()),
                        result.getToken());
            }
        } finally {
            // Restore original value
            homeStub.setProperty(ConstantValues.oldCheckForOathTokenLength, String.valueOf(originalValue));
        }
    }

    /**
     * Test edge cases and special scenarios
     */
    @ParameterizedTest
    @MethodSource("edgeCaseProvider")
    void testGetOauthTokenKeyValueWithEdgeCases(String authorizationHeader, String expectedToken,
            boolean shouldReturnNull) {
        // Given
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorizationHeader);

        // When
        TokenKeyValue result = AuthenticationFilterUtils.getTokenKeyValueFromHeader(request);

        // Then
        if (shouldReturnNull) {
            assertNull(result);
        } else {
            assertNotNull(result);
            assertEquals(OauthManager.OAUTH_KEY, result.getKey());
            assertEquals(expectedToken, result.getToken());
        }
    }

    /**
     * Provider for valid Bearer tokens
     */
    private static Stream<Arguments> validBearerTokenProvider() {
        return Stream.of(
            Arguments.of("Bearer Tz", "Tz"),
            Arguments.of("Bearer valid-jwt-token-123", "valid-jwt-token-123"),
            Arguments.of("Bearer 123", "123"),
            Arguments.of("Bearer abc-def-ghi", "abc-def-ghi"),
            Arguments.of("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"),
            Arguments.of("Bearer " + "a".repeat(1000), "a".repeat(1000)), // Long token
            Arguments.of("Bearer token-with-special-chars!@#$%^&*()_+-=[]{}|;':\",./<>?",
                    "token-with-special-chars!@#$%^&*()_+-=[]{}|;':\",./<>?")
        );
    }

    /**
     * Provider for configuration-dependent test cases
     */
    private static Stream<Arguments> configurationDependentProvider() {
        return Stream.of(
            Arguments.of(true, "Bearer T", true), // oldCheck enabled, should return null
            Arguments.of(false, "Bearer T", false), // oldCheck disabled, should return token
            Arguments.of(true, "Bearer TT", false), // oldCheck enabled, longer token should work
            Arguments.of(false, "Bearer TT", false) // oldCheck disabled, longer token should work
        );
    }

    /**
     * Provider for edge cases
     */
    private static Stream<Arguments> edgeCaseProvider() {
        return Stream.of(
            Arguments.of("Bearer A", "A", true), // Minimum valid length
            Arguments.of("Bearer ", null, true), // Empty token after Bearer
            Arguments.of("Bearer\t", null, true), // Tab instead of space
            Arguments.of("Bearer\n", null, true), // Newline instead of space
            Arguments.of("Bearer", null, true), // No space after Bearer
            Arguments.of("", null, true), // Empty string
            Arguments.of("Beerer TT", null, true) // Misspelled Bearer
        );
    }
}
