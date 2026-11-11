/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.log.logback;

import com.jfrog.sysconf.SysLayout;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.jfrog.common.logging.logback.LogbackContextConfigurator;
import org.jfrog.common.logging.logback.servlet.LoggerConfigInfo;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.logging.Logger;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

/**
 * @author Yinon Avraham.
 */
public class ArtifactoryLoggerConfig extends LoggerConfigInfo {

    // Using basic logger here to print errors/warnings in case of failures
    private static final Logger logger = Logger.getLogger(ArtifactoryLoggerConfig.class.getName());
    public static final String LOG_DIR_PROP = "log.dir";
    private static final String LOG_DIR_ARCHIVED_PROP = "log.dir.archived";
    public static final String ARTIFACTORY_USAGE_DIR_PROP = "artifactory.usage.dir";
    private static final String ARTIFACTORY_USAGE_DIR_ARCHIVED_PROP = "artifactory.usage.dir.archived";

    private final File logDir;
    private final File dataDir;

    public ArtifactoryLoggerConfig(String contextId, File logDir, File dataDir, File logbackConfigFile) {
        super(contextId, logbackConfigFile);
        this.logDir = logDir;
        this.dataDir = dataDir;
    }

    @Override
    protected void configure(LogbackContextConfigurator configurator) {
        try {
            super.configure(configurator);
            String artifactoryUsageDir = getUsageDir(dataDir);
            File artifactoryUsageArchivedDir = Paths.get(artifactoryUsageDir, "archived").toFile();
            configurator
                    .property(ArtifactoryContext.CONTEXT_ID_PROP, normalizedContextId())
                    .property(LOG_DIR_PROP, logDir.getAbsolutePath())
                    .property(LOG_DIR_ARCHIVED_PROP, new File(logDir.getAbsolutePath(), "archived").getAbsolutePath())
                    .property(ARTIFACTORY_USAGE_DIR_PROP, artifactoryUsageDir)
                    .property(ARTIFACTORY_USAGE_DIR_ARCHIVED_PROP, artifactoryUsageArchivedDir.getAbsolutePath());
            // add custom logback properties to be used by logback (pattern/file names) if defined for testing
            String customProps = getTestEnvCustomProp();
            if (StringUtils.isNotBlank(customProps)) {
                String[] customParams = customProps.split(",");
                Arrays.stream(customParams).forEach(
                        p -> configurator.property(p.split("::")[0], p.split("::")[1])
                );
            }
        } catch (Exception e) {
            logger.severe("Failed to configure logback: " +
                            Arrays.toString(ExceptionUtils.getRootCauseStackTrace(e)));
        }
    }

    @NonNull
    public static String getUsageDir(@NonNull File dataDir) {
        String dataPath = dataDir.getAbsolutePath();
        return dataPath.endsWith("artifactory") ?
                Paths.get(dataPath, "usage").toFile().getAbsolutePath() :
                Paths.get(dataPath, "artifactory", "usage").toFile().getAbsolutePath();
    }

    private String normalizedContextId() {
        String contextId = trimToEmpty(getContextId());
        contextId = "artifactory".equalsIgnoreCase(contextId) ? "" : contextId + " ";
        return isBlank(contextId) ? "" : contextId.toLowerCase();
    }

    private String getTestEnvCustomProp() {
        // If this is a test env then system.properties is already in place and we can parse it.
        // Another safe assumption at this point is that homeDir is the parent of the parent of logDir (since
        //var/log/artifactory)
        File homeDir = logDir.getParentFile().getParentFile();
        if (homeDir == null || !homeDir.exists()) {
            logger.warning("Cannot find home dir. Not fetching any test env custom logback properties.");
            return "";
        }
        SysLayout layout = new SysLayout(homeDir, ArtifactoryHome.ARTIFACTORY_SERVICE_NAME);
        File systemProps = new File(layout.getServiceEtc(), ArtifactoryHome.ARTIFACTORY_SYSTEM_PROPERTIES_FILE);
        if (!systemProps.exists()) {
            logger.fine("artifactory.system.properties not found, not fetching any test env custom logback " +
                            "properties.");
            return "";
        }
        ArtifactorySystemProperties properties = new ArtifactorySystemProperties();
        properties.loadArtifactorySystemProperties(systemProps, null, null);
        return properties.getProperty("artifactory.test.logback.custom", "");
    }
}
