package org.artifactory.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Mor Merhav
 */
class BasePackageTypeConfigMapperTest {

    @Test
    void testCommaDelimitedStringToList() {
        Map<String, String> flatMap = Map.of("list", "[\"bar\"]", "second_list", "[\"baz\"]");
        List<String> firstListResult = PackageTypeConfigMapper.INSTANCE.jsonStringToList("list", flatMap);

        assertNotNull(firstListResult);
        assertEquals(1, firstListResult.size());
        assertEquals("bar", firstListResult.get(0));

        List<String> secondListResult =
                PackageTypeConfigMapper.INSTANCE.jsonStringToList("second_list", flatMap);

        assertNotNull(secondListResult);
        assertEquals(1, secondListResult.size());
        assertEquals("baz", secondListResult.get(0));

        // Empty list
        flatMap = Map.of("a_null_list", "");
        List<String> nullListResult =
                PackageTypeConfigMapper.INSTANCE.jsonStringToList("a_null_list", flatMap);

        assertNull(nullListResult);

        flatMap = Map.of("an_empty_list", "[]");
        List<String> emptyListResult = PackageTypeConfigMapper.INSTANCE.jsonStringToList("an_empty_list", flatMap);

        assertNotNull(emptyListResult);
        assertTrue(emptyListResult.isEmpty());

        flatMap = Map.of("list", "[\"foo\"]");
        List<String> wrongKeyResult =
                PackageTypeConfigMapper.INSTANCE.jsonStringToList("does_not_exist", flatMap);

        assertNull(wrongKeyResult);
    }

    interface PackageTypeConfigMapper extends BasePackageTypeConfigMapper<Object> {
        NpmPackageTypeConfigMapper INSTANCE = packageTypeConfig -> null;
    }
}