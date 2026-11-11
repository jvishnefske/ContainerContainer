package org.artifactory.unifiedevent.dispatchers.outbound;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.security.access.AccessService;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.registry.Consumer;
import org.artifactory.unifiedevent.registry.TokenHandler;
import org.artifactory.unifiedevent.serializer.SerializationService;
import org.artifactory.util.HttpClientConfigurator;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_OK;

@AllArgsConstructor
@Component
@Slf4j
public class HttpOutbound implements OutboundDispatcher {

    protected AccessService accessService;
    protected TokenHandler tokenHandler;
    protected SerializationService serializationService;

    @Override
    public <T> DispatcherResponse dispatchEvent(Consumer consumer, EventEnvelope<T> payload) {
        String payloadString = serializationService.toJsonString(payload);
        HttpPost httpPost = new HttpPost(consumer.endpoint());
        //httpPost.setHeader(AUTHORIZATION, "Bearer " + tokenHandler.getToken(consumer));
        httpPost.setHeader(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        httpPost.setEntity(new StringEntity(payloadString, ContentType.APPLICATION_JSON));
        log.debug("Sending request to '{}'", consumer.endpoint());
        return executeRequest(httpPost);
    }

    public DispatcherResponse executeRequest(HttpPost httpPost) {
        try (CloseableHttpClient client = createHttpClient(); CloseableHttpResponse response = client.execute(
                httpPost)) {
            StatusLine statusLine = response.getStatusLine();
            String responseMessage = EntityUtils.toString(response.getEntity());
            if (statusLine.getStatusCode() != SC_OK) {
                throw new UnifiedEventException("Consumer responded with status code other than 200");
            }
            log.debug("Message from the Consumer: {}", responseMessage);
            return DispatcherResponse.builder().shouldProceed(true).build();
        } catch (Exception e) {
            throw new UnifiedEventException("Failed to call consumer: " + e.getMessage());
        }
    }

    CloseableHttpClient createHttpClient() {
        return new HttpClientConfigurator().socketTimeout(15000).connectionTimeout(1500).noRetry().build();
    }

}
