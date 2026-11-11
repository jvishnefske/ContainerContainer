package com.jfrog.build.model.api;

import org.jfrog.commons.continues.util.Direction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContinuePageDtoTest {

    @Test
    void constructor_shouldParsePaginationParams_whenProvided() {
        // Arrange
        Map<String, String[]> params = Map.of(
                "continueState", new String[] {"state-token"},
                "mustInclude", new String[] {"some-build-name"},
                "limit", new String[] {"25"},
                "direction", new String[] {"asc"});

        // Act
        ContinuePageDto continuePageDto = new ContinuePageDto(params);

        // Assert
        assertEquals("state-token", continuePageDto.getContinueState());
        assertEquals("some-build-name", continuePageDto.getMustInclude());
        assertEquals(25L, continuePageDto.getLimit());
        assertEquals(Direction.ASC, continuePageDto.getDirection());
    }

    @Test
    void constructor_shouldLeaveUnsetFieldsNull_whenParamsMissing() {
        // Act
        ContinuePageDto continuePageDto = new ContinuePageDto(Map.of());

        // Assert
        assertNull(continuePageDto.getContinueState());
        assertNull(continuePageDto.getMustInclude());
        assertNull(continuePageDto.getLimit());
        assertNull(continuePageDto.getDirection());
    }

    @Test
    void copyConstructor_shouldCopyAllFields() {
        // Arrange
        ContinuePageDto original = new ContinuePageDto("state", "must", 10L, Direction.DESC);

        // Act
        ContinuePageDto copy = new ContinuePageDto(original);

        // Assert
        assertEquals(original.getContinueState(), copy.getContinueState());
        assertEquals(original.getMustInclude(), copy.getMustInclude());
        assertEquals(original.getLimit(), copy.getLimit());
        assertEquals(original.getDirection(), copy.getDirection());
    }
}
