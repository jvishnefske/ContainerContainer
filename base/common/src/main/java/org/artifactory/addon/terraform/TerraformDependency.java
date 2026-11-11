package org.artifactory.addon.terraform;

import lombok.Builder;
import lombok.Data;

/**
 * @author BarakH
 */
@Data
@Builder
public class TerraformDependency {

    private String name;
    private String version;
}