package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;
import org.artifactory.repo.packagetype.PomCleanupPolicy;

/**
 * @author Uriah Levy
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class MavenConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(defaultValue = "true")
    private boolean suppressPomConsistencyChecks = true;
    @MetadataInfo(skip = true)
    private boolean forceMavenAuthentication = false;
    @MetadataInfo(skip = true)
    private PomCleanupPolicy pomRepositoryReferencesCleanupPolicy = PomCleanupPolicy.discard_active_reference;
    @MetadataInfo(skip = true)
    private boolean fetchJarsEagerly = false;
    @MetadataInfo(skip = true)
    private boolean fetchSourcesEagerly = false;
    @MetadataInfo(skip = true)
    private boolean rejectInvalidJars = false;
}
