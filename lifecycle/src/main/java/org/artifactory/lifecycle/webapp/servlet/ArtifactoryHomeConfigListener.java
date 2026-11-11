/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
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

import com.jfrog.sysconf.SysConfigConstants;
import jakarta.annotation.Nonnull;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ServerTracingScope;

/**
 * @author yoavl
 */
@Slf4j
public class ArtifactoryHomeConfigListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            ServletContext servletContext = event.getServletContext();
            // Start a tracing span for the Artifactory startup
            ArtifactoryTracer tracer = getArtifactoryTracer();
            try (ServerTracingScope ignored = tracer.startSpanOnCurrentThread("art-init-config")) {
                ArtifactoryHome artifactoryHome = getArtifactoryHome(servletContext);
                initArtifactoryHome(artifactoryHome);
                BasicConfigurationManager basicConfigManagers = getBasicConfigManagers(tracer, artifactoryHome);
                initBasicConfigManager(servletContext, basicConfigManagers);
            }
        } catch (RuntimeException e) {
            log.error("Failed initializing Home. Caught exception: ", e);
            throw e;
        }
    }

    private void initBasicConfigManager(ServletContext servletContext, BasicConfigurationManager basicConfigManagers) {
        basicConfigManagers.initialize();
        basicConfigManagers.addServletAttributes(servletContext);
        basicConfigManagers.inheritInitParamsAsConstantValues(servletContext);
    }

    @Nonnull
    ArtifactoryTracer getArtifactoryTracer() {
        return new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer());
    }

    @NonNull
    BasicConfigurationManager getBasicConfigManagers(@NonNull ArtifactoryTracer artifactoryTracer,
            @NonNull ArtifactoryHome artifactoryHome) {
        return new BasicConfigurationManager(artifactoryHome, artifactoryTracer);
    }

    @NonNull
    ArtifactoryHome getArtifactoryHome(@NonNull ServletContext servletContext) {
        String homeDir = getHomeDirForTesting(servletContext);
        ArtifactoryHome artifactoryHome = new ArtifactoryHome(homeDir);
        log.info("Resolved Home: '{}", artifactoryHome.getHomeDir().getAbsolutePath());
        ArtifactoryHome.bind(artifactoryHome);
        return artifactoryHome;
    }

    void initArtifactoryHome(@NonNull ArtifactoryHome artifactoryHome) {
        artifactoryHome.initDBProperties();
        artifactoryHome.initArtifactorySystemProperties();
    }

    static String getHomeDirForTesting(ServletContext servletContext) {
        return servletContext.getInitParameter(SysConfigConstants.KEY_JF_PRODUCT_HOME);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // nop
    }
}