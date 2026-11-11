package org.artifactory.support.core.collectors.logs;

import lombok.NonNull;
import org.jfrog.support.common.config.SystemLogsConfiguration;
import org.jfrog.support.common.core.collectors.DefaultFilesMatcher;

import java.nio.file.Path;

/**
 * @author Tamir Hadad
 */
public class ArtifactoryLogsMatcher extends DefaultFilesMatcher {
    private static final String ARTIFACTORY_LOG_PATTERN = "artifactory-.+\\.log(\\.gz)?";
    private static final String FILEBEAT_LOG_PATTERN = "filebeat\\.log?.*";

    @Override
    public boolean isMatch(@NonNull Path filePath, @NonNull SystemLogsConfiguration configuration) {
        return super.isMatch(filePath, configuration)
                && isFileNameMatches(filePath);
    }

    private boolean isFileNameMatches(@NonNull Path filePath) {
        String fileName = filePath.getFileName().toString();
        return fileName.matches(ARTIFACTORY_LOG_PATTERN) ||
                fileName.matches(FILEBEAT_LOG_PATTERN);
    }
}
