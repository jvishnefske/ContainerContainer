package org.artifactory.repo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
public class RepoStatDto {

    private String repoKey;
    @Setter
    private long lastDownloaded;
    @Setter
    private long lastUploaded;
    private long lastStatisticsModified;
    private RepoStatUpdateType updateType;

    public enum RepoStatUpdateType {
        DOWNLOAD,
        UPLOAD
    }

}
