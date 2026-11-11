package org.artifactory.repo.system;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.system.SystemRepos.SystemRepoConfig;
import org.artifactory.repo.system.suppliers.BillingRepo;
import org.artifactory.repo.system.suppliers.BuildInfoRepo;
import org.artifactory.repo.system.suppliers.EdgeRepo;
import org.artifactory.repo.system.suppliers.FullSyncRepo;
import org.artifactory.repo.system.suppliers.GridInfoRepo;
import org.artifactory.repo.system.suppliers.InTransitRepo;
import org.artifactory.repo.system.suppliers.LogShippingRepo;
import org.artifactory.repo.system.suppliers.PipeinfoRepo;
import org.artifactory.repo.system.suppliers.ReleaseBundleRepo;
import org.artifactory.repo.system.suppliers.SystemRepoBase;
import org.artifactory.repo.system.suppliers.UsageRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.BILLING;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.BUILDINFO;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.EDGE_UPLOADS;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.FULL_SYNC;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.GRID_INFO;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.IN_TRANSIT;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.LOG_SHIPPING;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.PIPEINFO;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.RELEASE_BUNDLE;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.USAGE;

/**
 * This class will be called when possible when creating a system repo.
 * The map contains the system repos that can be built through the factory and their supplying classes
 * Note that not all system repos are actually built through here due to limitations such as only getting initialised
 * on startup or not needing to be saved.
 *
 * @author Tomer Slutsky
 */
@Slf4j
@Lazy
@Component
public class SystemRepoConfigFactory {
    private final Map<SystemRepoConfig, SystemRepoBase> systemRepoSuppliers;
    private final RepositoryService repositoryService;
    private final CentralConfigService centralConfigService;

    @Autowired
    public SystemRepoConfigFactory(RepositoryService repositoryService,
            CentralConfigService centralConfigService) {
        this.repositoryService = repositoryService;
        this.centralConfigService = centralConfigService;
        systemRepoSuppliers = Map.of(
                IN_TRANSIT,
                new InTransitRepo(),
                EDGE_UPLOADS,
                new EdgeRepo(),
                RELEASE_BUNDLE,
                new ReleaseBundleRepo(),
                PIPEINFO,
                new PipeinfoRepo(),
                BUILDINFO,
                new BuildInfoRepo(),
                LOG_SHIPPING,
                new LogShippingRepo(),
                USAGE,
                new UsageRepo(),
                BILLING,
                new BillingRepo(),
                FULL_SYNC,
                new FullSyncRepo(),
                GRID_INFO,
                new GridInfoRepo()
        );
    }

    public void buildSystemRepo(SystemRepoConfig repoConfig, String repoKey) {
        SystemRepoBase repo = systemRepoSuppliers.get(repoConfig);
        if (repo == null) {
            log.warn("Tried to build a system repo with a non existing system repo - {}", repoConfig.getRepoKey());
            return;
        }
        log.debug("Fetched system repo - {}", repoConfig.getRepoKey());
        LocalRepoConfig systemRepoConfig = repo.createSystemRepoConfig(centralConfigService.getDescriptor(), repoKey);
        repositoryService.upsertRepoConfig(systemRepoConfig);
    }

    public void buildSystemRepo(SystemRepoConfig repoConfig) {
        buildSystemRepo(repoConfig, repoConfig.getRepoKey());
    }
}
