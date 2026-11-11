package org.artifactory.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * @author Tomer Slutsky
 */
@AllArgsConstructor
@Getter
public enum TmpEventPartitions {
    /**
     * The partitionToTruncate value is the partition that will be truncated when one of the values
     * in truncationHours is the current hour. Each hour value is between 0-23 based on the calculation in
     * org.artifactory.event.TmpEventLogTruncateJob#getCurrentHour()
     * i.e. in hour 1 of the day, it will attempt to truncate node_events_tmp_p12_17 partition and
     * node_events_tmp_p18_23 if they are empty
     * Each partition truncates the two partitions before it, i.e. P0 truncates P12_17 and P18_23
     */
    P0(List.of("node_events_tmp_p12_17", "node_events_tmp_p18_23"), List.of(0, 1, 2, 3, 4, 5)),
    P1(List.of("node_events_tmp_p18_23", "node_events_tmp_p0_5"), List.of(6, 7, 8, 9, 10, 11)),
    P2(List.of("node_events_tmp_p0_5", "node_events_tmp_p6_11"), List.of(12, 13, 14, 15, 16, 17)),
    P3(List.of("node_events_tmp_p6_11", "node_events_tmp_p12_17"), List.of(18, 19, 20, 21, 22, 23));

    private final List<String> partitionsToTruncate;
    private final List<Integer> truncationHours;

    /**
     * Gets the partition to truncate based on current hour, the hours are in UTC(ClockUtils.epochMillis())
     *
     * @param currentHour The current hour in UTC
     * @return The partition to clean based on hour
     */
    public static Optional<TmpEventPartitions> getPartitionsToTruncate(int currentHour) {
        return Arrays.stream(TmpEventPartitions.values())
                .filter(curr -> curr.getTruncationHours().contains(currentHour)).findFirst();
    }
}
