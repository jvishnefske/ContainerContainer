package org.artifactory.api.rest.retention;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.retention.ArtifactRetentionOperationType;

import java.io.Serializable;
import java.util.List;

/**
 * @author Tamar Rosen
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArtifactRetentionRestoreRequest implements Serializable {
    // to keep API backward compatible
    private ArtifactRetentionOperationType restoreType = ArtifactRetentionOperationType.ARTIFACT_RESTORE;
    private Boolean restoreToOriginal;
    private String fallbackDestinationPath;
    private String centralDestinationPath;
    private List<String> itemsToRestore;
    private Boolean addNamespace;
}
