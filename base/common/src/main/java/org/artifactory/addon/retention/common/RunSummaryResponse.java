package org.artifactory.addon.retention.common;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RunSummaryResponse {
    private PolicySummary policySummary;
    private ExecutionSummary executionSummary;
    private ProcessedItemsSummary processedItemsSummary;
    private RunConfiguration runConfiguration;

    @Data
    @Builder
    public static class PolicySummary {
        private String policyName;
        private Integer runId;
        private boolean dryRun;
        private String status;
        private String triggeredBy;
    }

    @Data
    @Builder
    public static class ExecutionSummary {
        private String startedAt;
        private String finishedAt;
        private long durationMillis;
    }

    @Data
    @Builder
    public static class ProcessedItemsSummary {
        private String itemType;
        private long totalItems;
        private long processedItems;
        private long failedItems;
        private long processedPhysicalSizeBytes;
        private long processedVirtualSizeBytes;
    }

    @Data
    @Builder
    public static class RunConfiguration {
        private String createdAt;
        private String createdBy;
        private String modifiedAt;
        private String modifiedBy;
        private Schedule schedule;
        private String description;
        private boolean skipTrashCan;
        private Target target;
        private CleanupCriteria cleanupCriteria;
    }

    @Data
    @Builder
    public static class Schedule {
        private String cronExpression;
        private long maxRunDurationMillis;
    }

    @Data
    @Builder
    public static class Target {
        private List<String> reposIncluded;
        private List<String> reposExcluded;
        private List<String> packageTypes;
        private List<String> packagesIncluded;
        private List<String> packagesExcluded;
        private boolean includeAllProjects;
        private List<String> projectsIncluded;
    }

    @Data
    @Builder
    public static class CleanupCriteria {
        private String createdBefore;
        private long lastDownloadedBeforeInDays;
        private long keepLastNVersions;
    }
}
