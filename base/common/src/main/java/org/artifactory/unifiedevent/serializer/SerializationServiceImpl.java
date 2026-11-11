package org.artifactory.unifiedevent.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.unifiedevent.model.EventEnvelope;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class SerializationServiceImpl implements SerializationService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public <T> String toJsonString(EventEnvelope<T> unifiedEvent) {
        try {
            return objectMapper.writeValueAsString(unifiedEvent);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize EventEnvelope to JSON string", e);
            throw new UnifiedEventException("Failed to serialize event: " + e.getMessage());
        }
    }

    @Override
    public <T> byte[] toBytes(EventEnvelope<T> eventEnvelope) {
        try {
            return objectMapper.writeValueAsString(eventEnvelope).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize EventEnvelope to bytes", e);
            throw new UnifiedEventException("Failed to serialize event: " + e.getMessage());
        }
    }
}

