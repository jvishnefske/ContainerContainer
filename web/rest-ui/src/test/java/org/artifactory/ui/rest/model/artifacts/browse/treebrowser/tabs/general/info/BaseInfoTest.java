package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseInfoTest {

    @ParameterizedTest
    @CsvSource({
            "1715953680000, 2024-05-17T13:48:00.000Z",
            "1735732800000, 2025-01-01T12:00:00.000Z",
            "1735732800123, 2025-01-01T12:00:00.123Z",
            "0, 1970-01-01T00:00:00.000Z",
            "4102444799999, 2099-12-31T23:59:59.999Z"
    })
    void formatIsoDateShouldReturnIso8601UtcString(long epochMillis, String expected) {
        assertEquals(expected, BaseInfo.formatIsoDate(epochMillis));
    }
}
