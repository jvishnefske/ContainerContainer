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

package org.artifactory.lifecycle.webapp.servlet;

import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysLayout;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter;
import org.artifactory.log.logback.ArtifactoryLoggerConfig;
import org.artifactory.util.HttpUtils;
import org.jfrog.common.logging.BootstrapLogger;
import org.jfrog.common.logging.logback.servlet.LogbackConfigManager;
import org.jfrog.common.logging.logback.servlet.LoggerConfigInfo;
import org.jfrog.config.metadata.MetaInfFile;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.io.File;

import static org.artifactory.api.context.ArtifactoryContext.CTX_ATTR_LOGBACK_CONFIG_MANAGER;
import static org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter.META_INF_DEFAULT_PATH;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_SERVICE_NAME;
import static org.artifactory.common.home.ArtifactoryHome.LOGBACK_CONFIG_FILE_NAME;

/**
 * Configured logback with the config file from etc directory.
 *
 * @author Yossi Shaul
 * @author Yoav Landman
 */
public class LogbackConfigListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext servletContext = event.getServletContext();
        LoggerConfigInfo configInfo = createLoggerConfigInfo(servletContext);
        LogbackConfigManager configManager = new LogbackConfigManager(configInfo);
        servletContext.setAttribute(CTX_ATTR_LOGBACK_CONFIG_MANAGER, configManager);
        configManager.configureLogbackContext();
    }

    private LoggerConfigInfo createLoggerConfigInfo(ServletContext servletContext) {
        SysLayout preInitLayout = createPreInitLayout(servletContext);
        BootstrapLogger.init(ARTIFACTORY_SERVICE_NAME, preInitLayout.getProductLog());
        File logbackConfigFile = createLogbackConfigFile(preInitLayout);
        return new ArtifactoryLoggerConfig(HttpUtils.getContextId(servletContext), preInitLayout.getProductLog(),
                preInitLayout.getProductData(), logbackConfigFile);
    }

    private SysLayout createPreInitLayout(ServletContext servletContext) {
        //Use SysConfig to resolve homeDir, then SysLayout for the service dir and file
        File homedirFile = resolveHomeDir(servletContext);
        return new SysLayout(homedirFile, ARTIFACTORY_SERVICE_NAME);
    }

    File resolveHomeDir(ServletContext servletContext) {
        String homeDir = ArtifactoryHomeConfigListener.getHomeDirForTesting(servletContext);
        return StringUtils.isNotBlank(homeDir) ? new File(homeDir) : SysConfig.detectAbsoluteHomeDir();
    }

    /**
     * Originally {@link ArtifactoryConfigurationAdapter}'s responsibility, since logback is configured at the first
     * step of the application's init this is the place where we must create logback.xml under the etc dir.
     */
    private File createLogbackConfigFile(SysLayout preInitLayout) {
        //Ensure logback.xml is present under var/etc/artifactory/logback.xml
        File serviceEtcDir = preInitLayout.getServiceEtc();
        if (!serviceEtcDir.mkdirs() && !serviceEtcDir.exists()) {
            throw new IllegalStateException(
                    "Failed to init log configuration, cannot create directory " + serviceEtcDir.getAbsolutePath());
        }
        File logbackConfigFile = new File(serviceEtcDir, LOGBACK_CONFIG_FILE_NAME);
        new MetaInfFile(META_INF_DEFAULT_PATH + LOGBACK_CONFIG_FILE_NAME, logbackConfigFile).create();
        return logbackConfigFile;
    }

    static Long getRefreshInterval(ServletContext servletContext) {
        Long refreshInterval = null;
        String intervalString = servletContext.getInitParameter("logbackRefreshInterval");
        if (intervalString != null) {
            try {
                refreshInterval = Long.parseLong(intervalString);
            } catch (NumberFormatException e) {
                BootstrapLogger.error("Failed to parse logbackRefreshInterval. Using default log refresh interval.");
            }
        }
        return refreshInterval;
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        ServletContext servletContext = servletContextEvent.getServletContext();
        LogbackConfigManager configManager =
                (LogbackConfigManager) servletContext.getAttribute(CTX_ATTR_LOGBACK_CONFIG_MANAGER);
        if (configManager != null) {
            configManager.destroy();
        }
    }
}
