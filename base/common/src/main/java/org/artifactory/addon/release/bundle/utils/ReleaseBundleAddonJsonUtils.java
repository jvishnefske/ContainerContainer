package org.artifactory.addon.release.bundle.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.PropertyNamingStrategies.LOWER_CAMEL_CASE;

/**
 * @author Yevdo Abramov
 * Created on 22/10/2023
 */
public class ReleaseBundleAddonJsonUtils {

    private ReleaseBundleAddonJsonUtils() {
    }

    private static final ReleaseBundleAddonJsonMapper JSON_MAPPER_CAMEL_CASE = new ReleaseBundleAddonJsonMapper(
            new ObjectMapper(getJsonFactory()).configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .setPropertyNamingStrategy(LOWER_CAMEL_CASE),
            new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writerWithDefaultPrettyPrinter());


    @NonNull
    public static <T> T readCamelCase(@NonNull String json, @NonNull Class<T> clazz) throws IOException {
        InputStream stream = IOUtils.toInputStream(json, StandardCharsets.UTF_8);
        return JSON_MAPPER_CAMEL_CASE.readValue(stream, clazz);
    }

    @NonNull
    public static <T> T readCamelCase(@NonNull File file, @NonNull Class<T> clazz) throws IOException {
        return JSON_MAPPER_CAMEL_CASE.readValue(file, clazz);
    }

    @NonNull
    public static <T> T readCamelCase(@NonNull InputStream is, @NonNull Class<T> clazz) throws IOException {
        return JSON_MAPPER_CAMEL_CASE.readValue(is, clazz);
    }

    @NonNull
    public static String writeJsonCamelCase(@NonNull Object value) throws JsonProcessingException {
        return JSON_MAPPER_CAMEL_CASE.writeAsJson(value);
    }

    @NonNull
    private static JsonFactory getJsonFactory() {
        StreamReadConstraints streamReadConstraints = StreamReadConstraints.builder()
                .maxStringLength(Integer.MAX_VALUE)
                .build();
        return JsonFactory.builder().build().setStreamReadConstraints(streamReadConstraints);
    }
}