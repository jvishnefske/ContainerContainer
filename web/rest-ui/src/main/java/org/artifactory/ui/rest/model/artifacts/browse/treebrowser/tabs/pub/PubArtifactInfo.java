package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.pub;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.addon.pub.PubDependencyMetadataInfo;
import org.artifactory.addon.pub.PubEnvironmentMetadataInfo;
import org.artifactory.addon.pub.PubInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;

import java.util.List;

/**
 * @author Shahaf Golan
 */
@Getter
@Setter
public class PubArtifactInfo extends BaseArtifactInfo {
    private PubInfo info;
    private List<PubDependencyMetadataInfo> dependencies;
    private List<PubDependencyMetadataInfo> devDependencies;
    private List<PubEnvironmentMetadataInfo> environment;
}