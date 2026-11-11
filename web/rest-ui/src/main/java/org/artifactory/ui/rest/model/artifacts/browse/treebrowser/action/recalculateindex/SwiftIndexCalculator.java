package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.recalculateindex;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.swift.SwiftAddon;
import org.artifactory.common.profile.PackageProfile;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;

import jakarta.ws.rs.core.Response;
import java.util.Arrays;

@JsonTypeName("Swift")
@Slf4j
public class SwiftIndexCalculator extends BaseIndexCalculator {

    private boolean legacyProfile;

    public SwiftIndexCalculator() {
        legacyProfile = !Arrays.asList(environment.getActiveProfiles()).contains(PackageProfile.NEW_SWIFT_IMPL);
    }

    @Override
    public void calculateIndex() throws Exception {
        String repoKey = getRepoKey();
        log.info("Starting swift{} profile reindexing of {} repository", legacyProfile ? " legacy" : "", repoKey);
        if (legacyProfile) {
            SwiftAddon swiftAddon = addonsManager.addonByType(SwiftAddon.class);
            if (swiftAddon != null && assertRepoType(repoKey, RepoType.Swift)) {
                swiftAddon.reindexSwift(repoKey);
            }
        } else {
            try (Response response = packageResourceService.reindex(PackageType.SWIFT, repoKey, true)) {
                log.info("Reindexing of Swift repository '{}' finished with status '{}'", repoKey,
                        response.getStatus());
            }
        }
    }
}
