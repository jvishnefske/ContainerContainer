package org.artifactory.storage.db.repo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Tamar Rosen
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DbRepositoryConfig {
    private String key;
    private String type;
    /** Shadow of {@link #type}: federated repos shadow as local, all others as their own type. */
    private String repoTypeV2;
    private String packageType;
    private long revision;
    private long created;
    private long modified;
    private byte[] repositoryInfoBlob;
}
