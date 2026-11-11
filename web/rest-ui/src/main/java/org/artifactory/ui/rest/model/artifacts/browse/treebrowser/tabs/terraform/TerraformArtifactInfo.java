package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.terraform;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.addon.terraform.TerraformDependency;
import org.artifactory.addon.terraform.TerraformInfo;
import org.artifactory.addon.terraform.TerraformInput;
import org.artifactory.addon.terraform.TerraformOutput;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;

import java.util.List;

/**
 * @author Barakh
 */
@Getter
@Setter
public class TerraformArtifactInfo extends BaseArtifactInfo {

    private TerraformInfo terraformInfo;
    private List<String> subModules;
    private List<TerraformDependency> providersDependencies;
    private List<TerraformDependency> modulesDependencies;
    private List<TerraformInput> inputs;
    private List<TerraformOutput> outputs;
    private String readme;
}
