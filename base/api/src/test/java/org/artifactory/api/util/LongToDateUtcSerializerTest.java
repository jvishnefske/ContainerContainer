package org.artifactory.api.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Amir Avidan
 */
class LongToDateUtcSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
            "1735732800000, 2025-01-01T12:00:00.000Z",
            "1735732800123, 2025-01-01T12:00:00.123Z",
            "1736064919595, 2025-01-05T08:15:19.595Z",
            "0, 1970-01-01T00:00:00.000Z",
            "4102444799999, 2099-12-31T23:59:59.999Z"
    })
    void serializeShouldFormatTimestampAsUtcIsoString(long timestamp, String expectedDate) throws IOException {
        TestObject obj = new TestObject(timestamp);

        String json = objectMapper.writeValueAsString(obj);

        assertEquals("{\"timestamp\":\"" + expectedDate + "\"}", json);
    }

    @ParameterizedTest
    @CsvSource({
            "1735732800000, 2025-01-01T12:00:00.000Z",
            "0, 1970-01-01T00:00:00.000Z",
            "4102444799999, 2099-12-31T23:59:59.999Z"
    })
    void formatIsoDateShouldReturnIso8601String(long epochMillis, String expected) {
        assertEquals(expected, LongToDateUtcSerializer.formatIsoDate(epochMillis));
    }

    @Test
    void serializeShouldFormatCorrectlyWhenCalledDirectly() throws IOException {
        LongToDateUtcSerializer serializer = new LongToDateUtcSerializer();
        StringWriter writer = new StringWriter();
        JsonGenerator generator = objectMapper.getFactory().createGenerator(writer);
        SerializerProvider provider = objectMapper.getSerializerProvider();
        Long timestamp = 1736064919595L;

        serializer.serialize(timestamp, generator, provider);
        generator.flush();

        assertEquals("\"2025-01-05T08:15:19.595Z\"", writer.toString());
    }

    /**
     * Helper class for testing serialization
     */
    @Getter
    private static class TestObject {
        @JsonSerialize(using = LongToDateUtcSerializer.class)
        private final Long timestamp;

        TestObject(Long timestamp) {
            this.timestamp = timestamp;
        }
    }
}

