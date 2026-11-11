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

package org.artifactory.webapp.main;

import ch.qos.logback.classic.util.ContextInitializer;
import com.jfrog.sysconf.SysConfigConstants;
import com.jfrog.sysconf.test.YamlSysConfig;
import com.jfrog.test.LogTestUtils;
import lombok.NonNull;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.rewrite.RewriteValve;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.webapp.WebappUtils;
import org.jfrog.common.YamlUtils;
import org.jfrog.common.platform.test.helpers.RouterProcess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static com.jfrog.sysconf.SysConfigConstants.SYS_JF_PRODUCT_HOME;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_SERVICE_NAME;
import static org.artifactory.log.logback.ArtifactoryLoggerConfig.ARTIFACTORY_USAGE_DIR_PROP;
import static org.artifactory.log.logback.ArtifactoryLoggerConfig.LOG_DIR_PROP;
import static org.jfrog.config.home.JFrogHome.DATA_FOLDER;
import static org.jfrog.config.home.JFrogHome.ETC_FOLDER;
import static org.jfrog.config.home.JFrogHome.LOG_FOLDER;
import static org.jfrog.config.home.JFrogHome.SYSTEM_YAML_FILENAME;
import static org.jfrog.config.home.JFrogHome.VAR_FOLDER;
import static org.jfrog.config.home.JFrogHome.WORK_FOLDER;

/**
 * Starts an Artifactory OSS instance with Tomcat embedded.
 *
 * @author yoavl
 */
public class StartArtifactoryDev {
    private static final String ACCESS_DEBUG_PORT_PROP = "jfrog.access.debug.port";
    private static final String ACCESS_DEFAULT_DEBUG_PORT = "6006";
    private static final int ROUTER_INTERNAL_PORT = 8046;
    private static final int ROUTER_EXTERNAL_PORT = 8082;
    private static final int ARTIFACTORY_MAINTENANCE_PORT = 8091;

    private static final int FRONTEND_SERVICE_PORT = 8087;
    private static final int METADATA_SERVICE_PORT = 8086;
    private static final int EVENT_SERVICE_PORT = 8061;
    private static final int OBSERVABILITY_SERVICE_PORT = 8036;
    private static final int JFCONNECT_SERVICE_PORT = 8030;

    public static void main(String... args) throws IOException {
        File devArtHome = getArtifactoryDevHome(args);
        setupLogback(devArtHome);
        setSystemProperties(devArtHome);
        File devEtcDir = WebappUtils.getDevEtcFolder();
        WebappUtils.updateMimetypes(devEtcDir);
        WebappUtils.copyNewerDevResources(devEtcDir, devArtHome, true);

        startAccessProcess(devArtHome);

        Tomcat tomcat = startTomcat(devArtHome);
        tomcat.getServer().await();
    }

    static Tomcat startTomcat(File devArtHome) {
        return startTomcat(devArtHome, 8080);
    }

    static Tomcat startTomcat(File devArtHome, int port) {
        return startTomcat(devArtHome, port, ARTIFACTORY_MAINTENANCE_PORT);
    }

    static Tomcat startTomcatWithRouter(File devArtHome) {
        return startTomcat(devArtHome, 8081);
    }

    static Tomcat startTomcat(File devArtHome, int port, int maintenancePort) {
        Tomcat tomcat = null;
        try {
            tomcat = createTomcatServer(devArtHome);
            Connector defaultConnector = createDefaultConnector(port);
            tomcat.getService().addConnector(defaultConnector);
            tomcat.setConnector(defaultConnector);
            configureMaintenanceConnector(tomcat, maintenancePort);
            addRewriteRules(tomcat);
            tomcat.start();
            return tomcat;
        } catch (Exception e) {
            System.err.println("Could not start the Tomcat server: " + e);
            if (tomcat != null) {
                try {
                    tomcat.stop();
                } catch (Exception e1) {
                    System.err.println("Unable to stop the Tomcat server: " + e1);
                }
            }
            throw new RuntimeException(e);
        }
    }

    private static Tomcat createTomcatServer(@NonNull File devArtHome) throws IOException {
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(
                Paths.get(devArtHome.getAbsolutePath(), VAR_FOLDER, WORK_FOLDER, ARTIFACTORY_SERVICE_NAME,
                                "tomcat")
                        .toFile().getAbsolutePath());
        // must serve root for the rewrite to process that.

        String webappsAbsolutePath = WebappUtils.getWebappRoot(devArtHome, false, true)
                .getAbsolutePath();
        Path rootDir = Paths.get(webappsAbsolutePath, "ROOT");
        Files.createDirectories(rootDir);

        tomcat.addContext(tomcat.getHost(), "", rootDir.toString());
        Context context = tomcat.addWebapp("/artifactory", webappsAbsolutePath);
        File tomcatResources = WebappUtils.getTomcatResources();
        ((StandardContext) context).setDefaultWebXml(new File(tomcatResources, "web.xml").getAbsolutePath());
        ((StandardContext) context)
                .setDefaultContextXml(new File(tomcatResources, "context.xml").getAbsolutePath());
        context.setConfigFile(new File(tomcatResources, "artifactory.xml").toURI().toURL());
        context.setLoader(new SimpleLoader());
        return tomcat;
    }

    private static Connector createDefaultConnector(int port) {
        Connector defaultConnector = new Connector();
        defaultConnector.setPort(Integer.parseInt(System.getProperty("server.port", port + "")));
        defaultConnector.setProperty("address", "127.0.0.1");
        defaultConnector.setProperty("relaxedPathChars", "[]");
        defaultConnector.setProperty("relaxedQueryChars", "[]");
        defaultConnector.setEncodedSolidusHandling("decode");
        return defaultConnector;
    }

    private static void configureMaintenanceConnector(Tomcat tomcat, int maintenancePort) {
        Connector maintenanceConnector = new Connector("HTTP/1.1");
        maintenanceConnector.setPort(maintenancePort);
        maintenanceConnector.setProperty("address", "localhost");
        maintenanceConnector.setProperty("maxThreads", "5");
        maintenanceConnector.setProperty("acceptCount", "5");
        tomcat.setConnector(maintenanceConnector);
    }

    private static void addRewriteRules(Tomcat tomcat) throws Exception {
        String rules =
                " RewriteRule ^/v2/(.*)$ /artifactory/v2/$1 [L]\n" +
                        " RewriteRule ^/.well-known/terraform.json /artifactory/api/terraform/.well-known/terraform" +
                        ".json [L]\n" +
                        " RewriteRule ^/$ /artifactory/ [L]\n";
        RewriteValve valve = new RewriteValve();
        tomcat.getEngine().getPipeline().addValve(valve);

        valve.setConfiguration(rules);
    }

    static void setSystemProperties(File devArtHome) {
        System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty(ConstantValues.dev.getPropertyName(), "true");
        System.setProperty(ConstantValues.pluginScriptsRefreshIntervalSecs.getPropertyName(), "3");
        if (System.getProperty("artifactory.addons.disabled") == null) {
            System.setProperty("artifactory.addons.disabled", "aol");
        }
        System.setProperty(ACCESS_DEBUG_PORT_PROP, ACCESS_DEFAULT_DEBUG_PORT);
        System.setProperty("jf.shared.node.ip", "127.0.0.1");
        System.setProperty("staging.mode", "true"); // license checks against staging store
        System.setProperty(SYS_JF_PRODUCT_HOME, devArtHome.getAbsolutePath());
        System.setProperty(ConstantValues.termLicenseDynamicExpiryDateEnabled.getPropertyName(), "false");
    }

    //Logback must be partially setup early since some calls in this class cause loggers to write before context is
    //initialized. Don't use any code that requires logging before calling this method
    private static void setupLogback(File devArtHome) {
        LogTestUtils.suppressIllegalAccessWarning();
        URL logbackXmlUrl = null;
        boolean isFileProtocol = false;
        try {
            Enumeration<URL> resources =
                    Thread.currentThread().getContextClassLoader().getResources("dev/etc/logback.xml");
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                // Prefer file protocol (for development), but keep first JAR resource as fallback
                if (resource.getProtocol().equals("file")) {
                    logbackXmlUrl = resource;
                    isFileProtocol = true;
                    break;
                } else if (logbackXmlUrl == null) {
                    logbackXmlUrl = resource;
                }
            }
            if (logbackXmlUrl == null) {
                throw new RuntimeException("Could not find logback configuration file");
            }
            System.out.println("Logback configuration file: " + logbackXmlUrl);
            File logbackFile;
            if (isFileProtocol) {
                logbackFile = new File(logbackXmlUrl.toURI());
            } else {
                // Resource is in a JAR, extract it to a temporary file
                Path tempFile = Files.createTempFile("logback", ".xml");
                try (InputStream inputStream = logbackXmlUrl.openStream()) {
                    Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                logbackFile = tempFile.toFile();
                logbackFile.deleteOnExit();
            }
            System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, logbackFile.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String hardcodedLogsDir = Paths
                .get(devArtHome.getAbsolutePath(), VAR_FOLDER, LOG_FOLDER, ARTIFACTORY_SERVICE_NAME)
                .toFile().getAbsolutePath();
        System.setProperty(LOG_DIR_PROP, hardcodedLogsDir);
        String hardcodedUsageLogsDir = Paths
                .get(devArtHome.getAbsolutePath(), VAR_FOLDER, DATA_FOLDER, ARTIFACTORY_SERVICE_NAME, "usage")
                .toFile().getAbsolutePath();
        System.setProperty(ARTIFACTORY_USAGE_DIR_PROP, hardcodedUsageLogsDir);

    }

    public static File getSystemYamlPath(File artifactoryHome) {
        return new File(Paths.get(artifactoryHome.getAbsolutePath(), VAR_FOLDER, ETC_FOLDER, SYSTEM_YAML_FILENAME)
                .toUri());
    }

    static File getArtifactoryDevHome(String[] args) throws IOException {
        String prefix = args.length == 0 ? ".." : args[0];
        String homeDir = System.getProperty(SYS_JF_PRODUCT_HOME);
        File devArtHome = new File(homeDir != null ? homeDir : prefix + "/devenv/.artifactory").getCanonicalFile();
        if (!devArtHome.exists() && !devArtHome.mkdirs()) {
            throw new IllegalStateException("Failed to create home dir: " + devArtHome.getAbsolutePath());
        }
        // this is done here to prevent any other calls to the logger (happened multiple times!)
        setupLogback(devArtHome);
        return devArtHome;
    }

    /**
     * @param devAccessHome equivalent of {@link SysConfigConstants#KEY_JF_PRODUCT_HOME}
     */
    private static void startAccessProcess(File devAccessHome) {
        startAccessProcess(0, false, devAccessHome, -1);
    }

    /**
     * @param devAccessHome equivalent of {@link SysConfigConstants#KEY_JF_PRODUCT_HOME}
     */
    static AccessProcess startAccessProcess(int port, boolean waitForAccess, File devAccessHome, int routerPort) {
        if (!Boolean.getBoolean("access.process.skip")) {
            // start Access server. re-use existing service is detected on the same port
            // the process will register a shutdown hook so we will let the JVM kill it
            assertAccessHomePath(devAccessHome);
            AccessProcess.AccessProcessConfig accessConfig = new AccessProcess.AccessProcessConfig(devAccessHome)
                    .port(port);
            if (routerPort > 0) {
                accessConfig.contextPath("/access");
                accessConfig.routerPort(routerPort);
            }
            String debugPortProperty = System.getProperty(ACCESS_DEBUG_PORT_PROP);
            if (StringUtils.isNotBlank(debugPortProperty)) {
                int debugPort = (port > 0) ? Integer.parseInt(debugPortProperty) + port - 8040 :
                        Integer.parseInt(debugPortProperty);

                accessConfig = accessConfig.debug(debugPort, false);
            }
            AccessProcess accessProcess = new AccessProcess(accessConfig);
            if (waitForAccess) {
                accessProcess.startAndWait();
            } else {
                accessProcess.start();
            }
            if (routerPort == -1) {
                System.setProperty(ConstantValues.accessClientServerUrlOverride.getPropertyName(),
                        accessProcess.getAccessUrl());
            }
            return accessProcess;
        }
        return null;
    }

    static void startMetadataProcess(File productHome) {
        try {
            startMetadataProcess(productHome, METADATA_SERVICE_PORT, ROUTER_INTERNAL_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void startMetadataProcess(File productHome, int port, int routerPort) throws IOException {
        MetadataServerProcess metadataServerProcess = new MetadataServerProcess(port, routerPort,
                productHome);
        metadataServerProcess.start();
    }

    static void startEventServerProcess(File productHome) {
        try {
            startEventServerProcess(productHome, EVENT_SERVICE_PORT, ROUTER_INTERNAL_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void startEventServerProcess(File productHome, int port, int routerPort) throws IOException {
        EventServerProcess eventServerProcess = new EventServerProcess(port, routerPort, productHome);
        eventServerProcess.start();
    }

    static void startJFConnectProcess(File productHome) {
        try {
            startJFConnectProcess(productHome, JFCONNECT_SERVICE_PORT, ROUTER_INTERNAL_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void startJFConnectProcess(File productHome, int port, int routerPort) throws IOException {
        JFConnectServerProcess jfConnectServerProcess = new JFConnectServerProcess(port, routerPort, productHome);
        jfConnectServerProcess.start();
    }

    static void startObservabilityProcess(File productHome) {
        try {
            startObservabilityProcess(productHome, OBSERVABILITY_SERVICE_PORT, ROUTER_INTERNAL_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void startObservabilityProcess(File productHome, int port, int routerPort) throws IOException {
        ObservabilityServerProcess obsService = new ObservabilityServerProcess(port, routerPort, productHome);
        obsService.start();
    }

    static void startFrontendProcess(File productHome) {
        try {
            startFrontendProcess(productHome, FRONTEND_SERVICE_PORT, ROUTER_INTERNAL_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void startFrontendProcess(File productHome, int port, int routerPort) throws IOException {
        FrontendServerProcess frontendServerProcess = new FrontendServerProcess(port, routerPort,
                productHome);
        frontendServerProcess.start();
    }

    static void startRouterProcess(AccessProcess accessProcess, File artDev) throws IOException {
        startRouterProcess(accessProcess, artDev, ROUTER_INTERNAL_PORT, ROUTER_EXTERNAL_PORT);
    }

    static void startRouterProcess(AccessProcess accessProcess, File artDev, int internalPort, int externalPort)
            throws IOException {

        Map<String, String> env = new HashMap<>();
        if ("true".equals(System.getProperty("jf.federation.multiTenant.enabled"))) {
            env.put("JF_ROUTER_MULTITENANT_TENANTID", "t1");
            env.put("JF_ROUTER_MULTITENANT_SERVICES_JFRTFS_SERVICEURL", "http://localhost:9082");
            env.put("JF_ROUTER_MULTITENANT_SERVICES_JFRTFS_MATCH",
                    "/artifactory/service/rtfs/(.*),/rtfs/(.*),/com\\.jfrog\\.rtfs\\.v1\\..*");
        }

        if ("true".equals(System.getProperty("artifactory.jfbus.enabled"))) {
            env.put("JF_ROUTER_MULTITENANT_TENANTID", "t1");
            env.put("JF_ROUTER_MULTITENANT_SERVICES_JFBUS_SERVICEURL", "http://localhost:8057");
            env.put("JF_ROUTER_MULTITENANT_SERVICES_JFBUS_MATCH",
                    "/artifactory/service/jfbus/(.*),/jfbus/(.*),/com\\.jfrog\\.jfbus\\.v1\\..*");
        }

        RouterProcess.RouterProcessConfig routerProcessConfigBuilder = RouterProcess.RouterProcessConfig.builder()
                .accessUrl(accessProcess.getAccessUrl())
                .internalPort(internalPort)
                .externalPort(externalPort)
                .home(artDev.getAbsolutePath())
                .joinKey(AccessProcess.TEST_JFROG_JOIN_KEY)
                .additionalEnv(env)
                .nodeId(getNodeIdFromSystemYaml(artDev))
                .executablePath(WebappUtils.getRouterExecutable(SystemUtils.IS_OS_MAC).getAbsolutePath())
                .build();
        RouterProcess routerProcess = new RouterProcess(routerProcessConfigBuilder);
        routerProcess.start();
    }

    private static String getNodeIdFromSystemYaml(@NonNull File artDev) {
        File systemYaml = getSystemYamlPath(artDev);
        if (!systemYaml.exists()) {
            return null;
        }
        YamlSysConfig yamlSysConfig = YamlUtils.getInstance().readValue(systemYaml, YamlSysConfig.class);
        return yamlSysConfig.getShared().getNode().getId();
    }

    private static void assertAccessHomePath(@NonNull File devAccessHome) {
        if (!devAccessHome.exists() && !devAccessHome.mkdirs()) {
            throw new IllegalStateException("Failed to create home dir: " + devAccessHome.getAbsolutePath());
        }
    }

    private static class SimpleLoader extends WebappLoader {
        @Override
        public ClassLoader getClassLoader() {
            return ClassLoader.getSystemClassLoader();
        }
    }
}
