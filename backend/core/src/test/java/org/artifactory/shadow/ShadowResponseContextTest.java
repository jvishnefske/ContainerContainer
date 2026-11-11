package org.artifactory.shadow;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowResponseContextTest {

    @Test
    void of_shouldDeepCopyHeaders_whenHeadersProvided() {
        // Arrange
        MultivaluedMap<String, Object> originalHeaders = new MultivaluedHashMap<>();
        originalHeaders.add("Content-Type", "application/json");
        originalHeaders.add("X-Custom-Header", "value1");
        originalHeaders.add("X-Custom-Header", "value2");

        // Act
        ShadowResponseContext context = ShadowResponseContext.of(200, originalHeaders);

        // Assert
        assertEquals(200, context.status());
        assertNotNull(context.headers());
        assertNotSame(originalHeaders, context.headers());
        assertEquals(2, context.headers().size());
        assertEquals(List.of("application/json"), context.headers().get("Content-Type"));
        assertEquals(List.of("value1", "value2"), context.headers().get("X-Custom-Header"));
    }

    @Test
    void of_shouldReturnEmptyHeaders_whenHeadersAreNull() {
        // Act
        ShadowResponseContext context = ShadowResponseContext.of(404, null);

        // Assert
        assertEquals(404, context.status());
        assertNotNull(context.headers());
        assertTrue(context.headers().isEmpty());
    }

    @Test
    void of_shouldCreateIndependentCopy_whenOriginalHeadersModifiedAfterCreation() {
        // Arrange
        MultivaluedMap<String, Object> originalHeaders = new MultivaluedHashMap<>();
        originalHeaders.add("Header-Name", "original-value");

        // Act
        ShadowResponseContext context = ShadowResponseContext.of(200, originalHeaders);

        // Modify original headers after context creation
        originalHeaders.put("Header-Name", List.of("modified-value"));
        originalHeaders.add("New-Header", "new-value");

        // Assert - context should retain original values
        assertEquals(List.of("original-value"), context.headers().get("Header-Name"));
        assertTrue(context.headers().get("New-Header") == null || context.headers().get("New-Header").isEmpty());
    }

    @Test
    void of_shouldHandleEmptyHeaders() {
        // Arrange
        MultivaluedMap<String, Object> emptyHeaders = new MultivaluedHashMap<>();

        // Act
        ShadowResponseContext context = ShadowResponseContext.of(204, emptyHeaders);

        // Assert
        assertEquals(204, context.status());
        assertNotNull(context.headers());
        assertTrue(context.headers().isEmpty());
    }
}
