package org.artifactory.addon.terraform;

import lombok.Builder;
import lombok.Data;

/**
 * @author Michael Akushsky
 */
@Data
@Builder
public class TerraformOutput {

    private String name;
    private String description;
}
