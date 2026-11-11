package org.artifactory.ui.rest.model.artifacts.search.syntax;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class QueryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserializationWithFolder() throws Exception {
        String json = """
                {
                    "text": "test",
                    "repo": "my-repo",
                    "folder": "some/path",
                    "sha": "abc123",
                    "autoComplete": true
                }
                """;

        Query query = objectMapper.readValue(json, Query.class);

        assertNotNull(query);
        assertEquals("test", query.text());
        assertEquals("my-repo", query.repo());
        assertEquals("some/path", query.folder());
        assertEquals("abc123", query.sha());
        assertTrue(query.autoComplete());
    }

    @Test
    void testDeserializationWithPath() throws Exception {
        String json = """
                {
                    "text": "test",
                    "repo": "my-repo",
                    "path": "some/path",
                    "sha": "abc123",
                    "autoComplete": true
                }
                """;

        Query query = objectMapper.readValue(json, Query.class);

        assertNotNull(query);
        assertEquals("test", query.text());
        assertEquals("my-repo", query.repo());
        assertEquals("some/path", query.folder());
        assertEquals("abc123", query.sha());
        assertTrue(query.autoComplete());
    }

    @Test
    void testSerializationUsesFolder() throws Exception {
        Query query = new Query("test", "my-repo", "some/path", "abc123", true);

        String json = objectMapper.writeValueAsString(query);

        assertNotNull(json);
        assertTrue(json.contains("\"folder\":\"some/path\""));
        assertFalse(json.contains("\"path\":"));
    }

    @Test
    void testDeserializationWithBothPathAndFolder() throws Exception {
        String json = """
                {
                    "text": "test",
                    "repo": "my-repo",
                    "path": "old/path",
                    "folder": "new/path",
                    "sha": "abc123",
                    "autoComplete": true
                }
                """;

        Query query = objectMapper.readValue(json, Query.class);

        assertNotNull(query);
        assertEquals("new/path", query.folder());
    }
}
