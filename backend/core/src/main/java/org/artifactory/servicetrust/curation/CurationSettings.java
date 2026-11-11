package org.artifactory.servicetrust.curation;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import org.artifactory.addon.curation.ForceCVSPackagesDeserializer;
import org.artifactory.addon.curation.ForceCVSPackagesParser;
import org.artifactory.common.repo.PackageType;
import org.artifactory.servicetrust.ServiceTrustToken;

import java.util.Set;

/**
 * @author Tomer Slutsky
 */
@Builder(toBuilder = true)
public record CurationSettings(String jfrogCurationNamespace,
                               ServiceTrustToken jfrogServiceTrustToken,
                               boolean enabled, boolean blockDownloadEnabled,
                               boolean indexVersionsCompliantEnabled,
                               boolean allowDownloadWhenUnavailable,
                               @JsonDeserialize(using = ForceCVSPackagesDeserializer.class)
                               Set<PackageType> forceCVSPackages) {

    public CurationSettings {
        forceCVSPackages = ForceCVSPackagesParser.emptyIfNull(forceCVSPackages);
    }
}
