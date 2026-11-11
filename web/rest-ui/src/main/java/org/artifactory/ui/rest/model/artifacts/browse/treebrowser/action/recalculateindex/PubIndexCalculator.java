package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.recalculateindex;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.artifactory.addon.pub.PubAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.profile.PackageProfile;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.ph.selector.IndexingSelector;

import java.util.Arrays;

/**
 * @author Shachaf Golan
 */
@JsonTypeName("Pub")
public class PubIndexCalculator extends BaseIndexCalculator {

    private final boolean legacyProfile;

    public PubIndexCalculator() {
        legacyProfile = !Arrays.asList(environment.getActiveProfiles()).contains(PackageProfile.NEW_PUB_IMPL);
    }

    @Override
    public void calculateIndex() throws Exception {
        if (legacyProfile) {
            PubAddon pubAddon = addonsManager.addonByType(PubAddon.class);
            String repoKey = getRepoKey();
            if (pubAddon != null && assertRepoType(repoKey, RepoType.Pub)) {
                pubAddon.reindexPubRepo(repoKey);
            }
        } else {
            IndexingSelector indexingSelector = ContextHelper.get().beanForType(IndexingSelector.class);
            indexingSelector.reindex(getRepoKey(), null);
        }
    }
}