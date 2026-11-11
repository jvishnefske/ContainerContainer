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

package org.artifactory.common.home;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigConstants;
import com.jfrog.sysconf.SysLayout;
import com.jfrog.sysconf.exception.SystemConfigurationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.xml.bind.JAXBException;
import lombok.NonNull;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.common.profile.PackageProfile;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.exception.ProductVersionNotFoundException;
import org.artifactory.mime.MimeTypes;
import org.artifactory.mime.MimeTypesReader;
import org.artifactory.permissions.PermissionsCacheType;
import org.artifactory.util.Files;
import org.artifactory.version.ArtifactoryVersionReader;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.config.Home;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.EncryptionWrapperFactory;
import org.jfrog.security.crypto.JFrogMasterKeyEncrypter;
import org.jfrog.security.crypto.PlainTextEncryptionWrapper;
import org.jfrog.security.file.SecurityFolderHelper;
import org.jfrog.security.wrapper.ArtifactoryEncryptionKeyFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.dgc.VMID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.jfrog.sysconf.SysLayout.BACKUP_DIR;
import static com.jfrog.sysconf.SysLayout.DATA_DIR;
import static com.jfrog.sysconf.SysLayout.ETC_DIR;
import static com.jfrog.sysconf.SysLayout.IMPORT_DIR;
import static com.jfrog.sysconf.SysLayout.SECURITY_DIR;
import static com.jfrog.sysconf.SysLayout.WORK_DIR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.artifactory.common.config.adapter.ArtifactoryConfigurationAdapter.META_INF_DEFAULT_PATH;

/**
 * Artifactory home.
 * Standard layout looks like:
 * <pre>
 * JF_PRODUCT_HOME {@link SysConfigConstants#KEY_JF_PRODUCT_HOME}
 *  ├── backup
 *  │   └── artifactory
 *  ├── data
 *  │   └── artifactory
 *  │       ├── derby
 *  │       ├── filestore
 *  │       ├── git
 *  │       ├── import
 *  │       └── tmp
 *  │           ├── artifactory-uploads
 *  │           └── work
 *  ├── etc
 *  │   ├── artifactory
 *  │   │   ├── artifactory.config.latest.xml
 *  │   │   ├── artifactory.properties
 *  │   │   ├── artifactory.system.properties
 *  │   │   ├── binarystore.xml
 *  │   │   ├── logback.xml
 *  │   │   ├── mimetypes.xml
 *  │   │   ├── conversion
 *  │   │   ├── plugins
 *  │   │   ├── security
 *  │   │   │   ├── access
 *  │   │   │   │   ├── access.admin.token
 *  │   │   │   │   ├── data
 *  │   │   │   │   └── keys
 *  │   │   │   │       └── root.crt
 *  │   │   │   └── artifactory.key
 *  │   │   └── ui
 *  │   └── security
 *  │       ├── join.key
 *  │       └── master.key
 *  ├── log
 *  │   ├── access-service.log
 *  │   ├── artifactory-access.log
 *  │   ├── artifactory-service.log
 *  │   ├── artifactory-binarystore.log
 *  │   ├── artifactory-event.log
 *  │   ├── artifactory-import-export.log
 *  │   ├── artifactory-request.log
 *  │   ├── artifactory-request-trace.log
 *  │   ├── artifactory-traffic.log
 *  │   ├── tomcat-catalina.log
 *  │   ├── tomcat-localhost.log
 *  │   └── archived
 *  │       ├── access-service-2019-15-05T10-34-07.000.log.gz
 *  │       └── artifactory-service-2019-15-05T10-34-07.000.log.gz
 *  └── work
 *      └── artifactory
 *          └── tomcat
 *              ├── ROOT
 *              ├── artifactory
 *              └── temp
 * </pre>
 *
 * @author yoavl
 */
public class ArtifactoryHome implements Home {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryHome.class);

    /**
     * Represents the env var passed on by the installer script, it points to the /var/ path under which all folders
     * of all microservices reside.
     * Each folder under var is of the form var/etc/artifactory/  ||  var/data/artifactory and so forth.
     */
    public static final String ARTIFACTORY_SERVICE_NAME = "artifactory";

    /**
     * Server ID in case of a non HA Artifactory node
     */
    public static final String ARTIFACTORY_PRO = "Artifactory";

    private static final String LOCK_FILENAME = ".lock";
    private static final String RELEASE_INIT_LOCK_MARKER_FILENAME = ".release.init.lock.marker";
    static final String FORCE_DUPLICATE_REPLICATION_CONVERT_MARKER =
            ".force.duplicate.replication.convert.marker";
    private static final String ARTIFACTORY_PROPERTIES_FILE = "artifactory.properties";
    private static final String ARTIFACTORY_PRODUCT_VERSION_PROPERTIES_FILE = "artifactory.product.version.properties";
    public static final String BINARY_STORE_FILE_NAME = "binarystore.xml";
    public static final String BINARY_STORE_GCP_CREDS_FILE_NAME = "gcp.credentials.json";
    public static final String LICENSE_FILE_NAME = "artifactory.lic";
    public static final String CLUSTER_LICENSE_FILE_NAME = "artifactory.cluster.license";
    public static final String ARTIFACTORY_KEY_FILENAME = "artifactory.key";
    public static final String ARTIFACTORY_KEY_DEFAULT_TEMP_FILE_NAME = "artifactory.tmp.key";
    public static final String COMMUNICATION_KEY_FILE_NAME = "communication.key"; //Deprecated!!
    public static final String COMMUNICATION_TOKEN_FILE_NAME = "communication.token"; //Deprecated
    public static final String ACCESS_ADMIN_TOKEN_FILE_NAME = "access.admin.token";
    public static final String SERVICE_ID_FILE_NAME = "service_id";
    public static final String CLUSTER_ID = "cluster.id";
    public static final String SERVLET_CTX_ATTR = "artifactory.home.obj";
    public static final String ARTIFACTORY_CONVERTER_OBJ = "artifactory.converter.manager.obj";
    public static final String ARTIFACTORY_VERSION_PROVIDER_OBJ = "artifactory.version.provider.obj";
    public static final String ARTIFACTORY_CONFIG_MANAGER_OBJ = "artifactory.config.manager.obj";
    public static final String IS_DATABASE_SCHEMA_NEWLY_CREATED = "artifactory.schema.newly.created.bool";
    public static final String ARTIFACTORY_TRACER_OBJ = "artifactory.tracer.obj";
    public static final String ARTIFACTORY_VERSION_HISTORY_SERVICE_OBJ = "artifactory.version.history.service.obj";
    public static final String ARTIFACTORY_HA_INIT_LOCK_OBJ = "artifactory.ha.init.lock.obj";
    public static final String ARTIFACTORY_HA_INIT_LOCK_KEY = "artifactory.ha.init.lock.key";
    public static final String ARTIFACTORY_CONFIG_FILE = "artifactory.config.xml";
    public static final String ARTIFACTORY_REPO_CONFIG_FILE = "artifactory.repository.config.json";
    public static final String ARTIFACTORY_REPO_CONFIG_IMPORT_FILE = "artifactory.repository.config.import.json";
    public static final String ARTIFACTORY_REPO_CONFIG_LATEST_FILE = "artifactory.repository.config.latest.json";
    public static final String ARTIFACTORY_REPO_CONFIG_BOOTSTRAP_FILE = "artifactory.repository.config.bootstrap.json";
    public static final String ARTIFACTORY_CONFIG_BOOTSTRAP_FILE = "artifactory.config.bootstrap.xml";
    public static final String ARTIFACTORY_SYSTEM_PROPERTIES_FILE = "artifactory.system.properties";
    public static final String LOGBACK_CONFIG_FILE_NAME = "logback.xml";
    public static final String MIME_TYPES_FILE_NAME = "mimetypes.xml";
    public static final String ARTIFACTORY_HA_NODE_PROPERTIES_FILE = "ha-node.properties";
    public static final String ACCESS_SUB_DIR = "access";
    public static final String ACCESS_KEYS_DIR_NAME = "keys";
    public static final String ACCESS_CLIENT_CREDS_FILE_NAME = "access.creds";
    public static final String MC_SUB_DIR = "mc";
    public static final String TRUSTED_KEYS_FILE_NAME = "trusted.keys.json";
    public static final String STORAGE_USAGE_FILE = "storage.usage.json";
    public static final String ARTIFACTORY_CONFIG_IMPORT_FILE = "artifactory.config.import.xml";

    private static final String ARTIFACTORY_CONFIG_LATEST_FILE = "artifactory.config.latest.xml";
    private static final String ARTIFACTORY_SAML_ENCRYPTED_ASSERTION_PUBLIC_KEY =
            "artifactory.saml.encrypted.assertion.public";
    private static final String ARTIFACTORY_SAML_ENCRYPTED_ASSERTION_PRIVATE_KEY =
            "artifactory.saml.encrypted.assertion.private";
    private static final String ARTIFACTORY_BOOTSTRAP_YAML_IMPORT_FILE = "artifactory.config.import.yml";
    private static final InheritableThreadLocal<ArtifactoryHome> current = new InheritableThreadLocal<>();
    private static final EncryptionWrapper DUMMY_WRAPPER = new PlainTextEncryptionWrapper();
    private static final String VM_HOST_ID = new VMID().toString();
    public static final String ARTIFACTORY_VERSION_PROPERTIES = "/META-INF/artifactory.version.properties";

    private final SysConfig sysConfig;
    private final SysLayout sysLayout;
    private final File homeDir;
    private final File appDir;
    private final File varDir;
    private File etcDir;
    private File dataDir;
    private File gitDir;
    private File importDir;
    private File securityDir;
    private File backupDir;
    private File tempWorkDir;
    private File tempWorkFullSyncDir;
    private File tempUploadDir;
    private File pluginsDir;
    private File logoDir;
    private File logDir;
    private File accessClientDir;
    private File externalConversionsDir;
    private MimeTypes mimeTypes;
    private HaNodeProperties haNodeProperties;
    private EncryptionWrapper artifactoryEncryptionWrapper;
    private EncryptionWrapper masterEncryptionWrapper;
    private JFrogMasterKeyEncrypter jFrogMasterKeyEncrypter;
    private ArtifactorySystemProperties artifactorySystemProperties;
    private ArtifactoryDbProperties dbProperties;
    private final String technicalServerName;

    /**
     * Creates new artifactory home object base on the optional home directory. If no home directory is supplied, it
     * is searched according to the system configuration priorities order as defined in {@link SysConfig}.
     *
     * @see SysConfig
     */
    public ArtifactoryHome(@Nullable String homeDirPath) {
        try {
            log.info("Creating Artifactory home at '{}'", homeDirPath);
            sysConfig = new SysConfig.Builder().withHome(homeDirPath).build();
            homeDir = sysConfig.getHomeDir().toFile().getAbsoluteFile();
            sysLayout = new SysLayout(homeDir, ARTIFACTORY_SERVICE_NAME);
            appDir = sysLayout.getProductApp();
            varDir = sysLayout.getProductVar();
            technicalServerName = sysConfig.get("shared.system.technicalServerName").orElse(null);
            FileUtils.forceMkdir(varDir);
            create();
        } catch (SystemConfigurationException e) {
            log.error("Can't resolve Artifactory var dir.", e);
            throw new IllegalStateException("Can't resolve Artifactory var dir --> " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Can't resolve Artifactory var dir --> " + e.getMessage(), e);
        }
    }

    public static void bind(ArtifactoryHome props) {
        current.set(props);
    }

    public static void unbind() {
        current.remove();
    }

    public static boolean isBound() {
        return current.get() != null;
    }

    @NonNull
    public static ArtifactoryHome get() {
        ArtifactoryHome home = current.get();
        if (home == null) {
            throw new IllegalStateException("Artifactory home is not bound to the current thread.");
        }
        return home;
    }

    private static void checkWritableDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory() || !dir.canWrite()) {
            String message = "Directory '" + dir.getAbsolutePath() + "' is not writable!";
            log.error(message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Checks the existence of the logback configuration file under the etc directory. If the file doesn't exist this
     * method will extract a default one from the war.
     */
    public File getLogbackConfig() {
        return new File(etcDir, LOGBACK_CONFIG_FILE_NAME);
    }

    public File getReleaseInitLockMarker() {
        return new File(etcDir, RELEASE_INIT_LOCK_MARKER_FILENAME);
    }

    public File getForceDuplicateReplicationConvertFile() {
        return new File(etcDir, FORCE_DUPLICATE_REPLICATION_CONVERT_MARKER);
    }

    public ArtifactorySystemProperties getArtifactoryProperties() {
        return artifactorySystemProperties;
    }

    public MimeTypes getMimeTypes() {
        return mimeTypes;
    }

    public SysConfig getSysConfig() {
        return sysConfig;
    }

    public SysLayout getSysLayout() {
        return sysLayout;
    }


    public File getHomeDir() {
        return homeDir;
    }

    public File getAppDir() {
        return appDir;
    }

    public File getVarDir() {
        return varDir;
    }

    public File getDataDir() {
        return dataDir;
    }

    public File getCacheDir() {
        String metadataCacheFolder = ConstantValues.dotCacheMetadataFolder.getString();
        return StringUtils.isBlank(metadataCacheFolder) ?
                new File(getDataDir().getPath(), ".cache") : new File(metadataCacheFolder);
    }

    public File getGitDir() {
        return gitDir;
    }

    @NonNull
    public File getImportDir() {
        return importDir;
    }

    public File getArtifactoryLockFile() {
        return new File(getDataDir(), LOCK_FILENAME);
    }

    @Override
    public File getEtcDir() {
        return etcDir;
    }

    @Override
    public File getSecurityDir() {
        return securityDir;
    }

    public File getLogDir() {
        return logDir;
    }

    public File getBackupDir() {
        return backupDir;
    }

    public File getTempWorkDir() {
        return tempWorkDir;
    }

    public File getTempWorkFullSyncDir() {
        return tempWorkFullSyncDir;
    }

    public File getTempUploadDir() {
        return tempUploadDir;
    }

    public File getExternalConversionsDir() {
        return externalConversionsDir;
    }

    @Override
    public File getPluginsDir() {
        return pluginsDir;
    }

    @Override
    public File getLogoDir() {
        return logoDir;
    }

    public File getAccessClientDir() {
        return accessClientDir;
    }

    public File getAccessAdminCredsFile() {
        return Paths.get(accessClientDir.getAbsolutePath(), ACCESS_KEYS_DIR_NAME, ACCESS_CLIENT_CREDS_FILE_NAME)
                .toFile();
    }

    public File getLicenseFile() {
        String licenseFileName = isHaConfigured() ? CLUSTER_LICENSE_FILE_NAME : LICENSE_FILE_NAME;
        return new File(getEtcDir(), licenseFileName);
    }

    public File getArtifactorySystemPropertiesFile() {
        return new File(getEtcDir(), ARTIFACTORY_SYSTEM_PROPERTIES_FILE);
    }

    public File getArtifactoryHaNodePropertiesFile() {
        //This method is called also before the 'etcDir' member is initialized - hence use "etc" explicitly
        return new File(getEtcDir(), ARTIFACTORY_HA_NODE_PROPERTIES_FILE);
    }

    public File getMimeTypesFile() {
        return new File(getEtcDir(), MIME_TYPES_FILE_NAME);
    }

    public File getBinaryStoreXmlFile() {
        return new File(getEtcDir(), BINARY_STORE_FILE_NAME);
    }

    public File getBinaryStoreGcpCredentialsFile() {
        return new File(getEtcDir(), BINARY_STORE_GCP_CREDS_FILE_NAME);
    }

    public File getArtifactoryConfigFile() {
        return new File(getEtcDir(), ARTIFACTORY_CONFIG_FILE);
    }

    public File getArtifactoryConfigLatestFile() {
        return new File(getEtcDir(), ARTIFACTORY_CONFIG_LATEST_FILE);
    }

    public File getArtifactoryConfigImportFile() {
        return new File(getEtcDir(), ARTIFACTORY_CONFIG_IMPORT_FILE);
    }

    public File getArtifactoryRepoConfigLatestFile() {
        return new File(getEtcDir(), ARTIFACTORY_REPO_CONFIG_LATEST_FILE);
    }

    public File getArtifactoryRepoConfigImportFile() {
        return new File(getEtcDir(), ARTIFACTORY_REPO_CONFIG_IMPORT_FILE);
    }

    public File getArtifactoryBootstrapYamlImportFile() {
        return new File(getEtcDir(), ARTIFACTORY_BOOTSTRAP_YAML_IMPORT_FILE);
    }

    public File getArtifactoryProductVersionPropertiesFile() {
        return new File(getAppDir(), ARTIFACTORY_PRODUCT_VERSION_PROPERTIES_FILE);
    }

    public File getArtifactoryPropertiesFile() {
        return new File(getEtcDir(), ARTIFACTORY_PROPERTIES_FILE);
    }

    public File getArtifactoryOldPropertiesFile() {
        return new File(getDataDir(), ARTIFACTORY_PROPERTIES_FILE);
    }

    public File getArtifactoryConfigBootstrapFile() {
        return new File(getEtcDir(), ARTIFACTORY_CONFIG_BOOTSTRAP_FILE);
    }

    public File getArtifactoryRepoConfigBootstrapFile() {
        return new File(getEtcDir(), ARTIFACTORY_REPO_CONFIG_BOOTSTRAP_FILE);
    }

    public File getArtifactoryConfigNewBootstrapFile() {
        return new File(getEtcDir(), "new_" + ArtifactoryHome.ARTIFACTORY_CONFIG_BOOTSTRAP_FILE);
    }

    public File getArtifactoryKey() {
        return new File(getSecurityDir(), ARTIFACTORY_KEY_FILENAME);
    }

    public File getAccessAdminTokenFile() {
        return new File(accessClientDir, ACCESS_ADMIN_TOKEN_FILE_NAME);
    }

    public static Path getPartialPathForAccessAdminToken() {
        return Paths.get(ETC_DIR, SECURITY_DIR, ACCESS_SUB_DIR, ACCESS_ADMIN_TOKEN_FILE_NAME);
    }

    public File getArtifactorySAMLEncryptedAssertionPublicKeyFile() {
        return new File(getSecurityDir(), ARTIFACTORY_SAML_ENCRYPTED_ASSERTION_PUBLIC_KEY);
    }

    public File getArtifactorySAMLEncryptedAssertionPrivateKeyFile() {
        return new File(getSecurityDir(), ARTIFACTORY_SAML_ENCRYPTED_ASSERTION_PRIVATE_KEY);
    }

    @Override
    public EncryptionWrapper getMasterEncryptionWrapper() {
        if (masterEncryptionWrapper == null) {
            String key = getMasterKey();
            masterEncryptionWrapper = EncryptionWrapperFactory.aesKeyWrapperFromString(key);
        }
        return masterEncryptionWrapper;
    }

    public EncryptionWrapper getArtifactoryEncryptionWrapper() {
        // Temporary fix for the HA master key synchronization issue. We always check if the key exist to ensure that
        // the dummy key will not get 'stuck' in the memory while another ha node re-created master encryption key
        File artifactoryKey = getArtifactoryKey();
        if ((artifactoryEncryptionWrapper == null ||
                artifactoryEncryptionWrapper instanceof PlainTextEncryptionWrapper) && artifactoryKey.exists()) {
            int numOfFallbackKeys = ConstantValues.securityArtifactoryKeyNumOfFallbackKeys.getInt(this);
            artifactoryEncryptionWrapper =
                    EncryptionWrapperFactory
                            .createArtifactoryKeyWrapper(
                                    artifactoryKey,
                                    artifactoryKey.getParentFile(),
                                    numOfFallbackKeys,
                                    new ArtifactoryEncryptionKeyFileFilter(
                                            ConstantValues.securityArtifactoryKeyLocation.getString(this)));
        } else if (artifactoryEncryptionWrapper == null) {
            artifactoryEncryptionWrapper = DUMMY_WRAPPER;
        }
        return artifactoryEncryptionWrapper;
    }

    @NonNull
    public JFrogMasterKeyEncrypter getJFrogMasterKeyEncrypter() {
        if (jFrogMasterKeyEncrypter == null) {
            String key = getMasterKey();
            jFrogMasterKeyEncrypter = new JFrogMasterKeyEncrypter(key);
        }
        return jFrogMasterKeyEncrypter;
    }

    public void unsetArtifactoryEncryptionWrapper() {
        this.artifactoryEncryptionWrapper = null;
    }

    /**
     * Return DB configuration
     */
    public ArtifactoryDbProperties getDBProperties() {
        return dbProperties;
    }

    /**
     * Calculate a unique id for the VM to support Artifactories with the same ip (e.g. accross NATs)
     */
    public String getHostId() {
        if (artifactorySystemProperties != null) {
            String result = artifactorySystemProperties.getProperty(ConstantValues.hostId);
            if (StringUtils.isNotBlank(result)) {
                return result;
            }
        }
        // TODO: Should support the HA Node host id system
        return VM_HOST_ID;
    }

    /**
     * Takes node id from ha properties into account first, used mainly for logs until we sort out RTFACT-13003
     */
    public String getHaAwareHostId() {
        if (haNodeProperties != null) {
            return haNodeProperties.getServerId();
        } else {
            return getHostId();
        }
    }

    /**
     * @return the {@link HaNodeProperties} object that represents the sysconf node contents
     */
    public HaNodeProperties getHaNodeProperties() {
        return haNodeProperties;
    }

    /**
     * Returns the content of the artifactory.config.import.xml file
     *
     * @return Content of artifactory.config.import.xml if exists, null if not
     */
    public String getImportConfigXml() {
        File importConfigFile = getArtifactoryConfigImportFile();
        if (importConfigFile.exists()) {
            try {
                String configContent = FileUtils.readFileToString(importConfigFile, UTF_8);
                if (StringUtils.isNotBlank(configContent)) {
                    File bootstrapConfigFile = getArtifactoryConfigBootstrapFile();
                    org.artifactory.util.Files.switchFiles(importConfigFile, bootstrapConfigFile);
                    return configContent;
                }
            } catch (IOException e) {
                throw toRuntimeExceptionOnFileReadFailure(importConfigFile.getAbsolutePath(), e);
            }
        }
        return null;
    }

    /**
     * Returns the content of the artifactory.repo.config.import.json file
     * Renames artifactory.repo.config.import.json file to artifactory.repository.config.bootstrap.json
     *
     * @return content of artifactory.repo.config.import.json if exists, null otherwise
     */
    public String getImportRepoConfigJson() {
        File repoConfigImportFile = getArtifactoryRepoConfigImportFile();
        if (repoConfigImportFile.exists()) {
            try {
                String repoConfigContent = FileUtils.readFileToString(repoConfigImportFile, UTF_8);
                if (StringUtils.isNotBlank(repoConfigContent)) {
                    File repoConfigBootstrapFile = getArtifactoryRepoConfigBootstrapFile();
                    Files.switchFiles(repoConfigImportFile, repoConfigBootstrapFile);
                    return repoConfigContent;
                }
            } catch (IOException e) {
                throw toRuntimeExceptionOnFileReadFailure(repoConfigImportFile.getAbsolutePath(), e);
            }
        }
        return null;
    }

    public void renameInitialConfigFileIfExists() {
        File initialConfigFile = getArtifactoryConfigFile();
        if (initialConfigFile.isFile()) {
            org.artifactory.util.Files.switchFiles(initialConfigFile,
                    getArtifactoryConfigBootstrapFile());
        }
    }

    public String getBootstrapConfigXml() {
        File oldLocalConfig = getArtifactoryConfigFile();
        File newBootstrapConfig = getArtifactoryConfigBootstrapFile();
        String result;
        if (newBootstrapConfig.exists()) {
            try {
                result = FileUtils.readFileToString(newBootstrapConfig, UTF_8);
            } catch (IOException e) {
                throw toRuntimeExceptionOnFileReadFailure(newBootstrapConfig.getAbsolutePath(), e);
            }
        } else if (oldLocalConfig.exists()) {
            try {
                result = FileUtils.readFileToString(oldLocalConfig, UTF_8);
            } catch (IOException e) {
                throw toRuntimeExceptionOnFileReadFailure(oldLocalConfig.getAbsolutePath(), e);
            }
        } else {
            String resPath = META_INF_DEFAULT_PATH + ARTIFACTORY_CONFIG_FILE;
            InputStream is = ArtifactoryHome.class.getResourceAsStream(resPath);
            if (is == null) {
                throw new IllegalStateException("Could read the default configuration from classpath at " + resPath);
            }
            try {
                result = IOUtils.toString(is, UTF_8);
            } catch (IOException e) {
                throw toRuntimeExceptionOnFileReadFailure(resPath, e);
            }
        }
        return result;
    }

    private RuntimeException toRuntimeExceptionOnFileReadFailure(String fileLocation, Exception e) {
        return new RuntimeException("Could not read data from '" + fileLocation +
                "' file due to: " + e.getMessage(), e);
    }

    public boolean isHaConfigured() {
        //haNodeProperties is inited when home is constructed, and should never be null.
        return haNodeProperties != null && haNodeProperties.isEnabled();
    }

    public String getServerId() {
        if (haNodeProperties != null) {
            return haNodeProperties.getServerId();
        }
        return ARTIFACTORY_PRO;
    }

    public Optional<String> getTenantId() {
        return Optional.ofNullable(technicalServerName);
    }

    //Be careful with this, The config manager init calls this when db is not available so it must be taken from
    //META-INF
    public CompoundVersionDetails getRunningArtifactoryServiceVersion() {
        try (InputStream inputStream = ArtifactoryHome.class.getResourceAsStream(ARTIFACTORY_VERSION_PROPERTIES)) {
            CompoundVersionDetails details = ArtifactoryVersionReader
                    .readAndFindServiceVersion(inputStream, ARTIFACTORY_VERSION_PROPERTIES);
            //Sanity check
            if (!details.isCurrent()) {
                throw new IllegalStateException("Running service version is not the current service version." +
                        " Running: " + details + " Current: " + details.getVersion());
            }
            return details;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Unexpected exception occurred: Fail to load artifactory.properties from class resource", e);
        }
    }

    public CompoundVersionDetails getRunningArtifactoryProductVersion() {
        File artifactoryProductPropertiesFile = getArtifactoryProductVersionPropertiesFile();
        try (InputStream inputStream = FileUtils.openInputStream(artifactoryProductPropertiesFile)) {
            return ArtifactoryVersionReader.readAndFindProductVersion(inputStream,
                    artifactoryProductPropertiesFile.getName());
        } catch (IOException e) {
            log.error("Failed to read {} file.", ARTIFACTORY_PRODUCT_VERSION_PROPERTIES_FILE);
            log.debug("Failed to read {} file from: {}", ARTIFACTORY_PRODUCT_VERSION_PROPERTIES_FILE,
                    artifactoryProductPropertiesFile.getAbsolutePath(), e);
            return null;
        }
    }

    private void create() {
        try {
            //etc, etc/security, etc/plugins, etc/logo, etc/ui
            etcDir = createDir(sysLayout.getServiceEtc());
            externalConversionsDir = getSubDir(getEtcDir(), "conversion");
            securityDir = createSubDir(etcDir, SECURITY_DIR);
            SecurityFolderHelper.setPermissionsOnSecurityFolder(securityDir);
            accessClientDir = createSubDir(getSecurityDir(), ACCESS_SUB_DIR);
            pluginsDir = createSubDir(getEtcDir(), "plugins");
            logoDir = createSubDir(getEtcDir(), "ui");
            checkWritableDirectory(logoDir);
            checkWritableDirectory(pluginsDir);

            // Node properties may influence the location of data and backup dirs
            initHaNodeProperties();

            //data and its temps
            dataDir = getHAAwareSubDir(DATA_DIR, sysLayout.getServiceData());
            checkWritableDirectory(dataDir);
            gitDir = createSubDir(getDataDir(), "git");
            checkWritableDirectory(gitDir);
            importDir = createSubDir(getDataDir(), IMPORT_DIR);
            checkWritableDirectory(importDir);
            File tempRootDir = createSubDir(getDataDir(), "tmp");
            checkWritableDirectory(tempRootDir);
            tempWorkDir = createSubDir(tempRootDir, WORK_DIR);
            checkWritableDirectory(tempWorkDir);
            tempWorkFullSyncDir = createSubDir(tempWorkDir, "fullSync");
            checkWritableDirectory(tempWorkFullSyncDir);
            tempUploadDir = createSubDir(tempRootDir, "artifactory-uploads");
            checkWritableDirectory(tempUploadDir);
            cleanTempDirs(tempRootDir);

            //backup
            backupDir = getHAAwareSubDir(BACKUP_DIR, sysLayout.getServiceBackup());
            checkWritableDirectory(backupDir);

            //log
            logDir = createDir(sysLayout.getProductLog());
            checkWritableDirectory(logDir);
        } catch (Exception e) {
            log.error("", e);
            throw new IllegalArgumentException(
                    "Could not initialize artifactory directory structure due to: " + e.getMessage(), e);
        }
    }

    public void initMimeTypes() {
        // Reload the data and backup dirs the directories might change after the NoNFS converter to support legacy
        File mimeTypesFile = getMimeTypesFile();
        try {
            String mimeTypesXml = FileUtils.readFileToString(mimeTypesFile, UTF_8);
            mimeTypes = new MimeTypesReader().read(mimeTypesXml);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse mime types file from: " +
                    mimeTypesFile.getAbsolutePath(), e);
        } catch (JAXBException e) {
            throw new RuntimeException("Failed to parse mime types file from: " +
                    mimeTypesFile.getAbsolutePath(), e);
        }
    }

    public ArtifactoryDbProperties initDBProperties() {
        dbProperties = new ArtifactoryDbProperties(this);
        return dbProperties;
    }

    public void initHaNodeProperties() {
        if (haNodeProperties == null) {
            haNodeProperties = new HaNodeProperties();
        }
        haNodeProperties.load(sysConfig);
    }


    protected void initArtifactorySystemPropertiesInternal() {
        try {
            File file = getArtifactorySystemPropertiesFile();
            // avoid unstable state - reload to new instance and then replace reference atomically
            ArtifactorySystemProperties newProps = new ArtifactorySystemProperties();
            newProps.loadArtifactorySystemProperties(file,
                    getRunningArtifactoryServiceVersion(), getRunningArtifactoryProductVersion());
            artifactorySystemProperties = newProps;
            setActiveSpringProfiles(newProps);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse artifactory system properties from: " +
                    getArtifactorySystemPropertiesFile().getAbsolutePath(), e);
        }
    }

    public void initArtifactorySystemProperties() {
        log.debug("Initializing Artifactory system properties");
        initArtifactorySystemPropertiesInternal();
        assertArtifactoryProductProperties();
        log.debug("Artifactory system properties initialized");
    }

    private void assertArtifactoryProductProperties() {
        if (!artifactorySystemProperties.hasProperty(ConstantValues.artifactoryProductVersion)) {
            throw new ProductVersionNotFoundException("The artifactory.product.version property is missing. " +
                    "Make sure the " + ARTIFACTORY_PRODUCT_VERSION_PROPERTIES_FILE + " file is in the app folder.");
        }
    }

    public String getDefaultDbHome() {
        return getDataDir().getAbsolutePath() + "/derby";
    }

    public String getDerbyLogFilePath() {
        return new File(getLogDir(), "derby.log").getAbsolutePath();
    }

    public void writeArtifactoryProperties() {
        File artifactoryPropertiesFile = getArtifactoryPropertiesFile();
        //Copy the artifactory.properties file into the data folder
        try {
            //Copy from default
            URL resource = ArtifactoryHome.class.getResource("/META-INF/" + ARTIFACTORY_PROPERTIES_FILE);
            FileUtils.copyURLToFile(resource, artifactoryPropertiesFile);
        } catch (IOException e) {
            throw new RuntimeException("Could not copy " + ARTIFACTORY_PROPERTIES_FILE + " to " +
                    artifactoryPropertiesFile.getAbsolutePath(), e);
        }
    }

    @Nonnull
    public File getAccessResourceTypeConverterMarkerFile() {
        return new File(etcDir, ".resource.type.converter.marker");
    }

    @Nonnull
    public File getAccessUserCustomDataDecryptionMarkerFile() {
        return new File(etcDir, ".custom.data.decryption.marker");
    }

    @Nonnull
    public File getCreateDefaultBuildPermissionMarkerFile() {
        return new File(etcDir, ".default.build.permission.marker");
    }

    @Nonnull
    public File getCreateBackupExcludedBuildNames() {
        return new File(etcDir, ".backup.builds.excluded.marker");
    }

    public File getSkipVerifyPrivilegesMarkerFile() {
        return new File(etcDir, ".skip.priv.check.marker");
    }

    protected void setMimeTypes(MimeTypes mimeTypes) {
        this.mimeTypes = mimeTypes;
    }

    /**
     * Missing Closure ;-)
     */
    public interface SimpleLog {
        void log(String message);
    }

    //Originally part of RTFACT-13364 by Bagants and Yinon
    private void cleanTempDirs(File tempRootDir) {
        try {
            // Never delete all files because in HA shared env (shared data dir), new nodes that starting might
            // delete files that older nodes just created and yet read/used them.
            AgeFileFilter ageFileFilter = new AgeFileFilter(DateUtils.addDays(new Date(), -1));
            Collection<File> files = FileUtils.listFiles(tempRootDir, ageFileFilter, DirectoryFileFilter.DIRECTORY);
            for (File childFile : files) {
                FileUtils.forceDelete(childFile);
            }
            // Don't clean up all empty directories blindly because it includes required folders (e.g. work & uploads)
            org.artifactory.util.Files.cleanupEmptyDirectories(tempRootDir, file ->
                    !file.equals(tempWorkDir) && !file.equals(tempUploadDir) && !file.equals(tempWorkFullSyncDir)
            );
        } catch (Exception e) {
            log.warn("Warning: unable to clean temporary directories: {}", e.getMessage());
            log.debug("", e);
        }
    }

    /**
     * ha.data and ha.backup dirs are potentially resolvable from the ha config, other sub dirs are not supported!
     * {@see RTFACT-19301}
     */
    File getHAAwareSubDir(String subDirName, File defaultLocation) throws IOException {
        if (!subDirName.equals("data") && !subDirName.equals("backup")) {
            //Dev: did you trigger this? check ur code.
            throw new IllegalArgumentException(
                    "Configurable location for HA directories is only supported for 'data' and 'backup'.");
        }
        Optional<String> customHaPath = getDirHaNodeProperty(subDirName);
        File subDir;
        if (customHaPath.isPresent()) {
            File userPath = new File(customHaPath.get());
            if (userPath.isAbsolute()) {
                // If the user provided absolute path then there is no need for the parent dir.
                subDir = userPath;
                //try to create it in any case
                FileUtils.forceMkdir(subDir);
            } else {
                //The migration script resolves relative paths and writes them as absolute paths upon upgrade.
                //see RTFACT-19301
                subDir = createDir(defaultLocation);
                log.warn("WARNING: relative path for directory '{}' is unsupported. the default location will be " +
                        "used instead: '{}'", subDirName, subDir.getAbsolutePath());
            }
        } else {
            // If the user didn't provide userPath then use the default name
            subDir = createDir(defaultLocation);
        }
        return subDir;
    }

    /**
     * @return a file representing {@param subDirName} under {@param parent}
     * An attempt is made to create the file as a directory.
     */
    private File createSubDir(File parent, String subDirName) throws IOException {
        File subDir = getSubDir(parent, subDirName);
        return createDir(subDir);
    }

    private File getSubDir(File parent, String subDirName) {
        return new File(parent, subDirName);
    }

    /**
     * Attempts creating {@param subdir} as a folder.
     */
    private File createDir(File dir) throws IOException {
        FileUtils.forceMkdir(dir);
        return dir;
    }

    private Optional<String> getDirHaNodeProperty(String subDirName) {
        String path = null;
        if (haNodeProperties != null) {
            path = haNodeProperties.getHaDirProperty(subDirName);
        }
        return Optional.ofNullable(path);
    }

    @NonNull
    private String getMasterKey() {
        return sysConfig.helper().getMasterKey().orElseThrow(() -> new IllegalStateException(
                "Could not load master key. Master key not provided and not found under the '" +
                        "" + sysConfig.helper().getMasterKeyFile() + "' dir"));
    }

    /**
     * Gets new packages implementations from ConstantValues.packageHandlerSupportedPackages, creates spring profile
     * for each of them. Combined list of profiles is set to System property `spring.profiles.active` that
     * processed by spring.
     *
     * @param artifactoryProps artifactoryProperties
     */
    @VisibleForTesting
    void setActiveSpringProfiles(ArtifactorySystemProperties artifactoryProps) {
        try {
            String packageHandlerProfiles =
                    artifactoryProps.getListProperty(ConstantValues.packageHandlerSupportedPackages)
                            .stream().map(this::toPackageProfile)
                            .filter(StringUtils::isNotBlank)
                            .collect(Collectors.joining(","));
            String existingProfiles = System.getProperty("spring.profiles.active");
            String result;
            if (StringUtils.isNotBlank(existingProfiles) && StringUtils.isNotBlank(packageHandlerProfiles) &&
                    !existingProfiles.contains(packageHandlerProfiles)) {
                result = String.join(",", existingProfiles, packageHandlerProfiles);
            } else if (StringUtils.isNotBlank(existingProfiles)) {
                result = existingProfiles;
            } else {
                result = packageHandlerProfiles;
            }
            result = packageHandlerSupportEnabledForPackages(artifactoryProps, result);
            result = addPermissionsCacheProfile(artifactoryProps, result);
            log.info("Setting '{}' to system property 'spring.profiles.active'", result);
            System.setProperty("spring.profiles.active", result);
        } catch (Exception e) {
            log.warn("Failed to execute setActiveSpringProfiles", e);
        }
    }

    private String addPermissionsCacheProfile(ArtifactorySystemProperties artifactoryProps, String result) {
        String profileName =
                PermissionsCacheType.toProfileName(artifactoryProps.getProperty(ConstantValues.permissionsCacheType));
        if (StringUtils.isBlank(result)) {
            return profileName;
        }
        if (result.contains(profileName)) {
            return result;
        }
        return result + ',' + profileName;
    }

    /**
     * This method looks for all the individual properties for Packages to decide if it needs to be added to Spring
     * Profile or not.
     * Remember, the packages that are already mentioned in "package.handler.supported.packages" will not be read for
     * individual package property.
     */
    private String packageHandlerSupportEnabledForPackages(ArtifactorySystemProperties artifactoryProps,
            String result) {
        String propertyPrefix = "package.handler";
        List<String> currentProfilesList =
                result.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(result.split(",")));
        Map<Boolean, List<ConstantValues>> availableIndividualPkgProperties = PackageProfile.values.keySet().stream()
                .map(packageProfile -> String.join(".", propertyPrefix, packageProfile))
                .flatMap(pkgIndividualProperty -> Arrays.stream(ConstantValues.values())
                        .filter(constantVal -> constantVal.getPropertyName().endsWith(pkgIndividualProperty)))
                .collect(Collectors.partitioningBy(artifactoryProps::getBooleanProperty));

        processAvailablePkgProperties(currentProfilesList, availableIndividualPkgProperties, true);
        processAvailablePkgProperties(currentProfilesList, availableIndividualPkgProperties, false);

        return currentProfilesList.isEmpty() ? "" : String.join(",", currentProfilesList);
    }

    private static void processAvailablePkgProperties(List<String> currentProfilesList,
            Map<Boolean, List<ConstantValues>> availableConstants, boolean enabled) {
        List<String> profiles = availableConstants.get(enabled).stream()
                .map(constantValues -> PackageProfile.toPackageProfile(
                        StringUtils.substringAfterLast(constantValues.getPropertyName(), ".")))
                .filter(packageName -> !currentProfilesList.contains(packageName))
                .toList();
        if (enabled) {
            currentProfilesList.addAll(profiles);
        } else {
            currentProfilesList.removeAll(profiles);
        }
    }

    private String toPackageProfile(String packageType) {
        String packageProfile;
        try {
            packageProfile = PackageProfile.toPackageProfile(packageType);
        } catch (Exception e) {
            // Remove unknown profile
            packageProfile = "";
        }
        return packageProfile;
    }

    public String getLogbackFile() throws IOException {
        File logbackFile = getLogbackConfig();
        if (logbackFile.exists()) {
            return FileUtils.readFileToString(logbackFile, UTF_8);
        }
        return null;
    }

    public static boolean getBooleanFromSysConfig(@Nonnull String key, boolean defaultValue) {
        try {
            return get().getSysConfig().getBoolean(key, defaultValue);
        } catch (Exception e) {
            log.warn("Failed to determine system property: '{}', defaulting to: '{}'", key, defaultValue);
            return defaultValue;
        }
    }
}
