package org.artifactory.shadow;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.ArrayList;

/**
 * Holds request data captured synchronously from ContainerRequestContext before async shadow request execution.
 * This avoids accessing the original request after Tomcat has recycled the request object.
 */
public record ShadowRequestContext(
        String repoType,
        String originalTraceId,
        String requestUri,
        String endpoint,
        String requestMethod,
        MultivaluedMap<String, String> headers
) {
    /**
     * Creates a ShadowRequestContext with a deep copy of the headers to avoid any reference to the original request.
     *
     * @param repoType the repository type
     * @param originalTraceId the original trace ID for correlation
     * @param requestUri the request URI
     * @param endpoint the endpoint name
     * @param requestMethod the HTTP method
     * @param originalHeaders the original request headers (will be deep copied)
     * @return a new ShadowRequestContext with copied data
     */
    public static ShadowRequestContext of(String repoType, String originalTraceId, String requestUri,
            String endpoint, String requestMethod, MultivaluedMap<String, String> originalHeaders) {
        return new ShadowRequestContext(repoType, originalTraceId, requestUri, endpoint, requestMethod,
                copyHeaders(originalHeaders));
    }

    private static MultivaluedMap<String, String> copyHeaders(MultivaluedMap<String, String> original) {
        MultivaluedMap<String, String> copy = new MultivaluedHashMap<>();
        if (original != null) {
            original.forEach((key, values) -> copy.put(key, new ArrayList<>(values)));
        }
        return copy;
    }
}
