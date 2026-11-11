package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;
import org.artifactory.descriptor.repo.TerraformType;
import org.artifactory.repo.packagetype.ExternalDependenciesConfig;

/**
 * @author volodymyrm
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class TerraformConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(skip = true)
    private String terraformRegistryUrl = "https://registry.terraform.io";
    @MetadataInfo(skip = true)
    private String terraformProvidersUrl = "https://releases.hashicorp.com";
    @MetadataInfo(defaultValue = "MODULE")
    private TerraformType terraformType = TerraformType.MODULE;
    @MetadataInfo(skip = true)
    private ExternalDependenciesConfig externalDependencies;
}