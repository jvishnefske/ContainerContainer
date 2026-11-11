package org.artifactory.addon.terraform;

import lombok.Builder;
import lombok.Data;

/**
 * @author BarakH
 */
@Data
@Builder
public class TerraformInfo {

    private String type;
    private String namespace;
    private String module;
    private String provider;
    private String version;
    private String flavor;
}