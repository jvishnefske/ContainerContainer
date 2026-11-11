package org.artifactory.storage.db.devopsagent.requestlog.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque keyset-pagination cursor. Encodes the {@code (request_time, id)} of the last row of
 * the previous page so the next query can resume with
 * {@code WHERE request_time < :t OR (request_time = :t AND id < :i)}.
 * <p>
 * Encoded as base64 of the JSON {@code {"t":<epochMs>,"i":<id>}} — stable across server restarts
 * because both fields are persisted with the row.
 */
public record Cursor(long requestTime, long id) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String encode() {
        try {
            byte[] jsonBytes = MAPPER.writeValueAsBytes(new EncodedCursor(requestTime, id));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(jsonBytes);
        } catch (Exception e) {
            // mapper writes two primitives — should not throw
            throw new IllegalStateException("Failed to encode cursor", e);
        }
    }

    public static Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("'cursor' is malformed");
        }
        try {
            byte[] jsonBytes = Base64.getUrlDecoder().decode(encoded.getBytes(StandardCharsets.US_ASCII));
            EncodedCursor parsed = MAPPER.readValue(jsonBytes, EncodedCursor.class);
            if (parsed == null) {
                throw new IllegalArgumentException("'cursor' is malformed");
            }
            return new Cursor(parsed.t(), parsed.i());
        } catch (Exception e) {
            throw new IllegalArgumentException("'cursor' is malformed");
        }
    }

    private record EncodedCursor(long t, long i) {
    }
}
