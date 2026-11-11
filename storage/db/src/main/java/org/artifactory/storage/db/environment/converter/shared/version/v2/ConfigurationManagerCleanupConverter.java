package org.artifactory.storage.db.environment.converter.shared.version.v2;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.environment.converter.BasicEnvironmentConverter;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.config.db.TemporaryDBChannel;
import org.jfrog.storage.util.DbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;

import static org.jfrog.config.ConfigurationManagerImpl.BASE_CONFIG_PREFIX;

/**
 * @author Uriah Levy
 */
public class ConfigurationManagerCleanupConverter implements BasicEnvironmentConverter {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationManagerCleanupConverter.class);
    private static final String ARTIFACTORY_PREFIX = "artifactory.";
    private Set<String> blackList = Set.of("artifactory.service_id", "artifactory.migration.conan.v2",
            "artifactory.config.xml");

    @Override
    public boolean isInterested(ArtifactoryHome home, CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(ArtifactoryServiceVersionProvider.v750m145.get());
    }

    @Override
    public void convert(ArtifactoryHome artifactoryHome, CompoundVersionDetails source, CompoundVersionDetails target) {
        TemporaryDBChannel temporaryDBChannel = new TemporaryDBChannel(artifactoryHome.getDBProperties());
        try {
            log.info("Starting to convert configuration-manager config names to new format");
            ArrayList<String> oldConfigNames = new ArrayList<>();
            collectOldConfigNames(temporaryDBChannel, oldConfigNames);
            log.info("Found {} config names to convert", oldConfigNames.size());
            oldConfigNames.forEach(oldName -> updateConfigName(temporaryDBChannel, oldName));
            log.info("Finished converting configuration-manager config names to new format");
        } finally {
            temporaryDBChannel.close();
        }
    }

    private void collectOldConfigNames(TemporaryDBChannel temporaryDBChannel, ArrayList<String> oldConfigNames) {
        ResultSet resultSet = null;
        try {
            resultSet = temporaryDBChannel.executeSelect("SELECT config_name FROM configs");
            while (resultSet.next()) {
                String oldConfigName = resultSet.getString("config_name");
                oldConfigNames.add(oldConfigName);
            }
        } catch (SQLException e) {
            String errorMessage = "Unable to query configs table";
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        } finally {
            DbUtils.close(resultSet);
        }
    }

    private void updateConfigName(TemporaryDBChannel temporaryDbChannel, String oldName) {
        String newConfigName = convertOldConfigNameToNewFormat(oldName);
        if (StringUtils.isBlank(newConfigName)) {
            log.info("Not updating {}", oldName);
            return;
        }
        log.info("Converting {} to {}", oldName, newConfigName);
        try {
            temporaryDbChannel.executeUpdate("UPDATE configs SET config_name = ? WHERE config_name = ?", newConfigName,
                    oldName);
            log.info("Converted {} to {}", oldName, newConfigName);
        } catch (SQLException e) {
            if (configExists(newConfigName, temporaryDbChannel)) {
                log.info("Config with name {} already exists, skipping conversion and deleting old config ({})",
                        newConfigName, oldName);
                tryToDeleteLeftoverConfig(temporaryDbChannel, oldName);
                return;
            }
            String errorMessage = "Unable to update configs table with new configuration name";
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }
    }

    @Override
    public void assertConversionPreconditions(ArtifactoryHome home) {
        // noop
    }

    @Nullable
    String convertOldConfigNameToNewFormat(String oldName) {
        if (!isValid(oldName)) {
            return null;
        }

        if ("artifactory.lic".equals(oldName)) {
            return BASE_CONFIG_PREFIX + "artifactory.lic";
        }
        if ("artifactory.cluster.license".equals(oldName)) {
            return BASE_CONFIG_PREFIX + "artifactory.cluster.license";
        }
        if ("artifactory.system.properties".equals(oldName)) {
            return BASE_CONFIG_PREFIX + "artifactory.system.properties";
        }
        if ("artifactory.mimeType".equals(oldName)) {
            return BASE_CONFIG_PREFIX + "mimetypes.xml";
        }

        String newFormat = "";
        if (oldName.startsWith(ARTIFACTORY_PREFIX)) {
            newFormat = StringUtils.replaceOnce(oldName, ARTIFACTORY_PREFIX, BASE_CONFIG_PREFIX);
        }
        if (newFormat.startsWith(BASE_CONFIG_PREFIX + "ui.")) {
            newFormat = StringUtils.replaceOnce(newFormat, BASE_CONFIG_PREFIX + "ui.",
                    BASE_CONFIG_PREFIX + "ui" + File.separator);
        }

        if (newFormat.startsWith(BASE_CONFIG_PREFIX + "security.")) {
            newFormat = StringUtils.replaceOnce(newFormat, BASE_CONFIG_PREFIX + "security.",
                    BASE_CONFIG_PREFIX + "security" + File.separator);
        }

        if (newFormat.startsWith(BASE_CONFIG_PREFIX + "plugin.")) {
            newFormat = StringUtils.replaceOnce(newFormat, BASE_CONFIG_PREFIX + "plugin.",
                    BASE_CONFIG_PREFIX + "plugins" + File.separator);
        }
        // Correct any leftover forward slashes from a previous Artifactory version to "\" on Windows when needed.
        newFormat = StringUtils.replace(newFormat, "/", File.separator);
        return newFormat;
    }

    private boolean isValid(String oldFormat) {
        return oldFormat.startsWith(ARTIFACTORY_PREFIX) && !blackList.contains(oldFormat);
    }

    private boolean configExists(String configName, TemporaryDBChannel temporaryDbChannel) {
        try (ResultSet resultSet = temporaryDbChannel.executeSelect("SELECT count(*) from configs where " +
                "config_name = ?", configName)) {
            if (resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
        } catch (SQLException e) {
            log.error("Unable to check if config exists due to: ", e);
        }
        return false;
    }

    private void tryToDeleteLeftoverConfig(TemporaryDBChannel temporaryDbChannel, String oldName) {
        try {
            log.info("Deleting old config blob ({}) from database", oldName);
            temporaryDbChannel.executeUpdate("DELETE from configs where config_name = ?", oldName);
        } catch (SQLException e) {
            log.error("Unable to delete old config - a newer one already exists, this is harmless but may " +
                    "require removing {} manually from the DB. The stack trace will be triggered for informational " +
                    "purposes.", oldName, e);
        }
    }
}
