package org.artifactory.model.fs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StatsImplTest {

    @ParameterizedTest
    @MethodSource("lastDownloadedProvider")
    void mergeUpdatesLastDownloadedBasedOnTimestamp(long currentLastDownloaded, String currentLastDownloadedBy,
                                                    long otherLastDownloaded, String otherLastDownloadedBy,
                                                    long expectedLastDownloaded, String expectedLastDownloadedBy) {
        StatsImpl stats1 = new StatsImpl();
        stats1.setLastDownloaded(currentLastDownloaded);
        stats1.setLastDownloadedBy(currentLastDownloadedBy);

        StatsImpl stats2 = new StatsImpl();
        stats2.setLastDownloaded(otherLastDownloaded);
        stats2.setLastDownloadedBy(otherLastDownloadedBy);

        stats1.merge(stats2);

        assertEquals(expectedLastDownloaded, stats1.getLastDownloaded());
        assertEquals(expectedLastDownloadedBy, stats1.getLastDownloadedBy());
    }


    @ParameterizedTest
    @MethodSource("remoteLastDownloadedProvider")
    void mergeUpdatesRemoteLastDownloadedBasedOnTimestamp(long currentRemoteLastDownloaded,
                                                          String currentRemoteLastDownloadedBy,
                                                          long otherRemoteLastDownloaded,
                                                          String otherRemoteLastDownloadedBy,
                                                          long expectedRemoteLastDownloaded,
                                                          String expectedRemoteLastDownloadedBy) {
        StatsImpl stats1 = new StatsImpl();
        stats1.setRemoteLastDownloaded(currentRemoteLastDownloaded);
        stats1.setRemoteLastDownloadedBy(currentRemoteLastDownloadedBy);

        StatsImpl stats2 = new StatsImpl();
        stats2.setRemoteLastDownloaded(otherRemoteLastDownloaded);
        stats2.setRemoteLastDownloadedBy(otherRemoteLastDownloadedBy);

        stats1.merge(stats2);

        assertEquals(expectedRemoteLastDownloaded, stats1.getRemoteLastDownloaded());
        assertEquals(expectedRemoteLastDownloadedBy, stats1.getRemoteLastDownloadedBy());
    }


    @ParameterizedTest
    @MethodSource("downloadCountProvider")
    void mergeAggregatesDownloadCounts(long currentCount, long otherCount, long expectedCount) {
        StatsImpl stats1 = new StatsImpl();
        stats1.setDownloadCount(currentCount);

        StatsImpl stats2 = new StatsImpl();
        stats2.setDownloadCount(otherCount);

        stats1.merge(stats2);

        assertEquals(expectedCount, stats1.getDownloadCount());
    }

    @ParameterizedTest
    @MethodSource("downloadCountProvider")
    void mergeAggregatesRemoteDownloadCounts(long currentCount, long otherCount, long expectedCount) {
        StatsImpl stats1 = new StatsImpl();
        stats1.setRemoteDownloadCount(currentCount);

        StatsImpl stats2 = new StatsImpl();
        stats2.setRemoteDownloadCount(otherCount);

        stats1.merge(stats2);

        assertEquals(expectedCount, stats1.getRemoteDownloadCount());
    }

    @Test
    void mergeWithNullDoesNotChangeStats() {
        StatsImpl originalStats = new StatsImpl();
        originalStats.setDownloadCount(5);
        originalStats.setLastDownloaded(1000);
        originalStats.setLastDownloadedBy("user1");
        originalStats.setRemoteDownloadCount(7);
        originalStats.setRemoteLastDownloaded(1500);
        originalStats.setRemoteLastDownloadedBy("remoteUser1");

        StatsImpl statsToMerge = new StatsImpl(originalStats);

        statsToMerge.merge(null);

        assertEquals(originalStats, statsToMerge);
    }

    static Stream<Arguments> lastDownloadedProvider() {
        return Stream.of(
                Arguments.of(1000L, "user1", 2000L, "user2", 2000L, "user2"),
                Arguments.of(2000L, "user1", 1000L, "user2", 2000L, "user1"),
                Arguments.of(1000L, "user1", 1000L, "user2", 1000L, "user1")
        );
    }

    static Stream<Arguments> downloadCountProvider() {
        return Stream.of(
                Arguments.of(5L, 3L, 8L),
                Arguments.of(0L, 10L, 10L),
                Arguments.of(10L, 0L, 10L),
                Arguments.of(0L, 0L, 0L)
        );
    }

    static Stream<Arguments> remoteLastDownloadedProvider() {
        return Stream.of(
                Arguments.of(1500L, "remoteUser1", 2500L, "remoteUser2", 2500L, "remoteUser2"),
                Arguments.of(2500L, "remoteUser1", 1500L, "remoteUser2", 2500L, "remoteUser1"),
                Arguments.of(1500L, "remoteUser1", 1500L, "remoteUser2", 1500L, "remoteUser1")
        );
    }
}
