package org.artifactory.addon.support;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jfrog.support.rest.model.manifest.NodeManifestBundleInfo;

import java.util.Date;

/**
 * @author Tamir Hadad
 */
@Data
@EqualsAndHashCode(callSuper=false)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ArtifactoryNodeManifestBundleInfo extends NodeManifestBundleInfo {

    private long createDate;

    public ArtifactoryNodeManifestBundleInfo(NodeManifestBundleInfo nodeManifestBundleInfo) {
        this.setId(nodeManifestBundleInfo.getId());
        this.setCreated(nodeManifestBundleInfo.getCreated());
        this.setDescription(nodeManifestBundleInfo.getDescription());
        this.setName(nodeManifestBundleInfo.getName());
        this.setStatus(nodeManifestBundleInfo.getStatus() == null ? null :
                nodeManifestBundleInfo.getStatus().replace('_', ' '));
        this.createDate = Date.from(nodeManifestBundleInfo.getCreated().toInstant()).getTime();
    }
}


