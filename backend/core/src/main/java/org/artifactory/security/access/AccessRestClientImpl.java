package org.artifactory.security.access;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.core.MediaType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.artifactory.api.security.access.AccessRestRequest;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.common.JsonUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.Optional.ofNullable;
import static org.artifactory.common.ConstantValues.accessClientServerUrlOverride;
import static org.artifactory.util.BasicHttpUtils.isSuccessfulResponseCode;

/**
 * @author Eli Fishman
 */
@Service
@Slf4j
public class AccessRestClientImpl implements AccessRestClient {

    private CloseableHttpClient httpClient;

    @Override
    public <T> T executePostRequest(AccessRestRequest accessRestRequest,
            String requestPath,
            String authorizationValue,
            Class<T> responseType) {
        HttpPost post = createHttpPost(accessRestRequest, requestPath, authorizationValue);
        try (CloseableHttpResponse httpResponse = httpClient.execute(post)) {
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            String entity = EntityUtils.toString(httpResponse.getEntity());

            if (!isSuccessfulResponseCode(statusCode)) {
                log.warn("Failed to proxy POST request to Access: {}. Response: {}", statusCode, entity);
                String errorMessage = JsonUtils.getInstance().readValue(entity, ErrorResponse.class).getFirstMessage();
                throw new IllegalArgumentException("Failed to proxy POST request to Access. Reason: " +
                        (StringUtils.isNotBlank(errorMessage) ? errorMessage : entity));
            }
            return JsonUtils.getInstance().readValue(entity, responseType);
        } catch (IOException e) {
            log.warn("Failed to proxy POST request", e);
            throw new IllegalArgumentException("Failed to proxy POST request to Access", e);
        }
    }

    private HttpPost createHttpPost(AccessRestRequest accessRestRequest, String requestPath,
            String authorizationValue) {
        String completeUrl = getAccessUrl() + requestPath;
        HttpPost post = new HttpPost(completeUrl);
        post.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        post.setHeader(HttpHeaders.AUTHORIZATION, authorizationValue);

        String requestBody = JsonUtils.getInstance().valueToString(accessRestRequest, true);
        log.debug("POST request to {} with body: {}", completeUrl, requestBody);
        HttpEntity requestEntity = new StringEntity(requestBody, ContentType.APPLICATION_JSON);
        post.setEntity(requestEntity);
        return post;
    }

    private String getAccessUrl() {
        String accessUrl = accessClientServerUrlOverride.getString(ArtifactoryHome.get());
        if (StringUtils.isBlank(accessUrl)) {
            String routerUrl = ArtifactoryHome.get().getSysConfig().helper().getRouterUrl();
            accessUrl = routerUrl + "/access";
        }
        return accessUrl;
    }

    @Data
    protected static class ErrorDetail {
        private String code;
        private String message;
    }

    @Data
    protected static class ErrorResponse {
        private List<ErrorDetail> errors;

        public String getFirstMessage() {
            if (errors != null && !errors.isEmpty()) {
                return errors.getFirst().getMessage();
            }
            return null;
        }
    }

    @PostConstruct
    public void postConstruct() {
        int timeoutValue = Integer.parseInt(
                ofNullable(System.getenv("artifactory.access.rest.client.connection.timeout.in.milliseconds"))
                        .orElse("120000"));
        if (httpClient == null) {
            httpClient = HttpClients.custom()
                    .setConnectionTimeToLive(timeoutValue, TimeUnit.MILLISECONDS)
                    .setDefaultSocketConfig(
                            SocketConfig.custom()
                                    .setSoTimeout(timeoutValue)
                                    .build())
                    .setDefaultRequestConfig(
                            RequestConfig.custom()
                                    .setConnectTimeout(timeoutValue)
                                    .setSocketTimeout(timeoutValue)
                                    .setConnectionRequestTimeout(timeoutValue)
                                    .build())
                    .build();
        }
    }

    @PreDestroy
    public void preDestroy() {
        if (httpClient != null) {
            IOUtils.closeQuietly(httpClient);
        }
    }
}
