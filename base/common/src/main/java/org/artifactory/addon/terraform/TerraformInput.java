package org.artifactory.addon.terraform;

import lombok.Builder;
import lombok.Data;

/**
 * @author Michael Akushsky
 */
@Data
@Builder
public class TerraformInput {

    private String name;
    private String description;
    private String type;
    private String defaultValue;
}
