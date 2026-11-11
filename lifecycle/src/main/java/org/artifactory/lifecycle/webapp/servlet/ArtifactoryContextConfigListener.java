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

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.jfrog.base.JFrogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.edition.ArtifactoryEdition;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.file.lock.ArtifactoryLockFile;
import org.artifactory.log.logback.ArtifactoryLoggerConfig;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ServerTracingScope;
import org.artifactory.spring.SpringConfigPaths;
import org.artifactory.spring.SpringConfigResourceLoader;
import org.artifactory.util.ExceptionUtils;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.TomcatListeningDetector;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.webapp.servlet.DelayedInit;
import org.jfrog.common.ResourceUtils;
import org.jfrog.common.logging.logback.servlet.LogbackConfigManager;
import org.jfrog.common.logging.logback.servlet.LogbackContextSelector;
import org.jfrog.common.logging.logback.servlet.LoggerConfigInfo;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.util.ClassUtils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.SessionTrackingMode;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.jfrog.sysconf.SysConfigConstants.KEY_JF_PRODUCT_HOME;
import static org.apache.commons.lang3.time.DurationFormatUtils.formatPeriod;
import static org.artifactory.api.context.ArtifactoryContext.CTX_ATTR_LOGBACK_CONFIG_MANAGER;
import static org.artifactory.lifecycle.webapp.servlet.LogbackConfigListener.getRefreshInterval;

/**
 * @author yoavl
 */
@Slf4j
public class ArtifactoryContextConfigListener implements ServletContextListener {

    private ArtifactoryLockFile artifactoryLockFile;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            log.info("Artifactory context initialization started for context: {}",
                    event.getServletContext().getInitParameter(KEY_JF_PRODUCT_HOME));
            init(event);
        } catch (RuntimeException e) {
            String reason = StringUtils.isNotBlank(e.getMessage()) ? e.getMessage() : "";
            log.error("Failed initializing Artifactory context: {}", reason);
            log.debug("Failed initializing Artifactory context: ", e);
            throw e;
        }
    }

    private void init(ServletContextEvent event) {
        final ServletContext servletContext = event.getServletContext();

        setSessionTrackingMode(servletContext);

        ArtifactoryTracer artifactoryTracer = (ArtifactoryTracer) servletContext
                .getAttribute(ArtifactoryHome.ARTIFACTORY_TRACER_OBJ);

        final Thread initThread = new Thread("art-init") {
            boolean success = true;

            @SuppressWarnings({"unchecked"})
            @Override
            public void run() {
                log.info("Artifactory initialization thread started for home {}",
                        servletContext.getInitParameter(KEY_JF_PRODUCT_HOME));
                try (ServerTracingScope ignored = artifactoryTracer.startSpanOnCurrentThread("art-init")) {
                    try {
                        //Build a partial config, since we expect the logger-context to exit in the selector cache by
                        //only contextId
                        String contextId = HttpUtils.getContextId(servletContext);
                        ArtifactoryHome home = getArtifactoryHome(servletContext);
                        configureLogback(contextId, home, servletContext);
                        //No log field since needs to lazy initialize only after logback customization listener has run
                        configure(servletContext);

                        LogbackContextSelector.unbindConfig();
                    } catch (Exception e) {
                        log.error("Application could not be initialized: " +
                                  ExceptionUtils.getRootCause(e).getMessage(), e);
                        success = false;
                    } finally {
                        if (success) {
                            //Run the waiting filters
                            BlockingQueue<DelayedInit> waitingFiltersQueue = (BlockingQueue<DelayedInit>) servletContext
                                    .getAttribute(DelayedInit.APPLICATION_CONTEXT_LOCK_KEY);
                            List<DelayedInit> waitingInits = new ArrayList<>();
                            waitingFiltersQueue.drainTo(waitingInits);
                            long time = System.currentTimeMillis();
                            for (DelayedInit filter : waitingInits) {
                                try {
                                    filter.delayedInit();
                                } catch (ServletException e) {
                                    log.error("Could not init {}.", filter.getClass().getName(), e);
                                    success = false;
                                    break;
                                }
                            }
                            log.debug("Filters Delayed Init took: " + (System.currentTimeMillis() - time) + " millis");
                        }
                        //Remove the lock and open the app to requests
                        servletContext.removeAttribute(DelayedInit.APPLICATION_CONTEXT_LOCK_KEY);
                    }
                }
            }
        };
        initThread.setDaemon(true);
        servletContext.setAttribute(DelayedInit.APPLICATION_CONTEXT_LOCK_KEY, new LinkedBlockingQueue<DelayedInit>());
        initThread.start();
        if (Boolean.getBoolean("artifactory.init.useServletContext")) {
            try {
                log.info("Waiting for servlet context initialization ...");
                initThread.join();
            } catch (InterruptedException e) {
                log.error("Artifactory initialization thread got interrupted", e);
            }
        }
    }

    /**
     * Disable sessionId in URL (Servlet 3.0 containers) by setting the session tracking mode to SessionTrackingMode
     * .COOKIE
     * For Servlet container < 3.0 we use different method (for tomcat 6 we use custom context.xml and for jetty
     * there is a custom jetty.xml file).
     */
    private void setSessionTrackingMode(ServletContext servletContext) {
        if (servletContext.getMajorVersion() < 3) {
            // some tests are still using
            return;
        }
        // We cannot use ConstantValue.enableURLSessionId.getBoolean() since ArtifactoryHome is not binded yet.
        ArtifactoryHome artifactoryHome = getArtifactoryHome(servletContext);

        if (artifactoryHome.getArtifactoryProperties().getBooleanProperty(ConstantValues.supportUrlSessionTracking)) {
            log.debug("Skipping setting session tracking mode to COOKIE, enableURLSessionId flag it on.");
            return;
        }

        try {
            servletContext.setSessionTrackingModes(Sets.newHashSet(SessionTrackingMode.COOKIE));
            log.debug("Successfully set session tracking mode to COOKIE");
        } catch (Exception e) {
            log.warn("Failed to set session tracking mode: {}", e.getMessage());
        }
    }

    private ArtifactoryHome getArtifactoryHome(ServletContext servletContext) {
        ArtifactoryHome artifactoryHome =
                (ArtifactoryHome) servletContext.getAttribute(ArtifactoryHome.SERVLET_CTX_ATTR);
        if (artifactoryHome == null) {
            throw new IllegalStateException("Artifactory home not initialized.");
        }
        return artifactoryHome;
    }

    private void configureLogback(String contextId, ArtifactoryHome home, ServletContext servletContext) {
        LogbackConfigManager configManager =
                (LogbackConfigManager) servletContext.getAttribute(CTX_ATTR_LOGBACK_CONFIG_MANAGER);
        try {
            //Watchdog thread must be bound to home since it calls its listeners later on (i.e. sumo service) and they
            // need the context.
            ArtifactoryHome.bind(home);
            configManager.startWatchDog(getRefreshInterval(servletContext));
        } finally {
            ArtifactoryHome.unbind();
        }

        if (!ConstantValues.isDevOrTest(home)) {
            configManager.relaxWebComponentLogLevel();
        }
        LoggerConfigInfo configInfo =
                new ArtifactoryLoggerConfig(contextId, home.getLogDir(), home.getDataDir(), home.getLogbackConfig());
        LogbackContextSelector.bindConfig(configInfo);
    }

    private void configure(ServletContext servletContext)
            throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, InstantiationException {
        long start = System.currentTimeMillis();

        BasicConfigurationManager bcm = new BasicConfigurationManager(servletContext);

        if (bcm.artifactoryHome == null) {
            throw new IllegalStateException("Artifactory home not initialized.");
        }
        logAsciiArt(bcm.artifactoryHome,
                bcm.versionProvider.getRunningServiceVersion(), bcm.versionProvider.getRunningProductVersion());
        try {
            ArtifactoryHome.bind(bcm.artifactoryHome);

            if (log.isDebugEnabled()) {
                log.debug("Artifactory is listening on port {}", TomcatListeningDetector.detectMainListeningPort());
            }

            //todo consider moving to org.artifactory.lifecycle.webapp.servlet.ArtifactoryHomeConfigListener
            //.contextInitialized()
            if (bcm.artifactoryHome.isHaConfigured()) {
                log.debug("Not using Artifactory lock file on HA environment");
            } else {
                File artifactoryLockFile = bcm.artifactoryHome.getArtifactoryLockFile();
                FileUtils.forceMkdir(artifactoryLockFile.getParentFile());
                this.artifactoryLockFile = new ArtifactoryLockFile(artifactoryLockFile);

                this.artifactoryLockFile.tryLock();
            }
            Class<?> contextClass = ClassUtils.forName(
                    "org.artifactory.spring.ArtifactoryApplicationContext", ClassUtils.getDefaultClassLoader());
            Constructor<?> constructor = contextClass.
                    getConstructor(String.class, SpringConfigPaths.class, BasicConfigurationManager.class,
                            ServletContext.class);
            //Construct the context name based on the context path
            //(will not work with multiple servlet containers on the same vm!)
            String contextUniqueName = HttpUtils.getContextId(servletContext);
            SpringConfigPaths springConfigPaths = SpringConfigResourceLoader.getConfigurationPaths(bcm.artifactoryHome);
            constructor.newInstance(contextUniqueName, springConfigPaths, bcm,
                    servletContext);
            log.info("{} ({}) service initialization completed in {} seconds. Listening on port: {}",
                    JFrogService.ARTIFACTORY.getServiceName(), JFrogService.ARTIFACTORY.getServiceId(),
                    (formatPeriod(start, System.currentTimeMillis(), "s.S")),
                    TomcatListeningDetector.detectMainListeningPort());
            String startTime =
                    String.format("%-17s", (formatPeriod(start, System.currentTimeMillis(), "s.S")) + " seconds");
            log.debug("Artifactory successfully started ({})", startTime);
        } finally {
            ArtifactoryHome.unbind();
        }

    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        AbstractApplicationContext context = (AbstractApplicationContext) event.getServletContext().getAttribute(
                ArtifactoryContext.APPLICATION_CONTEXT_KEY);
        try {
            log.debug("Context shutdown started");
            if (context != null) {
                if (context instanceof ArtifactoryContext) {
                    AddonsManager addonsManager = ((ArtifactoryContext) context).beanForType(AddonsManager.class);
                    addonsManager.addonByType(HaCommonAddon.class).shutdown();
                }
                context.close();
            }
            if (artifactoryLockFile != null) {
                artifactoryLockFile.release();
            }
            log.debug("Context shutdown Finished");
        } finally {
            event.getServletContext().removeAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY);
            event.getServletContext().removeAttribute(ArtifactoryHome.SERVLET_CTX_ATTR);
        }
    }

    private void logAsciiArt(ArtifactoryHome artifactoryHome,
            CompoundVersionDetails serviceVersionDetails, CompoundVersionDetails productVersionDetails) {
        String message = null;
        try {
            message = buildEditionMessage(artifactoryHome, serviceVersionDetails, productVersionDetails);
        } catch (Exception e) {
            log.warn("Failed to detect edition {}", e.getMessage());
            log.debug("Failed to detect edition {}", e.getMessage(), e);
        }

        // artifactory home location
        message += " Artifactory Home: '" + artifactoryHome.getHomeDir().getAbsolutePath() + "'\n";

        //optionally log HA properties
        HaNodeProperties haNodeProperties = artifactoryHome.getHaNodeProperties();
        if (haNodeProperties != null) {
            if (StringUtils.isNotBlank(haNodeProperties.getClusterDataDir())) {
                message += " Artifactory data dir: '" + haNodeProperties.getClusterDataDir() + "'\n";
            }
            message += " Node ID: '" + haNodeProperties.getServerId() + "'\n";
        }
        log.info(message);
    }

    String buildEditionMessage(ArtifactoryHome artifactoryHome,
            CompoundVersionDetails serviceVersionDetails, CompoundVersionDetails productVersionDetails)
            throws Exception {
        ArtifactoryEdition runningEdition = ArtifactoryEdition.detect(artifactoryHome);
        InputStream resource = ResourceUtils.getResource("/ascii-editions.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, Charsets.UTF_8))) {
            String edition;
            while ((edition = reader.readLine()) != null) {
                if (!runningEdition.name().equalsIgnoreCase(edition)) {
                    // skip 8 lines to get to the next chunk
                    for (int i = 0; i < 8; i++) {
                        reader.readLine();
                    }
                } else {
                    StringBuilder sb = new StringBuilder("\n");
                    // height of the ascii art with no modifications
                    for (int i = 0; i < 6; i++) {
                        sb.append(reader.readLine()).append("\n");
                    }
                    // line 7 prepend the version
                    String versionLine = reader.readLine();

                    String versionStr = "";
                    if (ArtifactoryEdition.AOL != runningEdition) {
                        versionStr = " Version:  " + serviceVersionDetails.getVersionName();
                    }
                    sb.append(versionStr).append(versionLine.substring(versionStr.length())).append("\n");

                    // line 8 prepend the revision
                    String revisionLine = reader.readLine();
                    String revisionStr = " Revision: " + serviceVersionDetails.getRevision();
                    sb.append(revisionStr).append(revisionLine.substring(revisionStr.length())).append("\n");

                    if (ArtifactoryEdition.AOL != runningEdition) {
                        sb.append(" Product Version:  ").append(productVersionDetails.getVersionName()).append("\n");
                    }
                    sb.append(" Product Revision: ").append(productVersionDetails.getRevision()).append("\n");
                    return sb.toString();
                }
            }
        }
        throw new IllegalArgumentException("Couldn't find matching art for " + runningEdition);
    }
}
