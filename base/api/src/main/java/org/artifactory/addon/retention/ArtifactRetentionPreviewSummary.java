package org.artifactory.addon.retention;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Tamar Rosen
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactRetentionPreviewSummary {
    private long count;
    private long totalSize;
}
