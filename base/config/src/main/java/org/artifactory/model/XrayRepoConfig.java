package org.artifactory.model;

import lombok.Data;
import lombok.Setter;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import static org.artifactory.descriptor.repo.XrayRepoConfigDescriptor.DEFAULT_DATA_TTL;
import static org.artifactory.repo.xray.XrayNotAvailableMode.FAIL_CLOSE;
import static org.artifactory.repo.xray.XrayNotAvailableMode.FAIL_OPEN;
import static org.artifactory.repo.xray.XrayNotAvailableMode.USE_SYSTEM_PROPERTY;

/**
 * Xray specific configuration for repositories
 *
 * @author Dan Feldman
 */
@Data
@GenerateDiffFunction
public class XrayRepoConfig {

    private boolean enabled = false;

    /**
     * The field will indicate for how long Xray data for artifacts in this repository shall be saved.
     */
    private int dataTtl = DEFAULT_DATA_TTL;

    /**
     * The field will indicate how repository should act when xray is down.
     */
    @Setter
    private XrayNotAvailableMode xrayNotAvailableMode = null;

    public void xrayNotAvailableModeFromString(String xrayNotAvailableMode) {
        switch (xrayNotAvailableMode.toLowerCase()) {
            case "fail_close" -> this.setXrayNotAvailableMode(FAIL_CLOSE);
            case "fail_open" -> this.setXrayNotAvailableMode(FAIL_OPEN);
            default -> this.setXrayNotAvailableMode(USE_SYSTEM_PROPERTY);
        }
    }
}
