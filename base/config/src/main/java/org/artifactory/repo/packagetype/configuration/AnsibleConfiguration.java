package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;

@Data
@MetadataVersion(metadataVersion = 0)
public class AnsibleConfiguration {
    @MetadataInfo(skip = true)
    private String authUrl = StringUtils.EMPTY;
}
