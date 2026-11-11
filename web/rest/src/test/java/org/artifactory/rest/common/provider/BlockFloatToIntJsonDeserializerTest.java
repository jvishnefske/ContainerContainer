package org.artifactory.rest.common.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.artifactory.rest.resource.cleanup.dto.PackageCleanupPolicySearchCriteriaDto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BlockFloatToIntJsonDeserializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        Class<PackageCleanupPolicySearchCriteriaDto> type;
        module.addDeserializer(Object.class, new BlockFloatToIntJsonDeserializer());
        objectMapper.registerModule(module);
    }

    @Test
    public void shouldAcceptInt() throws JsonProcessingException {
        Object value = objectMapper.readValue("1", Object.class);
        Assertions.assertEquals(1, value);
    }

    @Test
    public void shouldFailOnNonInt() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> objectMapper.readValue("1.2", Object.class));
    }
}
