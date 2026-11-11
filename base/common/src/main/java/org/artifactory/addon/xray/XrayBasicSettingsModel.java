package org.artifactory.addon.xray;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author BarakH
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class XrayBasicSettingsModel {

    private boolean xrayEnabled;

    /**
     * Use {@link #allowOperationsXrayUnavailable}
     */
    private boolean allowDownloadsXrayUnavailable;
    private boolean allowOperationsXrayUnavailable;

    private boolean allowBlockedArtifactsDownload;
    private Integer blockUnscannedTimeoutSeconds;
    private Integer blockUnfinishedScansTimeoutSeconds;
}