package org.artifactory.ui.rest.model.storage;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.rest.common.model.BaseModel;

/**
 * @author BarakH
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class ProjectStorageUsage extends BaseModel {

    private String projectKey;
    private String projectName;
    private long usedStorageBytes;
    private long storageLimitBytes;

    public ProjectStorageUsage() {
    }

    public ProjectStorageUsage(String projectKey, String projectName, long usedStorageBytes, long storageLimitBytes) {
        this.projectKey = projectKey;
        this.projectName = projectName;
        this.usedStorageBytes = usedStorageBytes;
        this.storageLimitBytes = storageLimitBytes;
    }
}
