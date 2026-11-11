package org.artifactory.storage.event;

import org.artifactory.event.EventType;
import org.jfrog.common.JsonSensitiveDataException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

class StorageEventInfoTest {
    @ParameterizedTest
    @MethodSource
    void fromJson(String json, StorageEventInfo.Data expected, Class<Exception> expectedError) {
        if (expectedError == null) {
            StorageEventInfo.Data result = StorageEventInfo.Data.fromJson(json);
            assertEquals(result, expected);
        } else {
            assertThrows(expectedError, () -> StorageEventInfo.Data.fromJson(json));
        }
    }

    private static Object[][] fromJson() {
        return new Object[][] {
                {null, null, null},
                {"", null, null},
                {"{\"retryCount\":}", StorageEventInfo.Data.empty(), JsonSensitiveDataException.class},
                {"{\"retryCount\":abc}", StorageEventInfo.Data.empty(), JsonSensitiveDataException.class},
                {"{\"somethingElse\":42}", StorageEventInfo.Data.empty(), null},
                {"{\"retryCount\":42}", new StorageEventInfo.Data(42), null}
        };
    }

    @ParameterizedTest
    @MethodSource
    void toJson(StorageEventInfo.Data data, String expected) {
        String result = StorageEventInfo.Data.toJson(data);
        if (result == null) {
            assertNull(expected);
        } else {
            assertTrue(result.contains(expected));
        }
    }

    private static Object[][] toJson() {
        return new Object[][] {
                {null, null},
                {StorageEventInfo.Data.empty(), "\"retryCount\":0"},
                {new StorageEventInfo.Data(42), "\"retryCount\":42"}
        };
    }


    @ParameterizedTest
    @MethodSource
    void withGCHintsPrefix(StorageEventInfo.Data data, String expected) {
        String hintsJson = StorageEventInfo.Data.withGCDataHintPrefix(data);
        assertTrue(hintsJson.contains(expected));
    }

    private static Object[][] withGCHintsPrefix() {
        return new Object[][] {
                {null, StorageEventInfo.Data.GC_DATA_HINT_KEY},
                {new StorageEventInfo.Data(42), StorageEventInfo.Data.GC_DATA_HINT_KEY + "{\"retryCount\":42"}
        };
    }


    @ParameterizedTest
    @MethodSource
    void getEventsGcHints(Set<String> hints, StorageEventInfo.Data expected, Class<Exception> expectedError) {
        if (expectedError == null) {
            StorageEventInfo.Data res = StorageEventInfo.Data.getEventsGcData(hints);
            assertEquals(res, expected);
        } else {
            assertThrows(expectedError, () -> StorageEventInfo.Data.getEventsGcData(hints));
        }
    }

    private static Object[][] getEventsGcHints() {
        return new Object[][] {
                {null, StorageEventInfo.Data.empty(), null},
                {Set.of(), StorageEventInfo.Data.empty(), null},
                {Set.of(StorageEventInfo.Data.GC_DATA_HINT_KEY), StorageEventInfo.Data.empty(), null},
                {Set.of("a.1", "b.2", "c.3"), StorageEventInfo.Data.empty(), null},
                {Set.of(StorageEventInfo.Data.GC_DATA_HINT_KEY + "[\"key.val\"]"),
                        null, JsonSensitiveDataException.class},
                {Set.of(StorageEventInfo.Data.GC_DATA_HINT_KEY + "{\"retryCount\":\"XYZ\"}"),
                        null, JsonSensitiveDataException.class},
                {null, StorageEventInfo.Data.empty(), null},
                {Set.of(StorageEventInfo.Data.GC_DATA_HINT_KEY + "{\"retryCount\":1}"),
                        new StorageEventInfo.Data(1), null}
        };
    }


    @ParameterizedTest
    @MethodSource
    void testEquals(String sha1A, String sha1B, EventType eventTypeA, EventType eventTypeB, boolean expected) {
        StorageEventInfo xA = new StorageEventInfo(eventTypeA, sha1A, "", 42L);
        StorageEventInfo xB = new StorageEventInfo(eventTypeB, sha1B, "", 42L);
        assertEquals(xA.equals(xB), expected);
    }
    private static Object[][] testEquals() {
        return new Object[][] {
                {null, null, EventType.delete, EventType.delete, true},
                {null, "sha1", EventType.delete, EventType.delete, false},
                {"sha1", null, EventType.delete, EventType.delete, false},
                {"sha1", "SHA1", EventType.delete, EventType.delete, false},
                {"", "", EventType.delete, EventType.delete, true},
                {"sha1", "sha1", EventType.delete, EventType.delete, true},

                {null, null, EventType.create, EventType.delete, false},
                {null, "sha1", EventType.create, EventType.delete, false},
                {"sha1", null, EventType.create, EventType.delete, false},
                {"sha1", "SHA1", EventType.create, EventType.delete, false},
                {"", "", EventType.create, EventType.delete, false},
                {"sha1", "sha1", EventType.create, EventType.delete, false},
        };
    }

    @ParameterizedTest
    @MethodSource
    void testHashCode(String sha1A, EventType eventTypeA, String sha1B, EventType eventTypeB, boolean shouldBeSame) {
        StorageEventInfo xA = new StorageEventInfo(eventTypeA, sha1A, "", 42L);
        StorageEventInfo xB = new StorageEventInfo(eventTypeB, sha1B, "", 42L);
        if(shouldBeSame) {
            assertEquals(xA.hashCode(), xB.hashCode());
        } else {
            assertNotEquals(xA.hashCode(), xB.hashCode());
        }
    }
    private static Object[][] testHashCode() {
        return new Object[][] {
                {null, EventType.delete, null, EventType.delete, true},
                {"", EventType.delete, "", EventType.delete, true},
                {"sha1", EventType.delete, "sha1", EventType.delete, true},
                {null, EventType.create, null, EventType.create, true},
                {"", EventType.create, "", EventType.create, true},
                {"sha1", EventType.create, "sha1", EventType.delete, false},
                {"sha1", EventType.create, "SHA1", EventType.create, false}
        };
    }
}