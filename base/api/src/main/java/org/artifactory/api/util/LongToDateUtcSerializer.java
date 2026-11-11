package org.artifactory.api.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Serializes Long timestamps to ISO 8601 date strings in UTC timezone.
 * Output format: "2026-01-05T07:35:19.595Z"
 *
 * @author Amir Avidan
 */
public class LongToDateUtcSerializer extends JsonSerializer<Long> {
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendInstant(3)
            .toFormatter();

    public static String formatIsoDate(long epochMillis) {
        return FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(formatIsoDate(value));
    }
}

