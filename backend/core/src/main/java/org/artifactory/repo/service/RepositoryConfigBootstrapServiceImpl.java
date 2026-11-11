package org.artifactory.repo.service;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.AggregatedReposConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.security.SystemAuthenticationToken;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.Reloadable;
import org.artifactory.spring.StateChangeListener;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.util.RepoConfigBootstrapUtils;
import org.jfrog.common.JsonUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * @author Mor Merhav
 */
@Slf4j
@Service
@Reloadable(beanClass = RepositoryConfigBootstrapService.class, initAfter = AccessService.class)
public class RepositoryConfigBootstrapServiceImpl implements RepositoryConfigBootstrapService, StateChangeListener {

    private final AtomicBoolean shouldPropagate = new AtomicBoolean(false);

    /**
     * Bootstrap repositories from:
     * {@link org.artifactory.common.home.ArtifactoryHome#ARTIFACTORY_REPO_CONFIG_IMPORT_FILE}, if exists.
     *
     * @throws IllegalStateException if config conflict exists.
     * @see RepoConfigBootstrapUtils
     */
    @Override
    public void init() {
        if (ArtifactoryHome.get().getArtifactoryRepoConfigImportFile().exists()) {
            log.info("Checking if repositories should be bootstrapped...");
            RepoConfigBootstrapUtils.verifyConflictingRepoBootstrap();
            log.info("Found {} file, initiating repositories bootstrap...",
                    ArtifactoryHome.ARTIFACTORY_REPO_CONFIG_IMPORT_FILE);
            String importRepoConfigJson = ArtifactoryHome.get().getImportRepoConfigJson();
            AggregatedReposConfig aggregatedReposConfig =
                    JsonUtils.getInstance().readValue(importRepoConfigJson, AggregatedReposConfig.class);
            Set<RepoConfig> allRepoConfigs =
                    aggregatedReposConfig.getAll().stream().collect(toUnmodifiableSet());

            RepoConfigBootstrapStorageHelper repoConfigBootstrapStorageHelper =
                    RepoConfigBootstrapStorageHelperFactory.create();

            deleteExistingRepoConfigs(repoConfigBootstrapStorageHelper);
            repoConfigBootstrapStorageHelper.bulkInsertRepositoryConfigs(allRepoConfigs, false);
            repoConfigBootstrapStorageHelper.reloadRepoConfigurations();
            shouldPropagate.set(true);
            log.info("Repositories bootstrap completed.");
        }
    }

    private void deleteExistingRepoConfigs(RepoConfigBootstrapStorageHelper repoConfigBootstrapStorageHelper) {
        int repoConfigCount = repoConfigBootstrapStorageHelper.countAll();
        if (repoConfigCount == 0) {
            log.info("Repo configs do not exist. Skip cleanup step");
            return;
        }
        int deletedRepositories = repoConfigBootstrapStorageHelper.deleteAll();
        repoConfigBootstrapStorageHelper.reloadRepoConfigurations();
        log.info("{} existing repo configs were cleaned up", deletedRepositories);
    }

    @Override
    public void onStateChange(ArtifactoryServerState state) {
        if (state == ArtifactoryServerState.RUNNING && shouldPropagate.compareAndSet(true, false)) {
            propagateRepoConfigChanges();
        }
    }

    private void propagateRepoConfigChanges() {
        RepoConfigPropagationModel propagationModel = new RepoConfigPropagationModel();
        propagationModel.setAll(true);
        Authentication originalAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(new SystemAuthenticationToken());
            ContextHelper.get().beanForType(AddonsManager.class).addonByType(HaAddon.class)
                    .propagateRepoConfigsChanges(propagationModel);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(originalAuthentication);
        }
    }
}