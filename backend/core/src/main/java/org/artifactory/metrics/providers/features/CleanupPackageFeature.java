package org.artifactory.metrics.providers.features;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.cleanup.CleanupPolicyAddon;
import org.artifactory.addon.cleanup.statistics.CleanupPackageStats;
import org.artifactory.api.callhome.FeatureGroup;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.entitlement.EntitlementType;
import org.springframework.stereotype.Component;

/**
 * @author Oleksandr Legenkiy
 */
@Slf4j
@Component
public class CleanupPackageFeature implements CallHomeFeature {

    static final String MAIN_FEATURE_NAME = "Package Cleanup";
    static final String TOTAL_CLEANUP_PACKAGES_POLICIES = "Total_Cleanup_Packages_Policies";
    static final String LAST_DAY_RUNS = "Last_Day_Cleanup_Packages_Policies_Runs";
    static final String LAST_DAY_CLEANED_PACKAGES = "Last_Day_Cleaned_Packages";
    static final String LAST_DAY_CLEANED_ARTIFACTS = "Last_Day_Cleaned_Artifacts";
    static final String LAST_DAY_PACKAGES_FAILED = "Last_Day_Cleaned_Packages_Failed";
    static final String LAST_DAY_CLEANED_PACKAGES_VIRTUAL_SIZE = "Last_Day_Cleaned_Packages_Virtual_Size";
    static final String LAST_DAY_CLEANED_PACKAGES_PHYSICAL_SIZE = "Last_Day_Cleaned_Packages_Physical_Size";

    @Override
    public FeatureGroup getFeature() {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        if (!ConstantValues.policyCleanupPackageEnabled.getBoolean() ||
                !addonsManager.isEntitled(EntitlementType.CLEANUP_POLICY)) {
            return null;
        }
        CleanupPolicyAddon addon = addonsManager.addonByType(CleanupPolicyAddon.class);
        CleanupPackageStats stats;
        try {
            stats = addon.getCleanupPackageStats();
        } catch (Exception e) {
            log.error("Failed to fetch package cleanup run statistics.", e);
            return null;
        }
        return createFeatureGroup(stats);
    }

    private FeatureGroup createFeatureGroup(CleanupPackageStats stats) {
        FeatureGroup feature = new FeatureGroup(MAIN_FEATURE_NAME);
        feature.addFeatureAttribute(TOTAL_CLEANUP_PACKAGES_POLICIES, stats.getPoliciesTotal());
        feature.addFeatureAttribute(LAST_DAY_RUNS, stats.getRunsTotal());
        feature.addFeatureAttribute(LAST_DAY_CLEANED_PACKAGES, stats.getPackagesCleaned());
        feature.addFeatureAttribute(LAST_DAY_CLEANED_PACKAGES_VIRTUAL_SIZE, stats.getCleanedPackagesVirtualSize());
        feature.addFeatureAttribute(LAST_DAY_CLEANED_PACKAGES_PHYSICAL_SIZE, stats.getCleanedPackagesPhysicalSize());
        feature.addFeatureAttribute(LAST_DAY_PACKAGES_FAILED, stats.getPackagesFailed());
        feature.addFeatureAttribute(LAST_DAY_CLEANED_ARTIFACTS, stats.getArtifactsCleaned());
        return feature;
    }
}
