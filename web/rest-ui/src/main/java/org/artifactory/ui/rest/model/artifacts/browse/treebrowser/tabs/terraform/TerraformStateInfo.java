package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.terraform;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;

/**
 * @author Barakh
 */
@Getter
@Setter
public class TerraformStateInfo extends BaseArtifactInfo {

    private String content;
    private TerraformStateInfoMetadata info;

    @Getter
    @Setter
    public static class TerraformStateInfoMetadata {
        private String createdBy;
        private String createdAt;
        private String id;
        private String workspace;
    }
}
