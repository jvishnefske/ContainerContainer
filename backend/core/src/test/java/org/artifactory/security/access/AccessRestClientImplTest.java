package org.artifactory.security.access;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.artifactory.api.security.access.AccessRestRequest;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author Eli Fishman
 */
@ExtendWith(MockitoExtension.class)
class AccessRestClientImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private CloseableHttpClient httpClient;
    @Mock
    private CloseableHttpResponse httpResponse;
    @Mock
    private StatusLine statusLine;
    @Mock
    private HttpEntity httpEntity;
    @Mock
    private JsonUtils jsonUtils;
    @Mock
    private AccessRestRequest request;

    private AccessRestClientImpl client;

    @BeforeEach
    void setUp() {
        client = new AccessRestClientImpl();
    }

    @AfterEach
    void tearDown() {
        client.preDestroy();
    }

    @Test
    void testExecutePostRequestSuccess() throws Exception {
        String requestPath = "/api/test";
        String auth = "Bearer token";
        String responseJson = "{\"result\":\"ok\"}";
        class DummyResponse {
            public String result;
        }
        DummyResponse dummy = new DummyResponse();
        dummy.result = "ok";

        withStaticMocks((httpClientsMock, jsonUtilsMock, entityUtilsMock) -> {
            prepareHttpClient(httpClientsMock);
            setupCommonMocks();
            when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
            entityUtilsMock.when(() -> EntityUtils.toString(httpEntity)).thenReturn(responseJson);
            jsonUtilsMock.when(JsonUtils::getInstance).thenReturn(jsonUtils);
            when(jsonUtils.readValue(eq(responseJson), eq(DummyResponse.class))).thenReturn(dummy);

            client.postConstruct();
            DummyResponse result = client.executePostRequest(request, requestPath, auth, DummyResponse.class);
            assertEquals("ok", result.result);
        });
    }

    @Test
    void testExecutePostRequestErrorResponse() throws Exception {
        String requestPath = "/api/test";
        String auth = "Bearer token";
        String errorJson = "{\"errors\":[{\"code\":\"401\",\"message\":\"Unauthorized\"}]}";
        AccessRestClientImpl.ErrorResponse errorResponse = new AccessRestClientImpl.ErrorResponse();
        AccessRestClientImpl.ErrorDetail detail = new AccessRestClientImpl.ErrorDetail();
        detail.setCode("401");
        detail.setMessage("Unauthorized");
        errorResponse.setErrors(java.util.Collections.singletonList(detail));

        withStaticMocks((httpClientsMock, jsonUtilsMock, entityUtilsMock) -> {
            prepareHttpClient(httpClientsMock);
            setupCommonMocks();
            when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_UNAUTHORIZED);
            entityUtilsMock.when(() -> EntityUtils.toString(httpEntity)).thenReturn(errorJson);
            jsonUtilsMock.when(JsonUtils::getInstance).thenReturn(jsonUtils);
            when(jsonUtils.readValue(eq(errorJson), eq(AccessRestClientImpl.ErrorResponse.class)))
                    .thenReturn(errorResponse);

            client.postConstruct();
            assertThrows(IllegalArgumentException.class,
                    () -> client.executePostRequest(request, requestPath, auth, Object.class));
        });
    }

    @Test
    void testExecutePostRequestIOException() throws Exception {
        String requestPath = "/api/test";
        String auth = "Bearer token";

        withStaticMocks((httpClientsMock, jsonUtilsMock, entityUtilsMock) -> {
            prepareHttpClient(httpClientsMock);
            when(httpClient.execute(any())).thenThrow(new IOException("IO error"));
            jsonUtilsMock.when(JsonUtils::getInstance).thenReturn(jsonUtils);
            when(jsonUtils.valueToString(any(), anyBoolean())).thenReturn("{}");

            client.postConstruct();
            assertThrows(IllegalArgumentException.class,
                    () -> client.executePostRequest(request, requestPath, auth, Object.class));
        });
    }

    private interface TestBlock {
        void run(MockedStatic<HttpClients> httpClientsMock,
                MockedStatic<JsonUtils> jsonUtilsMock,
                MockedStatic<EntityUtils> entityUtilsMock) throws Exception;
    }

    private void withStaticMocks(TestBlock block) throws Exception {
        try (
                MockedStatic<HttpClients> httpClientsMock = mockStatic(HttpClients.class);
                MockedStatic<JsonUtils> jsonUtilsMock = mockStatic(JsonUtils.class);
                MockedStatic<EntityUtils> entityUtilsMock = mockStatic(EntityUtils.class)
        ) {
            block.run(httpClientsMock, jsonUtilsMock, entityUtilsMock);
        }
    }

    private void setupCommonMocks() throws IOException {
        when(httpClient.execute(any())).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        when(jsonUtils.valueToString(any(), anyBoolean())).thenReturn("{}");
        when(JsonUtils.getInstance()).thenReturn(jsonUtils);
    }

    private void prepareHttpClient(MockedStatic<HttpClients> httpClientsMock) {
        HttpClientBuilder httpClientBuilder = Mockito.mock(HttpClientBuilder.class);
        when(httpClientBuilder.build()).thenReturn(httpClient);
        when(httpClientBuilder.setDefaultRequestConfig(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setDefaultSocketConfig(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setConnectionTimeToLive(anyLong(), any())).thenReturn(httpClientBuilder);
        httpClientsMock.when(HttpClients::custom).thenReturn(httpClientBuilder);
    }
}
