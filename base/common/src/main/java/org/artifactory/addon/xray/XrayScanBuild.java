package org.artifactory.addon.xray;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class XrayScanBuild {

    private String buildName;
    private String buildNumber;
    private String buildRepo;
    private Long buildStarted;

    private String artifactoryId;
    private String context;

    public XrayScanBuild(
            String buildName,
            String buildNumber,
            String buildRepo,
            Long buildStarted,
            String context) {
        this.buildName = buildName;
        this.buildNumber = buildNumber;
        this.buildRepo = buildRepo;
        this.buildStarted = buildStarted;
        this.context = context;
    }
}
