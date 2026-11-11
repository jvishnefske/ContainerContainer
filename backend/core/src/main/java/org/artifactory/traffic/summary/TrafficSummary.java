package org.artifactory.traffic.summary;

import lombok.Builder;
import org.artifactory.storage.db.traffic.summary.entity.TrafficSummaryEntity;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author kunalm
 */
@Builder
public record TrafficSummary(
        long from,
        String fromDate,
        long to,
        String toDate,
        MonthlyRequests monthlyRequests,
        MonthlyTransferBytes monthlyTransferBytes,
        List<HourlySnapshot> hourlySnapshots
) {

    @Builder
    public record MonthlyRequests(
            long total,
            long downloads,
            long uploads
    ) {
    }

    @Builder
    public record MonthlyTransferBytes(
            long total,
            long downloads,
            long uploads
    ) {
    }

    @Builder
    public record HourlySnapshot(
            long time,
            long totalRequests,
            long downloadRequests,
            long uploadRequests,
            long totalBytes,
            long downloadBytes,
            long uploadBytes
    ) {
    }

    @Builder
    private record TrafficTotals(
            long totalRequests,
            long totalDownloads,
            long totalUploads,
            long totalBytes,
            long totalDownloadBytes,
            long totalUploadBytes
    ) {
    }

    public static TrafficSummary fromTrafficSummaryEntities(List<TrafficSummaryEntity> entities, long from, long to) {
        if (entities.isEmpty()) {
            return TrafficSummary.builder().build();
        }

        TrafficTotals totals = computeTotals(entities);
        TrafficSummary.MonthlyRequests monthlyRequests = TrafficSummary.MonthlyRequests.builder()
                .total(totals.totalRequests)
                .downloads(totals.totalDownloads)
                .uploads(totals.totalUploads)
                .build();
        TrafficSummary.MonthlyTransferBytes monthlyTransferBytes = TrafficSummary.MonthlyTransferBytes.builder()
                .total(totals.totalBytes)
                .downloads(totals.totalDownloadBytes)
                .uploads(totals.totalUploadBytes)
                .build();
        List<TrafficSummary.HourlySnapshot> hourlySnapshots =
                entities.stream().map(entity -> TrafficSummary.HourlySnapshot.builder()
                        .time(entity.time())
                        .totalRequests(entity.totalRequests())
                        .downloadRequests(entity.downloadRequests())
                        .uploadRequests(entity.uploadRequests())
                        .totalBytes(entity.totalBytes())
                        .downloadBytes(entity.downloadBytes())
                        .uploadBytes(entity.uploadBytes())
                        .build()).toList();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");
        String fromDate = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).format(formatter);
        String toDate = Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC).format(formatter);

        return TrafficSummary.builder()
                .from(from)
                .fromDate(fromDate)
                .to(to)
                .toDate(toDate)
                .monthlyRequests(monthlyRequests)
                .monthlyTransferBytes(monthlyTransferBytes)
                .hourlySnapshots(hourlySnapshots)
                .build();
    }

    private static TrafficTotals computeTotals(List<TrafficSummaryEntity> entities) {
        long totalRequests = 0;
        long totalDownloads = 0;
        long totalUploads = 0;
        long totalBytes = 0;
        long totalDownloadBytes = 0;
        long totalUploadBytes = 0;
        for (TrafficSummaryEntity entity : entities) {
            totalRequests += entity.totalRequests();
            totalDownloads += entity.downloadRequests();
            totalUploads += entity.uploadRequests();
            totalBytes += entity.totalBytes();
            totalDownloadBytes += entity.downloadBytes();
            totalUploadBytes += entity.uploadBytes();
        }
        return TrafficTotals.builder()
                .totalRequests(totalRequests)
                .totalDownloads(totalDownloads)
                .totalUploads(totalUploads)
                .totalBytes(totalBytes)
                .totalDownloadBytes(totalDownloadBytes)
                .totalUploadBytes(totalUploadBytes)
                .build();
    }
}
