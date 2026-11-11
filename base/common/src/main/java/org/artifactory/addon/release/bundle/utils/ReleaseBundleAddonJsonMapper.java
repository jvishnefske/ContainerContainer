package org.artifactory.addon.release.bundle.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Yevdo Abramov
 * Created on 22/10/2023
 */
@AllArgsConstructor
public class ReleaseBundleAddonJsonMapper {

    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    @NonNull
    public <T> T readValue(@NonNull File file, @NonNull Class<T> clazz) throws IOException {
        return mapper.readValue(file, clazz);
    }

    @NonNull
    public <T> T readValue(@NonNull InputStream stream, @NonNull Class<T> clazz) throws IOException {
        return mapper.readValue(stream, clazz);
    }

    @NonNull
    public <T> T readValue(byte @NonNull [] input, @NonNull Class<T> clazz) throws IOException {
        return mapper.readValue(input, clazz);
    }

    @NonNull
    public String writeAsJson(@NonNull Object value) throws JsonProcessingException {
        return writer.writeValueAsString(value);
    }
}

