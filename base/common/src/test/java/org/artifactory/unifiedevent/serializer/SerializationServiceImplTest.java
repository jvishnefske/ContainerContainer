package org.artifactory.unifiedevent.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.artifactory.unifiedevent.model.HeadersData;
import org.artifactory.unifiedevent.model.artifact.ArtifactEventData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerializationServiceImplTest {

    private final SerializationServiceImpl serializationService = new SerializationServiceImpl();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toJsonString_shouldReturnValidJson_whenGivenValidEventEnvelope() throws JsonProcessingException {
        // Arrange
        ArtifactEventData artifactData = ArtifactEventData.builder().build();
        EventEnvelope<ArtifactEventData> eventEnvelope = EventEnvelope.<ArtifactEventData>builder()
                .specVersion("1.0")
                .id("test-id-123")
                .type(SupportedUnifiedEvents.BEFORE_DOWNLOAD)
                .source("artifactory")
                .time("2025-01-01T10:00:00Z")
                .subject("test-repo/test/path")
                .dataSchema("beforeDownload")
                .dataContentType("application/json")
                .eventHeaders(HeadersData.builder().build())
                .data(artifactData)
                .build();

        String expectedJson = objectMapper.writeValueAsString(eventEnvelope);

        // Act
        String result = serializationService.toJsonString(eventEnvelope);

        // Assert
        assertEquals(expectedJson, result);
    }

    @Test
    void toJsonString_shouldThrowUnifiedEventException_whenSerializationFails() {
        // Arrange
        EventEnvelope<Object> eventEnvelope = EventEnvelope.builder()
                .specVersion("1.0")
                .id("test-id-123")
                .type(SupportedUnifiedEvents.BEFORE_DOWNLOAD)
                .source("artifactory")
                .time("2025-01-01T10:00:00Z")
                .subject("test-repo/test/path")
                .dataSchema("beforeDownload")
                .dataContentType("application/json")
                .eventHeaders(HeadersData.builder().build())
                .data(new Object() {
                    @SuppressWarnings("unused")
                    private final Object selfReference = this;
                })
                .build();

        // Act & Assert
        UnifiedEventException exception = assertThrows(UnifiedEventException.class,
                () -> serializationService.toJsonString(eventEnvelope));

        assertTrue(exception.getMessage().startsWith("Failed to serialize event: "),
                "Exception message should start with 'Failed to serialize event: '");
        assertTrue(exception.getMessage().contains("No serializer found for class"),
                "Exception message should contain 'No serializer found for class'");
        assertTrue(exception.getMessage().contains("SerializationServiceImplTest$1"),
                "Exception message should contain the anonymous class name");
    }

    @Test
    void toBytes_shouldReturnValidUtf8Bytes_whenGivenValidEventEnvelope() throws JsonProcessingException {
        // Arrange
        ArtifactEventData artifactData = ArtifactEventData.builder().build();
        EventEnvelope<ArtifactEventData> eventEnvelope = EventEnvelope.<ArtifactEventData>builder()
                .specVersion("1.0")
                .id("test-id-456")
                .type(SupportedUnifiedEvents.BEFORE_DOWNLOAD)
                .source("artifactory")
                .time("2025-01-01T10:00:00Z")
                .subject("test-repo/test/path")
                .dataSchema("beforeDownload")
                .dataContentType("application/json")
                .eventHeaders(HeadersData.builder().build())
                .data(artifactData)
                .build();

        byte[] expectedBytes = objectMapper.writeValueAsString(eventEnvelope).getBytes(StandardCharsets.UTF_8);

        // Act
        byte[] result = serializationService.toBytes(eventEnvelope);

        // Assert
        assertArrayEquals(expectedBytes, result);
        assertEquals(new String(expectedBytes, StandardCharsets.UTF_8),
                new String(result, StandardCharsets.UTF_8));
    }

    @Test
    void toBytes_shouldThrowUnifiedEventException_whenSerializationFails() {
        // Arrange
        EventEnvelope<Object> eventEnvelope = EventEnvelope.builder()
                .specVersion("1.0")
                .id("test-id-789")
                .type(SupportedUnifiedEvents.BEFORE_DOWNLOAD)
                .source("artifactory")
                .time("2025-01-01T10:00:00Z")
                .subject("test-repo/test/path")
                .dataSchema("beforeDownload")
                .dataContentType("application/json")
                .eventHeaders(HeadersData.builder().build())
                .data(new Object() {
                    @SuppressWarnings("unused")
                    private final Object selfReference = this;
                })
                .build();

        // Act & Assert
        UnifiedEventException exception = assertThrows(UnifiedEventException.class,
                () -> serializationService.toBytes(eventEnvelope));

        assertTrue(exception.getMessage().startsWith("Failed to serialize event: "),
                "Exception message should start with 'Failed to serialize event: '");
        assertTrue(exception.getMessage().contains("No serializer found for class"),
                "Exception message should contain 'No serializer found for class'");
    }
}

