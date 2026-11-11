package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.alpine;

import org.artifactory.addon.alpine.AlpineInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;

import java.util.List;

/**
 * @author Yuval Reches
 */
public class AlpineArtifactInfo extends BaseArtifactInfo {

    private AlpineInfo alpineInfo;
    private List<String> alpineDependencies;

    public AlpineInfo getAlpineInfo() {
        return alpineInfo;
    }

    public void setAlpineInfo(AlpineInfo alpineInfo) {
        this.alpineInfo = alpineInfo;
    }

    public List<String> getAlpineDependencies() {
        return alpineDependencies;
    }

    public void setAlpineDependencies(List<String> alpineDependencies) {
        this.alpineDependencies = alpineDependencies;
    }

}
