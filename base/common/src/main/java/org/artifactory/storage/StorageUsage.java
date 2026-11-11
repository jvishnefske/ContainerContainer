package org.artifactory.storage;


import lombok.Data;
import lombok.NonNull;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;

import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author BarakH
 */
@Data
public class StorageUsage {

    long revision;
    private final Map<String, Long> repoStorageSum;
    private Map<String, Long> projectsStorageSum;
    private Map<String, Long> projectsLastNotifiedOnStorageQuota;

    public StorageUsage() {
        revision = 1;
        repoStorageSum = new HashMap<>();
        projectsStorageSum = new HashMap<>();
        projectsLastNotifiedOnStorageQuota = new HashMap<>();
    }

    public StorageUsage(StorageSummaryInfo info) {
        this.revision = 1;
        this.projectsStorageSum = info.getProjectsTotalSizes();
        this.repoStorageSum = info.getRepoStorageSummaries().values().stream()
                .collect(Collectors.toMap(RepoStorageSummaryInfo::getRepoKey, RepoStorageSummaryInfo::getUsedSpace));
        projectsLastNotifiedOnStorageQuota = new HashMap<>();
    }

    public StorageUsage(StorageUsage other) {
        revision = other.revision;
        repoStorageSum = new HashMap<>(other.repoStorageSum);
        projectsStorageSum = new HashMap<>(other.projectsStorageSum);
        projectsLastNotifiedOnStorageQuota = new HashMap<>(other.projectsLastNotifiedOnStorageQuota);
    }

    public void setProjectsLastNotifiedOnStorageQuota(@Nullable Map<String, Long> projectsLastNotifiedOnStorageQuota) {
        this.projectsLastNotifiedOnStorageQuota =
                Objects.requireNonNullElseGet(projectsLastNotifiedOnStorageQuota, HashMap::new);
    }

    @NonNull
    public Map<String, Long> getProjectsLastNotifiedOnStorageQuota() {
        return projectsLastNotifiedOnStorageQuota;
    }

    public void bumpRevision() {
        this.revision++;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }
}
