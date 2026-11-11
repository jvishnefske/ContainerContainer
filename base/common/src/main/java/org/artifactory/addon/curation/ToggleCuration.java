package org.artifactory.addon.curation;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.artifactory.common.repo.PackageType;

import java.util.Set;

/**
 * @author Tomer Slutsky
 */
public record ToggleCuration(boolean enabled, boolean blockDownloadEnabled, boolean indexVersionsCompliantEnabled,
                             @JsonDeserialize(using = ForceCVSPackagesDeserializer.class)
                             Set<PackageType> forceCVSPackages) {

    public ToggleCuration {
        forceCVSPackages = ForceCVSPackagesParser.emptyIfNull(forceCVSPackages);
    }
}
