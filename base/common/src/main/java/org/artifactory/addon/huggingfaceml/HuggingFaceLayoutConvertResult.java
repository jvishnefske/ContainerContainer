package org.artifactory.addon.huggingfaceml;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Volodymyr Shulga
 * This class is used to aggregate all the information about the result of the conversion process. The result is
 * stored as a metric in prometheus and can be used to monitor the conversion process.
 */
@Getter
@Builder
public class HuggingFaceLayoutConvertResult {
    private String repoKey;
    private HuggingFaceLayoutConvertStatus status;
    private long convertedArtifactsCount;
    private long duration;
    private String message;

    enum HuggingFaceLayoutConvertStatus {
        SUCCESS, FAILED
    }
}
