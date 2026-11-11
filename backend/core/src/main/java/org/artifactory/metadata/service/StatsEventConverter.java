package org.artifactory.metadata.service;

import com.google.common.collect.Lists;
import com.jfrog.artifactory.eventapi.stats.StatsEntryOuterClass;
import com.jfrog.artifactory.eventapi.stats.StatsEventOuterClass;
import lombok.experimental.UtilityClass;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.fs.model.MetadataStats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts aggregated Artifactory stats into versioned jfbus {@link StatsEventOuterClass.StatsEvent} batches.
 */
@UtilityClass
public class StatsEventConverter {

    static final String SCHEMA_VERSION = "stats.v1";
    static final int MAX_ENTRIES_PER_EVENT = 1000;

    /**
     * Converts a stats map into batched {@link StatsEventOuterClass.StatsEvent} messages ready for jfbus.
     * Applies the same entry-level filters as the REST baseline (lead-artifact-only, non-zero downloads)
     * and also skips repos whose package type is in {@code excludedPackageTypes} — mirroring the
     * package-index event pipeline's eligibility footing so that stats events never flow for repos
     * whose metadata is not tracked in Metadata Server.
     *
     * @param pathToStats          per-path stats produced by the stats flush tick
     * @param leadArtifactFacade   used to enforce the lead-artifact filter
     * @param repositoryService    used to resolve repo configs and remote-cache repo keys
     * @param excludedPackageTypes enum names (e.g. "Generic", "BuildInfo") of package types to skip;
     *                             typically sourced from {@code jfbus.excluded.package.types}
     */
    public static List<StatsEventOuterClass.StatsEvent> toStatsEvents(
            Map<RepoPath, MetadataStats> pathToStats,
            LeadArtifactFacade leadArtifactFacade,
            RepositoryService repositoryService,
            List<String> excludedPackageTypes) {
        Map<String, List<StatsEntryOuterClass.StatsEntry>> entriesByRepo = new LinkedHashMap<>();
        pathToStats.forEach((repoPath, metadataStats) -> {
            if (!leadArtifactFacade.isPackageConsumptionLead(repoPath) || metadataStats.downloadCount() <= 0) {
                return;
            }
            RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoPath.getRepoKey());
            if (repoConfig != null && repoConfig.getPackageType() != null
                    && excludedPackageTypes.contains(repoConfig.getPackageType().name())) {
                return;
            }
            String repoName = MetadataEntityFacade.resolveRepoKey(repoPath.getRepoKey(), repoConfig);
            StatsEntryOuterClass.StatsEntry.Builder entryBuilder = StatsEntryOuterClass.StatsEntry.newBuilder()
                    .setRepoName(repoName)
                    .setLeadFilePath(repoName + "/" + repoPath.getPath())
                    .setAdditionalDownloads(metadataStats.downloadCount())
                    .setAdditionalFollowers(0L)
                    .setLastDownloaded(metadataStats.lastDownloaded())
                    .setRemoteLastDownloaded(metadataStats.remoteLastDownloaded())
                    .setForce(metadataStats.force());
            if (repoConfig != null && repoConfig.getPackageType() != null) {
                entryBuilder.setRepoPackageType(repoConfig.getPackageType().getType());
            }
            entriesByRepo.computeIfAbsent(repoName, ignored -> new ArrayList<>()).add(entryBuilder.build());
        });

        List<StatsEventOuterClass.StatsEvent> events = new ArrayList<>();
        entriesByRepo.forEach((repoName, entries) -> {
            for (List<StatsEntryOuterClass.StatsEntry> chunk : Lists.partition(entries, MAX_ENTRIES_PER_EVENT)) {
                events.add(StatsEventOuterClass.StatsEvent.newBuilder()
                        .setSchemaVersion(SCHEMA_VERSION)
                        .setEventId(UUID.randomUUID().toString())
                        .setEmittedAt(Instant.now().toString())
                        .addAllEntries(chunk)
                        .build());
            }
        });
        return events;
    }
}
