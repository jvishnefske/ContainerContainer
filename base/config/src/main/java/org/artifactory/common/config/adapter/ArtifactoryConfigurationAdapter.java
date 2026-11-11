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

package org.artifactory.common.config.adapter;

import com.google.common.collect.Lists;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.config.ConfigurationManagerImpl;
import org.jfrog.config.Home;
import org.jfrog.config.broadcast.BroadcastChannel;
import org.jfrog.config.broadcast.TemporaryBroadcastChannelImpl;
import org.jfrog.config.db.DbChannel;
import org.jfrog.config.db.TemporaryDBChannel;
import org.jfrog.config.ifc.ConfigurationManagerAdapter;
import org.jfrog.config.metadata.MetaInfFile;
import org.jfrog.config.metadata.SharedConfigMetadata;
import org.jfrog.config.metadata.SharedFolderMetadata;
import org.jfrog.config.watch.FileEventType;
import org.jfrog.security.util.Pair;
import org.jfrog.storage.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_KEY_DEFAULT_TEMP_FILE_NAME;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_SYSTEM_PROPERTIES_FILE;
import static org.artifactory.common.home.ArtifactoryHome.BINARY_STORE_FILE_NAME;
import static org.artifactory.common.home.ArtifactoryHome.COMMUNICATION_KEY_FILE_NAME;
import static org.artifactory.common.home.ArtifactoryHome.COMMUNICATION_TOKEN_FILE_NAME;
import static org.artifactory.common.home.ArtifactoryHome.LOGBACK_CONFIG_FILE_NAME;
import static org.artifactory.common.home.ArtifactoryHome.MIME_TYPES_FILE_NAME;
import static org.jfrog.config.home.JFrogHome.JOIN_KEY_FILENAME;
import static org.jfrog.config.home.JFrogHome.MASTER_KEY_FILENAME;

/**
 * @author gidis
 */
@SuppressWarnings({"squid:S1075"})
public class ArtifactoryConfigurationAdapter implements ConfigurationManagerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryConfigurationAdapter.class);

    private static final String SECURITY_PREFIX = "artifactory.security.";
    public static final String SIGNING_KEY_DB_CONFIG_NAME = SECURITY_PREFIX + "url.signing.key";
    public static final String META_INF_DEFAULT_PATH = "/META-INF/default/";
    protected DbChannel dbChannel;
    protected BroadcastChannel broadcastChannel;
    protected boolean ha;
    protected final ArtifactoryHome home;
    protected ArtifactoryHome oldHome;

    private final List<MetaInfFile> defaultConfigs;
    private final List<SharedConfigMetadata> sharedConfigs;
    private final List<SharedFolderMetadata> sharedFolders;

    private static final List<String> blackList = Lists.newArrayList(
            ConfigurationManagerImpl.SECURITY_PREFIX + "access",
            ConfigurationManagerImpl.SECURITY_PREFIX + "binstore",
            ConfigurationManagerImpl.BASE_CONFIG_PREFIX + "artifactory.config",
            COMMUNICATION_KEY_FILE_NAME,
            COMMUNICATION_KEY_FILE_NAME + ".old",
            COMMUNICATION_TOKEN_FILE_NAME,
            ARTIFACTORY_KEY_DEFAULT_TEMP_FILE_NAME,
            MASTER_KEY_FILENAME,
            JOIN_KEY_FILENAME,
            SIGNING_KEY_DB_CONFIG_NAME
    );
    private static final List<String> blackListRegexes = Lists.newArrayList(
            // excluding all import files
            "^artifactory\\" + File.separator + "config\\" + File.separator + "(.*)\\.import\\.(.*)",
            // excluding legacy security.xml files
            "^artifactory\\" + File.separator + "config\\" + File.separator + "security\\.(.*)\\.xml$"
    );

    /**
     * Initialize JavaFilesWatcher and register the shared files in order to receive events on file changes and then
     * synchronize the changes with the database and the other nodes
     */
    public ArtifactoryConfigurationAdapter(ArtifactoryHome home) {
        this.home = home;
        this.defaultConfigs = initDefaultConfigs();
        this.sharedConfigs = initSharedConfigs();
        this.sharedFolders = initSharedFolders();
    }

    private List<MetaInfFile> initDefaultConfigs() {
        return Lists.newArrayList(
                new MetaInfFile(META_INF_DEFAULT_PATH + MIME_TYPES_FILE_NAME, home.getMimeTypesFile()),
                new MetaInfFile(META_INF_DEFAULT_PATH + ARTIFACTORY_SYSTEM_PROPERTIES_FILE,
                        home.getArtifactorySystemPropertiesFile()),
                new MetaInfFile(META_INF_DEFAULT_PATH + BINARY_STORE_FILE_NAME, home.getBinaryStoreXmlFile()),
                new MetaInfFile(META_INF_DEFAULT_PATH + LOGBACK_CONFIG_FILE_NAME, home.getLogbackConfig())
        );
    }

    private List<SharedConfigMetadata> initSharedConfigs() {
        return Lists.newArrayList(
                // Artifactory system properties
                new SharedConfigMetadata(home.getArtifactorySystemPropertiesFile(),
                        META_INF_DEFAULT_PATH + ARTIFACTORY_SYSTEM_PROPERTIES_FILE, true,
                        false, false),
                // mimetypes.xml
                new SharedConfigMetadata(home.getMimeTypesFile(),
                        META_INF_DEFAULT_PATH + MIME_TYPES_FILE_NAME,
                        true, false, false),
                // binarystore.xml
                new SharedConfigMetadata(home.getBinaryStoreXmlFile(),
                        META_INF_DEFAULT_PATH + BINARY_STORE_FILE_NAME,
                        true, true, false),
                new SharedConfigMetadata(home.getBinaryStoreGcpCredentialsFile(),
                        null, false, true, false),
                // Access creds
                new SharedConfigMetadata(home.getAccessAdminCredsFile(), null, false,
                        true, false),
                // shared admin token
                new SharedConfigMetadata(home.getAccessAdminTokenFile(),
                        null, false, true, false),
                // license
                new SharedConfigMetadata(home.getLicenseFile(),
                        null, false, false, false),
                // repo configuration
                new SharedConfigMetadata(home.getArtifactoryRepoConfigLatestFile(),
                        null, false, false, false)
        );
    }

    private List<SharedFolderMetadata> initSharedFolders() {
        return Lists.newArrayList(
                // Plugins dir
                new SharedFolderMetadata(home.getPluginsDir(), false, false),
                // UI logo dir
                new SharedFolderMetadata(home.getLogoDir(), false, false),
                // Security dir
                new SharedFolderMetadata(home.getSecurityDir(), true, false)
        );
    }

    @Override
    public void initialize() {
        this.home.initArtifactorySystemProperties();
        //getBooleanProperty does not explode.
        this.broadcastChannel = new TemporaryBroadcastChannelImpl();
        this.ha = home.getArtifactoryHaNodePropertiesFile().exists();
    }

    @Override
    public List<String> getBlackListConfigs() {
        return blackList;
    }

    @Override
    public List<String> getBlackListRegexes() {
        return blackListRegexes;
    }

    @Override
    public List<MetaInfFile> getDefaultConfigs() {
        return defaultConfigs;
    }

    /**
     * The method register the shared files in the JavaFilesWatcher to receive file change on the files
     */
    @Override
    public List<SharedConfigMetadata> getSharedConfigs() {
        return sharedConfigs;
    }

    @Override
    public List<SharedFolderMetadata> getSharedFolders() {
        return sharedFolders;
    }

    @Override
    public Home getHome() {
        return home;
    }

    @Override
    public void unbind() {
        ArtifactoryHome.bind(oldHome);
    }

    @Override
    public void bind() {
        oldHome = ArtifactoryHome.isBound() ? ArtifactoryHome.get() : null;
        ArtifactoryHome.bind(home);
    }

    @Override
    public DbChannel getDbChannel() {
        if (dbChannel == null) {
            ArtifactoryDbProperties dbProperties = this.home.getDBProperties();
            blockIfHAWithDerby(dbProperties);
            this.dbChannel = new TemporaryDBChannel(dbProperties);
        }
        return dbChannel;
    }

    @Override
    public BroadcastChannel getBroadcastChannel() {
        return broadcastChannel;
    }

    @Override
    public void destroy() {
        if (broadcastChannel != null) {
            broadcastChannel.destroy();
        }
        if (dbChannel != null) {
            dbChannel.close();
        }
    }

    @Override
    public String getName() {
        return home.getHaAwareHostId();
    }

    /**
     * The method replaces the initial broadcast channel into the permanent implementation
     */
    @Override
    public void setPermanentBroadcastChannel(BroadcastChannel broadcastChannel) {
        // Make sure that this can be called only once
        if (this.broadcastChannel instanceof TemporaryBroadcastChannelImpl) {
            // Replace broadcastChannel but first fire accumulated events.
            TemporaryBroadcastChannelImpl initialBroadcastChannel =
                    (TemporaryBroadcastChannelImpl) this.broadcastChannel;
            // Replace broadcastChannel
            this.broadcastChannel = broadcastChannel;
            // Fire accumulated notifications events
            Set<Pair<String, FileEventType>> notifications = initialBroadcastChannel.getNotifications();
            log.debug("notifications = {}", notifications);
            notifications.forEach(pair -> broadcastChannel.notifyConfigChanged(pair.getFirst(), pair.getSecond()));
        } else {
            // Can reach here only on reload
            this.broadcastChannel = broadcastChannel;
        }
    }

    /**
     * The method replaces the initial db channel into the permanent implementation
     */
    @Override
    public void setPermanentDBChannel(DbChannel permanentDbChannel) {
        try {
            DbChannel tempDBChanel = dbChannel;
            dbChannel = permanentDbChannel;
            // Replace DB channel
            if (tempDBChanel instanceof TemporaryDBChannel) {
                log.debug("Replacing temporary DB channel with permanent DB channel");
                tempDBChanel.close();
                log.debug("Successfully closed temporary DB channel");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to replace temporary db channel with the permanent one due to: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public int getRetryAmount() {
        return ConstantValues.configurationManagerRetryAmount.getInt();
    }

    @Override
    public long getBlockingTime() {
        return ArtifactoryHome.isBound() ?
                ConstantValues.configurationManagerBlockingTime.getLong()
                : ArtifactoryConfigurationAdapter.BLOCKING_TIME;
    }

    @Override
    public long getQuietPeriod() {
        return ArtifactoryHome.isBound() ?
                ConstantValues.configurationManagerQuietPeriod.getLong()
                : ArtifactoryConfigurationAdapter.QUITE_PERIOD;
    }

    // except from devenv, it is not allowed to use HA with derby
    private void blockIfHAWithDerby(ArtifactoryDbProperties dbProperties) {
        if (DbType.DERBY == dbProperties.getDbType() && home.isHaConfigured() && !ConstantValues.devHa.getBoolean()) {
            throw new IllegalStateException(
                    "Cannot use Derby as the database type in HA mode, please check the system.yaml file.");
        }
    }
}
