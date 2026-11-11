package org.artifactory.sensitive;

import org.artifactory.repo.remote.CustomHttpHeader;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link CustomHttpHeadersSensitiveHandler} behavior that does not require crypto setup: {@code mask()},
 * {@code sensitiveLeafPaths()}, and {@code validate()}. Encryption, decryption, and key-rotation paths depend on
 * {@code ArtifactoryHome} and are covered by the sensitive-field service/interceptor tests.
 */
class CustomHttpHeadersSensitiveHandlerTest {

    private static final String D = "\u00BB";
    private final CustomHttpHeadersSensitiveHandler handler = new CustomHttpHeadersSensitiveHandler();

    @Test
    void maskReplacesSensitiveValueAndKeepsNonSensitiveVisible() {
        List<CustomHttpHeader> masked = handler.mask(List.of(
                new CustomHttpHeader("Authorization", "secret", true),
                new CustomHttpHeader("X-Trace", "plain", false)));

        assertEquals("authorization", masked.get(0).getName());
        assertEquals("***", masked.get(0).getValue());
        assertEquals("plain", masked.get(1).getValue());
    }

    @Test
    void maskMutatesOriginalHeadersLikeExistingHandler() {
        CustomHttpHeader original = new CustomHttpHeader("Authorization", "secret", true);
        handler.mask(List.of(original));
        assertEquals("***", original.getValue());
    }

    @Test
    void maskLeavesSensitiveHeaderWithBlankValueUnchanged() {
        List<CustomHttpHeader> masked = handler.mask(List.of(new CustomHttpHeader("Authorization", "", true)));
        assertEquals("", masked.get(0).getValue());
    }

    @Test
    void maskHandlesNullListAndNullElements() {
        assertNull(handler.mask(null));

        List<CustomHttpHeader> masked = handler.mask(Arrays.asList((CustomHttpHeader) null));
        assertNull(masked.get(0));
    }

    @Test
    void sensitiveLeafPathsReportsOnlySensitiveHeaders() {
        Set<String> paths = handler.sensitiveLeafPaths(List.of(
                new CustomHttpHeader("Authorization", "secret", true),
                new CustomHttpHeader("X-Trace", "plain", false)), D);

        assertEquals(Set.of("authorization" + D + "value"), paths);
    }

    @Test
    void sensitiveLeafPathsReturnsEmptyForNull() {
        assertTrue(handler.sensitiveLeafPaths(null, D).isEmpty());
    }

    @Test
    void validateAcceptsNullAndAnyValue() {
        assertDoesNotThrow(() -> handler.validate(null));
        assertDoesNotThrow(() -> handler.validate(List.of(new CustomHttpHeader("a", "b", false))));
    }
}
