package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.cargo;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.addon.cargo.CargoDependencyMetadataInfo;
import org.artifactory.addon.cargo.CargoInfo;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;

import java.util.List;

/**
 * @author Volodymyr Shulga
 */
@Getter
@Setter
public class CargoArtifactInfo extends BaseArtifactInfo {
    private CargoInfo info;
    private List<CargoDependencyMetadataInfo> dependencies;
}
