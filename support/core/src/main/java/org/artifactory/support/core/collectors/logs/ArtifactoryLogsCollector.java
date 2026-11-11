package org.artifactory.support.core.collectors.logs;

import lombok.NonNull;
import org.jfrog.support.common.core.collectors.LogsCollector;

import java.io.File;

/**
 * @author Tamir Hadad
 */
public class ArtifactoryLogsCollector extends LogsCollector {

    private File logDir;

    public ArtifactoryLogsCollector(@NonNull File logDir) {
        super();
        this.logDir = logDir;
        this.setFilesMatcher(new ArtifactoryLogsMatcher());
    }

    @Override
    @NonNull
    protected File getLogsDirectory() {
        return logDir;
    }
}