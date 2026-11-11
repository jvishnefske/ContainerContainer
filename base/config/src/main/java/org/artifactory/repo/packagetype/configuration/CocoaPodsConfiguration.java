package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;
import org.artifactory.repo.packagetype.ExternalDependenciesConfig;
import org.artifactory.util.PackageTypeConfigFields;

/**
 * @author Tamar Rosen
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class CocoaPodsConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(skip = true)
    private ExternalDependenciesConfig externalDependencies;
    @MetadataInfo(defaultValue = "false")
    private boolean cocoaPodsForceModifyIndex = false;
    @MetadataInfo(skip = true)
    private String cocoaPodsCdnUrl = PackageTypeConfigFields.COCOA_PODS_CDN_URL.getDefaultValue();
    @MetadataInfo(skip = true)
    private String cocoaPodsSpecsRepoUrl = "https://github.com/CocoaPods/Specs";
    @MetadataInfo(skip = true)
    private VcsGitConfiguration specRepoProvider = new VcsGitConfiguration();
    @MetadataInfo(skip = true)
    private VcsConfiguration vcs;

    public void setCocoaPodsCdnUrl(String cocoaPodsCdnUrl) {
        this.cocoaPodsCdnUrl = StringUtils.trim(cocoaPodsCdnUrl);
    }

    public void setCocoaPodsSpecsRepoUrl(String cocoaPodsSpecsRepoUrl) {
        this.cocoaPodsSpecsRepoUrl = StringUtils.trim(cocoaPodsSpecsRepoUrl);
    }
}
