package org.artifactory.environment.converter.utils;

import lombok.experimental.UtilityClass;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.slf4j.Logger;

import jakarta.validation.constraints.NotNull;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.getRevisionFromVersion;

/**
 * @author Tamir Hadad
 */
@UtilityClass
public class VersionAllowedUtil {

    public static boolean isUpgradeAllowed(ArtifactoryHome artifactoryHome,
            ArtifactoryServiceVersionProvider minVersion,
            @NotNull Logger log) {
        String versionUpgradeProp = ConstantValues.allowAnyUpgrade.getString(artifactoryHome);
        if (isNotBlank(versionUpgradeProp)) {
            try {
                //User specified ok to upgrade with downtime?
                ArtifactoryServiceVersion allowUpgrade = ArtifactoryServiceVersionProvider.get(versionUpgradeProp);
                if (allowUpgrade == null) {
                    allowUpgrade = ArtifactoryServiceVersionProvider
                            .get(versionUpgradeProp, getRevisionFromVersion(versionUpgradeProp));
                }
                if (allowUpgrade != null && minVersion.get().beforeOrEqual(allowUpgrade)) {
                    return true;
                }
            } catch (Exception e) {
                String err = "Can't parse property upgrade.allowAnyUpgrade.forVersion " + versionUpgradeProp +
                        ", aborting upgrade.";
                log.error(err);
                log.error("", e);
            }
        }
        return false;
    }

}
