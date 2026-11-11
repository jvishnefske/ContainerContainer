package org.artifactory.api.replication;

import lombok.Data;

/**
 * @author Yoaz Menda
 */
@Data
public class ReplicationMetaData {
    private String replicationType;
    private String sourceRepo;
    private String targetUrl;
    private String includePathPrefixPattern;
    private String excludePathPrefixPattern;
    private String replicationStrategy;

    public ReplicationMetaData() {
    }

    public ReplicationMetaData(String replicationType, String sourceRepo, String targetUrl,
            String includePathPrefixPattern,
            String excludePathPrefixPattern, String replicationStrategy) {
        this.replicationType = replicationType;
        this.sourceRepo = sourceRepo;
        this.targetUrl = targetUrl;
        this.includePathPrefixPattern = includePathPrefixPattern;
        this.excludePathPrefixPattern = excludePathPrefixPattern;
        this.replicationStrategy = replicationStrategy;
    }
}
