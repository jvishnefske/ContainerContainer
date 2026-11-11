package org.artifactory.metrics.providers.features;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.addon.retention.RetentionStats;
import org.artifactory.addon.retention.RetentionStatsDetails;
import org.artifactory.api.callhome.FeatureGroup;
import org.artifactory.common.ConstantValues;
import org.artifactory.util.UnsupportedByLicenseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Mor Merhav
 */
@Component
public class ArtifactRetentionFeature implements CallHomeFeature {

    private static final String MAIN_FEATURE_NAME = "Artifactory On Ice";
    private static final String IS_COLD_INSTANCE = "Is_Cold_Instance";

    private static final String TOTAL_POLICIES_NO = "Total_%s_Policies";
    private static final String LAST_DAY_ARCHIVE_POLICIES_RUNS = "Last_Day_Archive_%s_Policies_Runs";
    private static final String LAST_DAY_ARCHIVED = "Last_Day_Archived_%s";
    private static final String LAST_DAY_ARCHIVED_SIZE = "Last_Day_Archived_%s_Size";
    private static final String LAST_DAY_ARCHIVED_FAILED = "Last_Day_Archived_%s_Failed";
    private static final String LAST_DAY_ARCHIVED_FAILED_SIZE = "Last_Day_Archived_%s_Failed_Size";
    private static final String LAST_DAY_RESTORE_POLICIES_RUNS = "Last_Day_Restore_%s_Policies_Runs";
    private static final String LAST_DAY_RESTORED = "Last_Day_Restored_%s";
    private static final String LAST_DAY_RESTORED_SIZE = "Last_Day_Restored_%s_Size";
    private static final String LAST_DAY_RESTORED_FAILED = "Last_Day_Restored_%s_Failed";
    private static final String LAST_DAY_RESTORED_FAILED_SIZE = "Last_Day_Restored_%s_Failed_Size";

    private static final String ARTIFACTS_TEMPLATE_VALUE = "Artifacts";
    private static final String PACKAGES_TEMPLATE_VALUE = "Packages";

    private final AddonsManager addonsManager;

    @Autowired
    public ArtifactRetentionFeature(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public FeatureGroup getFeature() {
        ArtifactRetentionAddon artifactRetentionAddon = addonsManager.addonByType(ArtifactRetentionAddon.class);
        if (!artifactRetentionAddon.isRetentionEnabled()) {
            return null;
        }

        boolean coldInstance = artifactRetentionAddon.isColdInstanceV1orV2();
        FeatureGroup feature = new FeatureGroup(MAIN_FEATURE_NAME);
        feature.addFeatureAttribute(IS_COLD_INSTANCE, coldInstance);
        if (coldInstance) {
            return feature;
        }

        RetentionStats retentionStats;
        try {
            retentionStats = artifactRetentionAddon.getArtifactRetentionStats();
        } catch (UnsupportedByLicenseException ignored) {
            return null;
        }
        if (retentionStats == null) {
            return null;
        }

        populateFeatureAttributes(feature, retentionStats.artifactRetentionStats(), ARTIFACTS_TEMPLATE_VALUE);
        if (ConstantValues.retentionEnabledForPackages.getBoolean()) {
            populateFeatureAttributes(feature, retentionStats.packageRetentionStats(), PACKAGES_TEMPLATE_VALUE);
        }

        return feature;
    }

    private void populateFeatureAttributes(FeatureGroup feature, RetentionStatsDetails details, String templateValue) {
        feature.addFeatureAttribute(String.format(TOTAL_POLICIES_NO, templateValue),
                details.getPoliciesCount());
        feature.addFeatureAttribute(String.format(LAST_DAY_ARCHIVE_POLICIES_RUNS, templateValue),
                details.getArchiveRunCount());
        feature.addFeatureAttribute(String.format(LAST_DAY_ARCHIVED, templateValue),
                details.getArchiveSuccessCount());
        feature.addFeatureAttribute(String.format(LAST_DAY_ARCHIVED_SIZE, templateValue),
                details.getArchiveSuccessSize());
        feature.addFeatureAttribute(String.format(LAST_DAY_ARCHIVED_FAILED, templateValue),
                details.getArchiveFailureCount());
        feature.addFeatureAttribute(String.format(LAST_DAY_ARCHIVED_FAILED_SIZE, templateValue),
                details.getArchiveFailureSize());
        feature.addFeatureAttribute(String.format(LAST_DAY_RESTORE_POLICIES_RUNS, templateValue),
                details.getRestoreRunCount());
        feature.addFeatureAttribute(String.format(LAST_DAY_RESTORED, templateValue),
                details.getRestoreSuccessCount());
        feature.addFeatureAttribute(String.format(LAST_DAY_RESTORED_SIZE, templateValue),
                details.getRestoreSuccessSize());
        feature.addFeatureAttribute(String.format(LAST_DAY_RESTORED_FAILED, templateValue),
                details.getRestoreFailureCount());
        feature.addFeatureAttribute(String.format(LAST_DAY_RESTORED_FAILED_SIZE, templateValue),
                details.getRestoreFailureSize());
    }
}
