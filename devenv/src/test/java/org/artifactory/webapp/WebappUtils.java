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

package org.artifactory.webapp;

import com.jfrog.sysconf.SysConfigConstants;
import jakarta.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.SystemUtils;
import org.apache.tomcat.util.descriptor.web.WebXml;
import org.apache.tomcat.util.descriptor.web.WebXmlParser;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.ResourceUtils;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter.META_INF_DEFAULT_PATH;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_SERVICE_NAME;
import static org.artifactory.common.home.ArtifactoryHome.MIME_TYPES_FILE_NAME;
import static org.jfrog.config.home.JFrogHome.ETC_FOLDER;
import static org.jfrog.config.home.JFrogHome.MASTER_KEY_FILENAME;
import static org.jfrog.config.home.JFrogHome.SECURITY_FOLDER;
import static org.jfrog.config.home.JFrogHome.VAR_FOLDER;

/**
 * @author Fred Simon on 9/12/16.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class WebappUtils {

    private static final String MASTER_KEY = "0c1a1554553d487466687b339cd85f3d";
    private static final File[] rootLocations;
    private static final String WEB_INF_LOCATION = "WEB-INF" + File.separator + "lib";
    private static final String AARCH64 = SystemUtils.OS_ARCH.contains("aarch64") ? "-arm64" : "-amd64";

    static {
        File currentFolder = new File(".").getAbsoluteFile();
        File userDir = SystemUtils.getUserDir().getAbsoluteFile();
        rootLocations = new File[] {
                currentFolder,
                currentFolder.getParentFile(),
                currentFolder.getParentFile().getParentFile(),
                currentFolder.getParentFile().getParentFile().getParentFile(),
                currentFolder.getParentFile().getParentFile().getParentFile().getParentFile(),
                currentFolder.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile(),
                userDir,
                userDir.getParentFile(),
                // TODO: [by fsi] use good naming standards like ~/projects to look into
                new File(userDir, "projects")
        };
    }

    public static File getWebappRoot(File artHome, boolean integTest, boolean addArtifactoryHomeParam)
            throws IOException {
        // Find out if Pro or OSS version by using classpath contains Pro license manager
        boolean isPro = ResourceUtils.resourceExists("/org/artifactory/addon/LicenseProvider.class");

        String toFindDescription;
        String[] folderNames;
        if (!integTest) {
            // Use only source code for UI dev
            toFindDescription = "Webapp Source Code root";
            folderNames = new String[] {
                    "artifactory-oss/web/war/src/main/webapp"
            };
        } else if (isPro) {
            toFindDescription = "Webapp target root";
            folderNames = new String[] {
                    "artifactory-pro/war/target/artifactory",
                    "war/target/artifactory",
                    "itest-pro/target/webapp",
                    "itest-online/target/webapp",
                    "webapp",
            };
        } else {
            toFindDescription = "Webapp Source OSS root";
            folderNames = new String[] {
                    "artifactory-oss/web/war/src/main/webapp",
                    "web/war/src/main/webapp",
                    "itest/target/webapp",
                    "war/target/artifactory-conan-ce"
            };
        }
        File targetWebappRoot = find(toFindDescription, folderNames);
        // Copy the correct web.xml dev-env file only if not running in Jenkins (there the one in target is used)
        File webXmlFile;
        if (isPro) {
            webXmlFile = find("Pro web.xml file", new String[] {
                    "devenv/src/main/webapp/WEB-INF/web.xml",
                    "artifactory-pro/devenv/src/main/webapp/WEB-INF/web.xml",
                    "itest-pro/target/webapp/WEB-INF/web.xml"
            });
        } else {
            webXmlFile = find("OSS web.xml file", new String[] {
                    "web/war/src/main/webconf/WEB-INF/web.xml",
                    "artifactory-oss/web/war/src/main/webconf/WEB-INF/web.xml",
                    "itest/target/webapp/WEB-INF/web.xml",
                    "war/src/main/webapp/WEB-INF/web.xml"
            });
        }
        updateWebXml(artHome, addArtifactoryHomeParam, targetWebappRoot, webXmlFile);

        if (integTest) {
            if (artHome == null) {
                throw new IllegalArgumentException(
                        "Cannot create webapp folder in unit test without an artifactory home folder");
            }
            File webapp = new File(artHome, "webapp");
            IOFileFilter fileFilter = new WebappCopyFileFilter(targetWebappRoot, webapp);
            fileFilter = FileFilterUtils.makeSVNAware(fileFilter);
            FileUtils.copyDirectory(targetWebappRoot, webapp, fileFilter, true);
            return webapp;
        }

        return targetWebappRoot;
    }

    private static void updateWebXml(File artHome, boolean addArtifactoryHomeParam, File targetWebappRoot,
            File webXmlFile) throws IOException {
        File destFile = new File(targetWebappRoot, "WEB-INF/web.xml");
        if (addArtifactoryHomeParam) {
            customizeWebXml(webXmlFile, destFile, artHome);
        } else {
            if (!webXmlFile.equals(destFile)) {
                FileUtils.copyFile(webXmlFile, destFile);
            }
        }
    }

    private static void customizeWebXml(File origWebXmlFile, File destWebXmlFile, File artHome) throws IOException {
        WebXmlParser parser = new WebXmlParser(false, false, true);
        WebXml webXml = new WebXml();
        try (InputStream in = new FileInputStream(origWebXmlFile); Reader reader = new InputStreamReader(in)) {
            InputSource inputSource = new InputSource(reader);
            parser.parseWebXml(inputSource, webXml, false);
        }
        webXml.addContextParam(SysConfigConstants.KEY_JF_PRODUCT_HOME, artHome.getAbsolutePath());
        webXml.addContextParam("logbackRefreshInterval", "50");    // shorten the log refresh interval for testing
        String webXmlString = webXml.toXml();
        FileUtils.write(destWebXmlFile, webXmlString, StandardCharsets.UTF_8);
    }

    public static File getDevEtcFolder() {
        String[] folderNames = new String[] {
                "artifactory-oss/devenv/src/test/resources/dev/etc",
                "devenv/src/test/resources/dev/etc"
        };
        return find("Dev etc folder", folderNames);
    }

    public static File getAccessStandaloneJar() {
        String[] folderNames = new String[] {
                "target/access-application-standalone.jar",
                "artifactory-pro/itest/target/access-application-standalone.jar",
                "artifactory-pro/itest-pro/target/access-application-standalone.jar",
                "artifactory-pro/conan-ce/conan-ce-itest/target/access-application-standalone.jar",
                "artifactory-pro/container-registry/jcr-itest/target/access-application-standalone.jar",
                "artifactory-pro/container-registry/online/itest-online/target/access-application-standalone.jar",
                "artifactory-pro/online/itest-online/target/access-application-standalone.jar",
                "artifactory-oss/devenv/target/access-standalone/access-application-standalone.jar",
        };
        return find("Access Standalone Jar", folderNames);
    }

    public static File getMetadataApiExecutable(boolean macOS) {
        List<String> pathPrefixes = List.of("artifactory-pro/itest/target/metadata-extract/metadata/bin/metadata-",
                "artifactory-pro/itest-pro/target/metadata-extract/metadata/bin/metadata-",
                "artifactory-oss/devenv/target/metadata-extract/metadata/bin/metadata-");
        return find("Metadata API Executable", getSearchPaths(pathPrefixes, macOS));
    }

    public static File getEventServerExecutable(boolean macOS) {
        return find("EventServer Executable",
                getSearchPaths(List.of("artifactory-pro/devenv/target/event-extract/event/bin/jf-event-"), macOS));
    }

    public static File getJFConnectServerExecutable(boolean macOS) {
        return find("JFConnect Executable", getSearchPaths(
                List.of("artifactory-pro/devenv/target/jfconnect-extract/jfconnect/bin/jf-connect-"), macOS));
    }

    public static File getJFObservabilityServerExecutable(boolean macOS) {
        List<String> pathPrefixes =
                List.of("artifactory-pro/itest/target/observability-extract/observability/bin/jf-observability-",
                        "artifactory-pro/itest-pro/target/observability-extract/observability/bin/jf-observability-",
                        "artifactory-oss/devenv/target/observability-extract/observability/bin/jf-observability-");
        return find("Observability Executable", getSearchPaths(pathPrefixes, macOS));
    }

    public static File getFrontendExecutable(boolean macOS) {
        String[] filePaths;
        String os = macOS ? "darwin" : "linux";
        filePaths = new String[] {
                "artifactory-pro/itest/target/frontend-extract/frontend/bin/server/dist/bundle.js",
                "artifactory-pro/itest-pro/target/frontend-extract/frontend/bin/server/dist/bundle.js",
                "artifactory-oss/devenv/target/" + os + "/frontend-extract/frontend/bin/server/dist/bundle.js"
        };
        return find("Frontend Executable", filePaths);
    }

    public static File getRouterExecutable(boolean macOS) {
        List<String> pathPrefixes = List.of(
                "artifactory-pro/itest/target/router-extract/router/bin/jf-router-",
                "artifactory-pro/itest-pro/target/router-extract/router/bin/jf-router-",
                "artifactory-oss/devenv/target/router-extract/router/bin/jf-router-");
        return find("Router API Executable", getSearchPaths(pathPrefixes, macOS));
    }

    public static File getNodeExecutable(boolean macOS) {
        String os = macOS ? "darwin" : "linux";
        String[] filePaths = {
                "artifactory-oss/devenv/target/" + os + "/node-extract/node-darwin-x64/bin/node"
        };
        return find("NodeJS app", filePaths);
    }

    @Nonnull
    private static String[] getSearchPaths(List<String> pathPrefixes, boolean macOS) {
        List<String> paths = new ArrayList<>();
        String os = macOS ? "darwin" : "linux";
        for (String pathPrefix : pathPrefixes) {
            if (macOS) {
                paths.add(pathPrefix + os + "-arm64");
                paths.add(pathPrefix + os + "-amd64");
            } else {
                paths.add(pathPrefix + os + AARCH64);
            }
        }
        return paths.toArray(new String[0]);
    }

    public static File getTomcatResources() {
        String[] folderNames =
                new String[] {"artifactory-oss/devenv/src/main/install/misc/tomcat"};
        return find("Tomcat resources folder", folderNames);
    }

    private static File getArtifactoryLicense() {
        String[] folderNames = new String[] {
                "artifactory-pro/addons-manager/src/test/resources/keys/ent-plus-1.lic"
        };
        return find("Artifactory License File", folderNames);
    }

    public static File find(String toFind, String[] folderNames) {
        for (String folderName : folderNames) {
            for (File rootLocation : rootLocations) {
                File file = new File(rootLocation, folderName);
                if (file.exists()) {
                    System.out.printf("Found %s under %s%n", toFind, file.getAbsolutePath());
                    return file;
                }
            }
        }

        StringBuilder lookedInto = new StringBuilder("Trying to find " + toFind + " under:\n");
        for (File rootLocation : rootLocations) {
            for (String folderName : folderNames) {
                lookedInto.append(new File(rootLocation, folderName).getAbsolutePath()).append("\n");
            }
        }
        throw new RuntimeException(lookedInto + "FAILED! Try executing Maven with the dev profile:\n\n" +
                "mvnw install -Pdev -f artifactory-oss/devenv/ && " +
                "mvnw install -Pdev -f artifactory-pro/devenv/\n\n");
    }

    public static void updateMimetypes(File devEtcDir) {
        File defaultMimeTypes = ResourceUtils.getResourceAsFile(META_INF_DEFAULT_PATH + MIME_TYPES_FILE_NAME);
        File devMimeTypes = new File(devEtcDir, "mimetypes.xml");
        if (!devMimeTypes.exists() || defaultMimeTypes.lastModified() > devMimeTypes.lastModified()) {
            // override developer mimetypes file with newer default mimetypes file
            try {
                FileUtils.copyFile(defaultMimeTypes, devMimeTypes);
            } catch (IOException e) {
                System.err.println("Failed to copy default mime types file: " + e.getMessage());
            }
        }
    }

    /**
     * Copy newer files from the standalone dir to the working artifactory home dir
     *
     * @param isMaster -> secondary nodes get config from the database when they start
     */
    public static File copyNewerDevResources(File devEtcDir, File artHome, boolean isMaster) throws IOException {
        File etcDir = Paths.get(artHome.getAbsolutePath(), VAR_FOLDER, ETC_FOLDER, ARTIFACTORY_SERVICE_NAME).toFile();
        IOFileFilter fileFilter = new EtcCopyFileFilter(devEtcDir, etcDir, isMaster);
        fileFilter = FileFilterUtils.makeSVNAware(fileFilter);
        FileUtils.copyDirectory(devEtcDir, etcDir, fileFilter, true);
        updateDefaultMimetypes(etcDir);

        /*
         * If the bootstrap already exists, it means it's not the first startup, so don't keep the original config file
         * or the etc folder will flood with bootstrap files
         */
        if (isMaster) {
            Files.deleteIfExists(new File(etcDir, ArtifactoryHome.ARTIFACTORY_CONFIG_BOOTSTRAP_FILE).toPath());
        }
        copyMasterKey(artHome);
        createProductProperties(artHome);
        return etcDir;
    }

    public static void copyLicenseIfMissing(File home) {
        File licenseFile = new File(
                Paths.get(home.getAbsolutePath(), VAR_FOLDER, ETC_FOLDER, "artifactory", "artifactory.lic").toUri());
        try {
            if (!licenseFile.exists()) {
                File licenseToCopy = getArtifactoryLicense();
                FileUtils.copyFile(licenseToCopy, licenseFile);
            }
        } catch (IOException e) {
            log.warn("Unable to create artifactory.lic.");
        }
    }

    public static void overwriteConfigDescriptorWithXrayEnabled(File devArtHome) throws IOException {
        File sourceFile = ResourceUtils.getResourceAsFile("/xray/artifactory.config.import.xml");
        File etcDir =
                Paths.get(devArtHome.getAbsolutePath(), VAR_FOLDER, ETC_FOLDER, ARTIFACTORY_SERVICE_NAME).toFile();
        File destFile = new File(etcDir, sourceFile.getName());
        FileUtils.copyFile(sourceFile, destFile);
    }

    private static void copyMasterKey(File homeDir) throws IOException {
        File securityDir = Paths.get(homeDir.getAbsolutePath(), VAR_FOLDER, ETC_FOLDER, SECURITY_FOLDER).toFile();
        Files.createDirectories(securityDir.toPath());
        File masterKeyFile = new File(securityDir, MASTER_KEY_FILENAME);
        if (!masterKeyFile.exists()) {
            Files.write(masterKeyFile.toPath(), MASTER_KEY.getBytes());
        }
    }

    /**
     * The artifactory.product.version.properties file is normally created by the installers script at startup.
     * This creates a dummy file for dev environment.
     */
    private static void createProductProperties(File homeDir) {
        File productPropertiesFile =
                Paths.get(homeDir.getAbsolutePath(), "app", "artifactory.product.version.properties").toFile();
        try {
            if (!productPropertiesFile.exists()) {
                String productPropertiesContent =
                        "artifactory.product.version=" + ArtifactoryServiceVersion.getCurrent().getVersion() + "\n" +
                                "artifactory.product.revision=" + Integer.MAX_VALUE + "\n" +
                                "artifactory.product.timestamp=LOCAL\n" +
                                "artifactory.product.buildNumber=LOCAL";
                FileUtils.writeByteArrayToFile(productPropertiesFile, productPropertiesContent.getBytes());
            }
        } catch (IOException e) {
            log.warn("Unable to create artifactory.product.version.properties.");
        }
    }

    private static void updateDefaultMimetypes(File devEtcDir) {
        File defaultMimeTypes = ResourceUtils.getResourceAsFile(META_INF_DEFAULT_PATH + MIME_TYPES_FILE_NAME);
        File devMimeTypes = new File(devEtcDir, "mimetypes.xml");
        if (!devMimeTypes.exists() || defaultMimeTypes.lastModified() > devMimeTypes.lastModified()) {
            // override developer mimetypes file with newer default mimetypes file
            try {
                FileUtils.copyFile(defaultMimeTypes, devMimeTypes);
            } catch (IOException e) {
                System.err.println("Failed to copy default mime types file: " + e.getMessage());
            }
        }
    }

    private static class EtcCopyFileFilter extends AbstractFileFilter {
        private final File srcDir;
        private final File destDir;
        private final boolean isMasterNode;

        EtcCopyFileFilter(File srcDir, File destDir, boolean isMasterNode) {
            this.srcDir = srcDir;
            this.destDir = destDir;
            this.isMasterNode = isMasterNode;
        }

        @Override
        public boolean accept(File srcFile) {
            if (srcFile.isDirectory()) {
                return true;    // don't exclude directories
            }
            // Jetty.xml always true
            String srcFileName = srcFile.getName();
            if ("jetty.xml".equals(srcFileName)) {
                return true;
            }
            String relativePath = PathUtils.getRelativePath(srcDir.getAbsolutePath(), srcFile.getAbsolutePath());
            File destFile = new File(destDir, relativePath);
            if (!destFile.exists() || srcFile.lastModified() > destFile.lastModified()) {
                // These are config files that should be fetched from db by nodes
                if (isMasterNode) {
                    return true;
                } else {
                    return !srcFileName.equalsIgnoreCase("artifactory.config.xml")
                            && !srcFileName.equalsIgnoreCase("artifactory.system.properties")
                            && !srcFileName.equalsIgnoreCase("mimetypes.xml");
                }
            }
            return false;
        }
    }

    private static class WebappCopyFileFilter extends AbstractFileFilter {
        private final File srcDir;
        private final File destDir;

        WebappCopyFileFilter(File srcDir, File destDir) {
            this.srcDir = srcDir;
            this.destDir = destDir;
        }

        @Override
        public boolean accept(File srcFile) {
            if (srcFile.isDirectory()) {
                if (srcFile.getAbsolutePath().endsWith(WEB_INF_LOCATION)) {
                    // WEB-INF lib no copy
                    return false;
                }
                // webapp UI not needed in integration tests
                return !srcFile.getAbsolutePath().endsWith("webapp");// don't exclude directories
            }
            if (srcFile.getName().endsWith(".jar")) {
                if (srcFile.getParentFile().getAbsolutePath().endsWith(WEB_INF_LOCATION)) {
                    // WEB-INF lib no copy
                    return false;
                }
            }
            String relativePath = PathUtils.getRelativePath(srcDir.getAbsolutePath(), srcFile.getAbsolutePath());
            File destFile = new File(destDir, relativePath);
            return !destFile.exists() || srcFile.lastModified() > destFile.lastModified();
        }
    }
}
