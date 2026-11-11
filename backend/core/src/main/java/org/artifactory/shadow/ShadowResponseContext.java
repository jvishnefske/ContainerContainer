package org.artifactory.shadow;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.ArrayList;

/**
 * Holds response data captured synchronously from ContainerResponseContext before async shadow request execution.
 * This avoids accessing the original response after Tomcat has recycled the request object.
 *
 * @author Mor Merhav
 */
public record ShadowResponseContext(
        int status,
        MultivaluedMap<String, Object> headers
) {
    /**
     * Creates a ShadowResponseContext with a deep copy of the headers to avoid any reference to the original response.
     *
     * @param status the response status code
     * @param originalHeaders the original response headers (will be deep copied)
     * @return a new ShadowResponseContext with copied data
     */
    public static ShadowResponseContext of(int status, MultivaluedMap<String, Object> originalHeaders) {
        return new ShadowResponseContext(status, copyHeaders(originalHeaders));
    }

    private static MultivaluedMap<String, Object> copyHeaders(MultivaluedMap<String, Object> original) {
        MultivaluedMap<String, Object> copy = new MultivaluedHashMap<>();
        if (original != null) {
            original.forEach((key, values) -> copy.put(key, new ArrayList<>(values)));
        }
        return copy;
    }
}
