package org.artifactory.environment.converter.local.version.v1;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FileUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Removes the legacy Mission Control plugins after the upgrade to V7.
 *
 * @author Uriah Levy
 */
public class RemoveMissionControlPluginsConverter implements BasicEnvironmentConverter {
    private static final Logger log = LoggerFactory.getLogger(RemoveMissionControlPluginsConverter.class);
    static final ImmutableSet<String> MC_PLUGIN_FILENAMES = ImmutableSet
            .of("propertySetsConfig.groovy", "haClusterDump.groovy", "httpSsoConfig.groovy",
                    "repoLayoutsConfig.groovy", "ldapGroupsConfig.groovy", "internalUser.groovy",
                    "ldapSettingsConfig.groovy", "pluginsConfig.groovy", "proxiesConfig.groovy",
                    "requestRouting.groovy");
    private String GROOVY_EXTENSION = ".groovy";

    @Override
    public boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(ArtifactoryServiceVersionProvider.v700m001.get());
    }

    @Override
    public void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source, CompoundVersionDetails target) {
        log.info("Entering Mission Control Plugins removal converter");
        File pluginsDir = artifactoryHome.getPluginsDir();
        File[] groovyFiles = pluginsDir.listFiles((dir, name) -> name.endsWith(GROOVY_EXTENSION));
        int removedCounter = 0;
        if (groovyFiles != null) {
            log.debug("Found {} Groovy Plugin files", groovyFiles.length);
            for (File groovyFile : groovyFiles) {
                if (MC_PLUGIN_FILENAMES.contains(groovyFile.getName())) {
                    if (groovyFile.exists()) {
                        log.debug("Deleting '{}'...", groovyFile.getAbsolutePath());
                        FileUtils.deleteQuietly(groovyFile);
                        log.info("Deleted '{}'", groovyFile.getAbsolutePath());
                        removedCounter++;
                    }
                }
            }
        }
        log.info("Finished Mission Control Plugins removal converter. Removed {} plugins", removedCounter);
    }

    @Override
    public void assertConversionPreconditions(ArtifactoryHome home) throws ConverterPreconditionException {
        // noop
    }
}
