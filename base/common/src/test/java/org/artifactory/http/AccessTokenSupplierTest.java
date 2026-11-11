package org.artifactory.http;

import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientProvider;
import org.jfrog.access.client.token.TokenClient;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.token.TokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessTokenSupplierTest {

    private static final String AUDIENCE = "jfbus@*";
    private static final String SCOPE = "internal:event-stream:x";

    @Mock
    private AccessClientProvider accessClientProvider;

    @Mock
    private AccessClient accessClient;

    @Mock
    private TokenClient tokenClient;

    @Mock
    private TokenResponse tokenResponse;

    @Test
    void usesConfiguredTokenTtlInMillis() {
        long twoHoursMillis = TimeUnit.HOURS.toMillis(2);
        when(accessClientProvider.getAccessClient()).thenReturn(accessClient);
        when(accessClient.token()).thenReturn(tokenClient);
        when(tokenClient.create(any(TokenRequest.class))).thenReturn(tokenResponse);
        when(tokenResponse.getTokenValue()).thenReturn("token");

        AccessTokenSupplier supplier = new AccessTokenSupplier(
                accessClientProvider, twoHoursMillis, AUDIENCE, SCOPE);
        supplier.get();

        ArgumentCaptor<TokenRequest> captor = ArgumentCaptor.forClass(TokenRequest.class);
        verify(tokenClient).create(captor.capture());
        assertEquals(twoHoursMillis, captor.getValue().getExpiresIn());
    }

    @Test
    void bumpsTokenTtlBelowMinimumBufferToTwoHours() {
        when(accessClientProvider.getAccessClient()).thenReturn(accessClient);
        when(accessClient.token()).thenReturn(tokenClient);
        when(tokenClient.create(any(TokenRequest.class))).thenReturn(tokenResponse);
        when(tokenResponse.getTokenValue()).thenReturn("token");

        AccessTokenSupplier supplier = new AccessTokenSupplier(
                accessClientProvider, 7_200L, AUDIENCE, SCOPE);
        supplier.get();

        ArgumentCaptor<TokenRequest> captor = ArgumentCaptor.forClass(TokenRequest.class);
        verify(tokenClient).create(captor.capture());
        assertEquals(TimeUnit.HOURS.toMillis(2), captor.getValue().getExpiresIn());
    }
}
