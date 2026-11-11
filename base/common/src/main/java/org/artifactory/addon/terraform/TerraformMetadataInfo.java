package org.artifactory.addon.terraform;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author BarakH
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TerraformMetadataInfo {

    private TerraformInfo terraformInfo;
    private List<String> subModules;
    private List<TerraformDependency> providersDependencies;
    private List<TerraformDependency> modulesDependencies;
    private List<TerraformInput> inputs;
    private List<TerraformOutput> outputs;
    private String readme;
}