package org.artifactory.maven.curation;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class MavenPackageVersionCurationComparatorTest {
    @Test
    void testCompareByReleaseDateDescending() {
        MavenVersionDates v1 = Mockito.mock(MavenVersionDates.class);
        MavenVersionDates v2 = Mockito.mock(MavenVersionDates.class);

        when(v1.releaseDate()).thenReturn(Instant.parse("2023-01-01T00:00:00Z"));
        when(v2.releaseDate()).thenReturn(Instant.parse("2024-01-01T00:00:00Z"));

        MavenPackageVersionCurationComparator comparator = new MavenPackageVersionCurationComparator();
        assertTrue(comparator.compare(v1, v2) > 0);
        assertTrue(comparator.compare(v2, v1) < 0);
    }

    @Test
    void testCompareByVersionWhenDatesEqual() {
        MavenVersionDates v1 = Mockito.mock(MavenVersionDates.class);
        MavenVersionDates v2 = Mockito.mock(MavenVersionDates.class);

        when(v1.releaseDate()).thenReturn(Instant.parse("2023-01-01T00:00:00Z"));
        when(v2.releaseDate()).thenReturn(Instant.parse("2023-01-01T00:00:00Z"));
        when(v1.version()).thenReturn("1.0.0");
        when(v2.version()).thenReturn("2.0.0");

        MavenPackageVersionCurationComparator comparator = new MavenPackageVersionCurationComparator();
        assertTrue(comparator.compare(v1, v2) > 0);
        assertTrue(comparator.compare(v2, v1) < 0);
    }

    @Test
    void testCompareByVersionWhenEpochDate() {
        MavenVersionDates v1 = Mockito.mock(MavenVersionDates.class);
        MavenVersionDates v2 = Mockito.mock(MavenVersionDates.class);

        when(v1.releaseDate()).thenReturn(Instant.EPOCH);
        when(v2.releaseDate()).thenReturn(Instant.parse("2023-01-01T00:00:00Z"));
        when(v1.version()).thenReturn("1.0.0");
        when(v2.version()).thenReturn("2.0.0");

        MavenPackageVersionCurationComparator comparator = new MavenPackageVersionCurationComparator();
        assertTrue(comparator.compare(v1, v2) > 0);
        assertTrue(comparator.compare(v2, v1) < 0);
    }

    @Test
    void testCompareEqual() {
        MavenVersionDates v1 = Mockito.mock(MavenVersionDates.class);
        MavenVersionDates v2 = Mockito.mock(MavenVersionDates.class);

        when(v1.releaseDate()).thenReturn(Instant.EPOCH);
        when(v2.releaseDate()).thenReturn(Instant.EPOCH);
        when(v1.version()).thenReturn("1.0.0");
        when(v2.version()).thenReturn("1.0.0");

        MavenPackageVersionCurationComparator comparator = new MavenPackageVersionCurationComparator();
        assertEquals(0, comparator.compare(v1, v2));
    }
}