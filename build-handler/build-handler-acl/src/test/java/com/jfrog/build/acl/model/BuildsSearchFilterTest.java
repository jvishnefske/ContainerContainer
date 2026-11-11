package com.jfrog.build.acl.model;

import org.junit.jupiter.api.Test;

import static com.jfrog.build.acl.model.BuildsSearchFilter.OrderByColumn;
import static com.jfrog.build.acl.model.BuildsSearchFilter.SortOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildsSearchFilterTest {

    @Test
    void orderByForValue_shouldDefaultToBuildDate_whenNull() {
        assertEquals("build_date", OrderByColumn.forValue(null));
    }

    @Test
    void orderByForValue_shouldDefaultToBuildDate_whenUnknown() {
        assertEquals("build_date", OrderByColumn.forValue("unknown"));
    }

    @Test
    void orderByForValue_shouldResolveKnownColumns() {
        assertEquals("build_name", OrderByColumn.forValue("name"));
        assertEquals("build_number", OrderByColumn.forValue("number"));
        assertEquals("build_date", OrderByColumn.forValue("date"));
    }

    @Test
    void sortOrderForValue_shouldDefaultToDesc_whenNull() {
        assertEquals("desc", SortOrder.forValue(null));
    }

    @Test
    void sortOrderForValue_shouldDefaultToDesc_whenUnknown() {
        assertEquals("desc", SortOrder.forValue("unknown"));
    }

    @Test
    void sortOrderForValue_shouldResolveKnownDirections() {
        assertEquals("asc", SortOrder.forValue("asc"));
        assertEquals("desc", SortOrder.forValue("desc"));
    }
}
