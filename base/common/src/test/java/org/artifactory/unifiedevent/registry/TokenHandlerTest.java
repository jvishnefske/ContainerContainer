package org.artifactory.unifiedevent.registry;

import org.artifactory.security.access.AccessService;
import org.artifactory.unifiedevent.constants.SupportedConsumers;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.token.TokenClient;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.token.TokenResponse;
import org.jfrog.access.common.ServiceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class TokenHandlerTest {
    private AccessService accessService;
    private TokenHandler tokenHandler;
    private Consumer consumer;

    @BeforeEach
    void setUp() {
        accessService = mock(AccessService.class);
        consumer = mock(Consumer.class);
        when(consumer.name()).thenReturn(SupportedConsumers.WORKER_CLIENT);
        tokenHandler = spy(new TokenHandler(accessService));
    }

    @Test
    void testIsExpired() {
        assertTrue(tokenHandler.isExpired("test-token"));
    }

    @ParameterizedTest
    @MethodSource("getTokenSource")
    void testGetToken(String expectedToken) {
        doReturn(expectedToken).when(tokenHandler).generateToken(consumer);
        String token = tokenHandler.getToken(consumer);
        assertEquals(expectedToken, token);
    }

    private static Stream<Arguments> getTokenSource() {
        return Stream.of(
                Arguments.of("NewToken")
        );
    }

    @ParameterizedTest
    @MethodSource("generateTokenSource")
    void testGetToken_GeneratesAndCachesToken(AccessClient accessClient, String token) {
        ServiceId serviceId = mock(ServiceId.class);
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
        when(serviceId.getFormattedName()).thenReturn("artifactory-service");
        when(accessService.getAccessClient()).thenReturn(accessClient);
        if (accessClient == null) {
            assertThrows(AccessClientException.class, () -> tokenHandler.generateToken(consumer));
            return;
        }
        TokenClient tokenClient = mock(TokenClient.class);
        when(accessClient.token()).thenReturn(tokenClient);
        TokenResponse tokenResponse = mock(TokenResponse.class);
        when(tokenClient.create(any(TokenRequest.class))).thenReturn(tokenResponse);
        when(tokenResponse.getTokenValue()).thenReturn(token);
        assertEquals(token, tokenHandler.generateToken(consumer));
    }

    private static Stream<Arguments> generateTokenSource() {
        return Stream.of(
                Arguments.of(mock(AccessClient.class), "Token123"),
                Arguments.of(null, "Token123")
        );
    }
}
