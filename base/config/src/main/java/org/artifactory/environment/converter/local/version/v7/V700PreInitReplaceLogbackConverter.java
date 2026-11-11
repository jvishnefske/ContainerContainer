package org.artifactory.environment.converter.local.version.v7;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.jfrog.common.FileUtils.TIMESTAMP_PLACEHOLDER;

/**
 * Backup and replace the logback to the base logback of 7.0.0.
 *
 * @author Nadav Yogev
 */
public class V700PreInitReplaceLogbackConverter implements BasicEnvironmentConverter {
    private static final Logger log = LoggerFactory.getLogger(V700PreInitReplaceLogbackConverter.class);

    @Override
    public boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().beforeOrEqual(ArtifactoryServiceVersionProvider.v700m001.get()) &&
                target.getVersion().afterOrEqual(ArtifactoryServiceVersionProvider.v700m001.get());
    }

    @Override
    public void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source, CompoundVersionDetails target) {
        log.info("Starting logback conversion --> Backup up old log dir reference");
        File logbackConfig = artifactoryHome.getLogbackConfig();
        if (!logbackConfig.exists()) {
            log.debug("logback.xml does not exist. continuing...");
            return;
        }
        try {
            FileUtils.renameFileToTemplate(logbackConfig, "logback.xml.date." + TIMESTAMP_PLACEHOLDER + ".backup");
        } catch (IOException e) {
            log.error("Unable to backup logback file before converting.", e);
            throw new IllegalStateException(e.getCause());
        }
    }

    @Override
    public void assertConversionPreconditions(ArtifactoryHome home) throws ConverterPreconditionException {
        assertTargetFilePermissions(home.getLogbackConfig());
    }
}
