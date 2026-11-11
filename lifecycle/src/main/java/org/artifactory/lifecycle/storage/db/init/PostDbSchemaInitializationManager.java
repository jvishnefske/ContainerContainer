package org.artifactory.lifecycle.storage.db.init;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterManager;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.state.ArtifactoryStateManagerUtil;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.version.VersionProvider;
import org.jfrog.config.ifc.ConfigurationManager;

import static org.artifactory.addon.ArtifactoryRunningMode.HA;
import static org.artifactory.addon.ArtifactoryRunningMode.PRO;

/**
 * A post-DB schema initialization manager.
 *
 * @author Uriah Levy
 */
@Slf4j
public class PostDbSchemaInitializationManager {
    public final ArtifactoryHome artifactoryHome;
    public final ConfigurationManager configurationManager;
    private final VersionProvider versionProvider;
    private final ConverterManager convertersManager;
    private final ArtifactoryServersCommonService serversCommonService;

    public PostDbSchemaInitializationManager(ArtifactoryHome artifactoryHome,
            ConfigurationManager configurationManager, VersionProvider versionProvider,
            ConverterManager convertersManager,
            ArtifactoryServersCommonService serversCommonService) {
        this.artifactoryHome = artifactoryHome;
        this.configurationManager = configurationManager;
        this.versionProvider = versionProvider;
        this.convertersManager = convertersManager;
        this.serversCommonService = serversCommonService;
    }

    /**
     * Performs post-DB schema initialization operations.
     * Converting the database, and initializing the server state.
     */
    public void init() {
        log.info("Initializing Post-DB initialization manager");
        convertAndInit();
        log.info("Post-DB initialization manager initialized");
    }

    private void convertAndInit() {
        convertersManager.convertDatabase();
        // Create the initial server state in this early stage in order to prevent an orphan HA init lock cleanup
        createInitialServerState();
        convertersManager.convertMarkers();
        convertersManager.convertHome();
        convertersManager.convertHomeSync();
        configurationManager.startSync();
    }

    private void createInitialServerState() {
        ArtifactoryStateManagerUtil
                .createOrUpdateArtifactoryServerState(artifactoryHome.getServerId(), ArtifactoryServerState.STARTING,
                        getRunningMode(artifactoryHome), versionProvider.getRunningServiceVersion(),
                        serversCommonService);
    }

    private ArtifactoryRunningMode getRunningMode(ArtifactoryHome artifactoryHome) {
        return artifactoryHome.isHaConfigured() ? HA : PRO;
    }
}