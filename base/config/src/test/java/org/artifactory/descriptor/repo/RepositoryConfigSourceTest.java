package org.artifactory.descriptor.repo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Tamar Rosen
 */
class RepositoryConfigSourceTest {
    @Test
    void testBuildListOfStringsFromFlatMap() {
        Map<String, String> flatMap = Map.of("list", "[\"bar\"]", "second_list", "[\"baz\"]");
        List<String> firstListResult = RepositoryConfigSource.jsonStringToList(flatMap, "list");

        assertNotNull(firstListResult);
        assertEquals(1, firstListResult.size());
        assertEquals("bar", firstListResult.get(0));

        List<String> secondListResult = RepositoryConfigSource.jsonStringToList(flatMap, "second_list");

        assertNotNull(secondListResult);
        assertEquals(1, secondListResult.size());
        assertEquals("baz", secondListResult.get(0));

        // Null list
        flatMap = Map.of("a_null_list", "");
        List<String> nullListResult =
                RepositoryConfigSource.jsonStringToList(flatMap, "a_null_list");

        assertNull(nullListResult);

        // Empty list
        flatMap = Map.of("an_empty_list", "[]");
        List<String> emptyListResult =
                RepositoryConfigSource.jsonStringToList(flatMap, "an_empty_list");

        assertNotNull(emptyListResult);
        assertTrue(emptyListResult.isEmpty());

        flatMap = Map.of("list", "[\"foo\"]");
        List<String> wrongKeyResult =
                RepositoryConfigSource.jsonStringToList(flatMap, "does_not_exist");

        assertNull(wrongKeyResult);
    }
}