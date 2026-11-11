package org.artifactory.unifiedevent.dispatchers.outbound;

import org.apache.http.ProtocolVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.registry.Consumer;
import org.artifactory.unifiedevent.registry.TokenHandler;
import org.artifactory.unifiedevent.serializer.SerializationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class HttpOutboundTest extends ArtifactoryHomeBoundJUnitTest {
    private TokenHandler tokenHandler;
    private HttpOutbound httpOutbound;
    SerializationService serializationService = mock(SerializationService.class);

    @BeforeEach
    void setUp() {
        AccessService accessService = mock(AccessService.class);
        tokenHandler = mock(TokenHandler.class);
        httpOutbound = new HttpOutbound(accessService, tokenHandler, serializationService);
    }

    @ParameterizedTest
    @MethodSource("executeRequestSource")
    void testExecuteRequest(DummyResponse response, boolean shouldThrow, Exception exception) throws IOException {
        HttpOutbound spyOutbound = spy(httpOutbound);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        doReturn(mockClient).when(spyOutbound).createHttpClient();
        when(mockClient.execute(any(HttpPost.class))).thenReturn(response);
        if (shouldThrow) {
            if (response == null) {
                doThrow(exception).when(mockClient).execute(any(HttpPost.class));
            }
            assertThrows(UnifiedEventException.class, () -> spyOutbound.executeRequest(mock(HttpPost.class)));
            return;
        }
        DispatcherResponse resultResponse = spyOutbound.executeRequest(mock(HttpPost.class));
        Assertions.assertTrue(resultResponse.shouldProceed());
    }

    private static Stream<Arguments> executeRequestSource() {
        return Stream.of(
                Arguments.of(
                        new DummyResponse(200, "OK", "{\"data\":\"test\"}"),
                        false,
                        null),
                Arguments.of(
                        new DummyResponse(400, "Bad Request", "{\"error\":\"Invalid request\"}"),
                        true,
                        null),
                Arguments.of(
                        null,
                        true,
                        new IOException("Network error")),
                Arguments.of(
                        null,
                        true,
                        new ClientProtocolException("Client protocol error"))

        );
    }

    @Test
    void testDispatchEvent() {
        Consumer consumer = mock(Consumer.class);
        when(consumer.endpoint()).thenReturn("http://test/endpoint");
        when(tokenHandler.getToken(consumer)).thenReturn("token123");
        EventEnvelope payload = mock(EventEnvelope.class);
        when(serializationService.toJsonString(payload)).thenReturn("{\"data\":\"test\"}");
        DispatcherResponse expectedResponse = mock(DispatcherResponse.class);
        HttpOutbound spyOutbound = spy(httpOutbound);
        doReturn(expectedResponse).when(spyOutbound).executeRequest(any(HttpPost.class));
        DispatcherResponse response = spyOutbound.dispatchEvent(consumer, payload);
        assertEquals(response, expectedResponse);
    }

    private static class DummyResponse extends BasicHttpResponse implements CloseableHttpResponse {

        public DummyResponse(int statusCode, String reasonPhrase, String entity) {
            super(new BasicStatusLine(new ProtocolVersion("a", 1, 1), statusCode, reasonPhrase));
            super.setEntity(EntityBuilder.create().setText(entity).build());
        }

        @Override
        public void close() {
            // Not Required
        }
    }

}
