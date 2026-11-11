package org.artifactory.rest.servlet;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.MediaType;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JerseyFixMemoryConsumptionHooksTest {
    @Test
    void fillMediaTypesHook_shouldReplaceWorkerMediaTypesWithWildcard() throws Exception {
        Set<MediaType> mediaTypes = new HashSet<>();

        boolean result = (boolean) JerseyFixMemoryConsumptionHooks.fillMediaTypes(
                mediaTypes,
                () -> {
                    mediaTypes.add(MediaType.APPLICATION_JSON_TYPE);
                    mediaTypes.add(MediaType.APPLICATION_XML_TYPE);
                    return true;
                });
        assertTrue(result, "The fillMediaTypes() hook should propagate the original return value");
        assertEquals(mediaTypes, Set.of(MediaType.WILDCARD_TYPE),
                "The fillMediaTypes() hook supposed to replace multiple (worker) media types with a wildcard");
    }

    @Test
    void fillMediaTypesHook_shouldKeepSpecificMediaTypes() throws Exception {
        Set<MediaType> mediaTypes = new HashSet<>();

        boolean result = (boolean) JerseyFixMemoryConsumptionHooks.fillMediaTypes(
                mediaTypes,
                () -> {
                    mediaTypes.add(MediaType.APPLICATION_JSON_TYPE);
                    mediaTypes.add(MediaType.APPLICATION_XML_TYPE);
                    return false;
                });
        assertFalse(result, "The fillMediaTypes() hook should propagate the original return value");
        assertEquals(mediaTypes, Set.of(MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_XML_TYPE),
                "The fillMediaTypes() hook supposed keep MIME types if they come from annotations");
    }
}