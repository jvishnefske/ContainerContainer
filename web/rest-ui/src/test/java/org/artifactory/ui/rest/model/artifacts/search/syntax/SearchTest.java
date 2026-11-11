package org.artifactory.ui.rest.model.artifacts.search.syntax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserialization_shouldUseDefaultValue_whenRealValueMissed() throws Exception {
        Search search = objectMapper.readValue("{}", Search.class);

        assertNotNull(search);
        assertNull(search.query());
        assertEquals(0, search.limit());
        assertNull(search.state());
        assertNull(search.filters());
        assertFalse(search.isExactMatch());
    }
}
