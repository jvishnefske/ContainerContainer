package org.artifactory.descriptor.repo;

import org.artifactory.common.ConstantValues;

/**
 * @author Shashank Taliwal
 * The main logic is: if the value is set in the system properties, then visibility is false and value is extracted from
 * system properties. Otherwise, visibility is true and value is extracted from the original config.
 */
public class RpmGeneralConfigFilter {

    private RpmGeneralConfigFilter() {
        // Hiding the implicit public constructor to avoid sonar issue
    }

    public static RpmGeneralConfig getFilteredRpmConfig(RpmGeneralConfig originalConfig) {
        if (originalConfig == null) {
            return null;
        }
        if (ConstantValues.yumRepomdCalculateSha2Enabled.isSet()) {
            boolean sha2ConstantSet = ConstantValues.yumRepomdCalculateSha2Enabled.getBoolean();
            originalConfig.setEnableSha256(new RpmEnableSha256Config(false, sha2ConstantSet));
        } else {
            boolean sha2ConstantSet = originalConfig.getEnableSha256().isEnabled();
            originalConfig.setEnableSha256(new RpmEnableSha256Config(true, sha2ConstantSet));
        }

        if (ConstantValues.yumInstallRecommendsEnabled.isSet()) {
            boolean recommendsConstantSet = ConstantValues.yumInstallRecommendsEnabled.getBoolean();
            originalConfig.setEnableRecommendTags(new RpmEnableRecommendTagsConfig(false, recommendsConstantSet
            ));
        } else {
            boolean recommendsConstantSet = originalConfig.getEnableRecommendTags().isEnabled();
            originalConfig.setEnableRecommendTags(new RpmEnableRecommendTagsConfig(true, recommendsConstantSet
            ));
        }
        return originalConfig;
    }
}
