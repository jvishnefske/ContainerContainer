package org.artifactory.storage.db.event.service.metadata.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Uriah Levy
 */
@Data
@AllArgsConstructor
public class MutableArtifactMetadata {
    private String artifactName;
    private String artifactMimeType;
    private long artifactLength;
    private String md5;
    private String sha1;
    private String sha256;
    private boolean lead;
    private String arch;
    private String dist;
    private String path;

    public MutableArtifactMetadata(String artifactName, String artifactMimeType, long artifactLength, String md5,
            String sha1, String sha256, boolean lead, String path) {
        this.artifactName = artifactName;
        this.artifactMimeType = artifactMimeType;
        this.artifactLength = artifactLength;
        this.md5 = md5;
        this.sha1 = sha1;
        this.sha256 = sha256;
        this.lead = lead;
        this.path = path;
    }
}
