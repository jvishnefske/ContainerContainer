package org.artifactory.maven.curation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MavenVersionDatesTests {
    @Test
    void testRecordFields() {
        String version = "1.2.3";
        Instant releaseDate = Instant.parse("2024-01-01T00:00:00Z");
        MavenVersionDates mvd = new MavenVersionDates(version, releaseDate);

        assertEquals(version, mvd.version());
        assertEquals(releaseDate, mvd.releaseDate());
    }

    @Test
    void testEqualsAndHashCode() {
        MavenVersionDates mvd1 = new MavenVersionDates("1.0.0", Instant.EPOCH);
        MavenVersionDates mvd2 = new MavenVersionDates("1.0.0", Instant.EPOCH);
        MavenVersionDates mvd3 = new MavenVersionDates("2.0.0", Instant.now());

        assertEquals(mvd1, mvd2);
        assertEquals(mvd1.hashCode(), mvd2.hashCode());
        assertNotEquals(mvd1, mvd3);
    }

    @Test
    void testToString() {
        MavenVersionDates mvd = new MavenVersionDates("1.0.0", Instant.EPOCH);
        String str = mvd.toString();
        assertTrue(str.contains("1.0.0"));
        assertTrue(str.contains("1970-01-01T00:00:00Z"));
    }
}
