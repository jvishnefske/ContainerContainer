package org.artifactory.unifiedevent.serializer;

import org.artifactory.unifiedevent.model.EventEnvelope;

/**
 * Service interface for serializing unified event payloads.
 * Implementations are responsible for converting event objects to their string or byte representations.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * String serialized = serializationService.toString(unifiedEvent);
 * Byte responseBytes = serializationService.toByte(dispatcherResponse);
 * }
 * </pre>
 * </p>
 */
public interface SerializationService {

    /**
     * Serializes the given unified event payload into a string representation.
     *
     * @param unifiedEvent the payload to be serialized; must not be {@code null}
     * @return a string representation of the unified payload
     *
     * <p>
     * Example output:
     * <pre>
     * {
     *   "eventType": "BEFORE_DOWNLOAD",
     *   "payload": {
     *     "repoKey": "libs-release",
     *     "path": "org/example/artifact.jar"
     *   }
     * }
     * </pre>
     * </p>
     */
    <T> String toJsonString(EventEnvelope<T> unifiedEvent);

    /**
     * Serializes the given unified dispatcher response into a byte representation.
     *
     * @param eventEnvelope the response to be serialized; must not be {@code null}
     * @return a byte representation of the unified response
     *
     * <p>
     * Example output:
     * <pre>
     * 0x7B 0x22 0x73 0x74 0x61 0x74 0x75 0x73 0x22 0x3A 0x22 0x53 0x55 0x43 0x43 0x45 0x53 0x53 0x22 0x7D
     * // (JSON: {"status":"SUCCESS"} as bytes)
     * </pre>
     * </p>
     */
    <T> byte[] toBytes(EventEnvelope<T> eventEnvelope);

}
