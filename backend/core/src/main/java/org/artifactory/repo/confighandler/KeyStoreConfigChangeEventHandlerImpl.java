package org.artifactory.repo.confighandler;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.sapi.common.Lock;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.KEYSTORE_CONFIG_REMOVE_KEY_PAIR;
import static org.artifactory.system.config.change.event.SystemConfigChangeEvent.SystemConfigChangeEventType.KEYSTORE_CONFIG_REMOVE_KEY_STORE;

/**
 * @author Mor Merhav
 * Triggered upon key store changes and updates the appropriate virtual/remote repository configurations
 * Triggered when:
 * 1. {@link org.artifactory.addon.webstart.keystore.KeyStoreService#removeKeyPair} is called and removes a keypair/cert
 * 2. {@link org.artifactory.addon.webstart.keystore.KeyStoreService#removeKeyStore} is called and removes a key store
 */
@SuppressWarnings("JavadocReference")
@Slf4j
@Component
public class KeyStoreConfigChangeEventHandlerImpl
        implements KeyStoreConfigChangeEventHandler, ConfigChangeEventHandler {

    private static final Set<SystemConfigChangeEventType> configChangeEventTypeSubscription =
            Set.of(KEYSTORE_CONFIG_REMOVE_KEY_PAIR, KEYSTORE_CONFIG_REMOVE_KEY_STORE);
    private final InternalRepositoryService repositoryService;

    @Autowired
    public KeyStoreConfigChangeEventHandlerImpl(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public void handle(ConfigChangeEventContext context) {
        SystemConfigChangeEvent event = context.getEvent();
        switch (event.getSystemConfigChangeEventType()) {
            case KEYSTORE_CONFIG_REMOVE_KEY_PAIR -> {
                String keyPairName = (String) event.getOldConfig();
                log.debug("Handling config change following removal of key pair name: {}", keyPairName);
                cleanRemovedClientCertificateFromRemoteRepos(context, keyPairName);
                cleanRemovedKeyPairsFromVirtualRepos(context, keyPairName);
            }
            case KEYSTORE_CONFIG_REMOVE_KEY_STORE -> {
                String keyStoreName = (String) event.getOldConfig();
                log.debug("Handling config change following removal of key store: {}", keyStoreName);
                cleanAllKeyPairsFromVirtualRepos(context);
            }
            default -> {/*NOOP*/}
        }
    }

    @Override
    public Set<SystemConfigChangeEventType> getConfigChangeEventTypeSubscription() {
        return configChangeEventTypeSubscription;
    }

    @Lock
    @Override
    public void undeployRepoInTransaction(VirtualRepo virtualRepo, RepoPath repoPath) {
        virtualRepo.undeploy(new DeleteContext(repoPath));
    }

    private void cleanRemovedClientCertificateFromRemoteRepos(ConfigChangeEventContext context,
            String removedClientCert) {
        RepositoryConfigService repositoryConfigService =
                ContextHelper.get().beanForType(RepositoryConfigService.class);
        List<RemoteRepoConfig> remoteRepoConfigs = repositoryConfigService.getRepoConfigsByType(REMOTE).stream()
                .map(RemoteRepoConfig.class::cast)
                .toList();
        for (RemoteRepoConfig remoteRepoConfig : remoteRepoConfigs) {
            String clientTlsCertificate = remoteRepoConfig.getRepoTypeConfig().getClientTlsCertificate();
            if (removedClientCert.equals(ArtifactWebstartAddon.SSL_CERT_ALIAS_PREFIX + clientTlsCertificate)) {
                String repoKey = remoteRepoConfig.getKey();
                log.debug("Removing client certificate '{}' from remote repository '{}'", removedClientCert, repoKey);
                RepoConfig repoConfig = context.getReposToUpdate().get(repoKey);
                if (repoConfig != null) {
                    remoteRepoConfig = (RemoteRepoConfig) repoConfig;
                }
                remoteRepoConfig.getRepoTypeConfig().setClientTlsCertificate(null);
                context.getReposToUpdate().put(remoteRepoConfig.getKey(), remoteRepoConfig);
            }
        }
    }

    private void cleanRemovedKeyPairsFromVirtualRepos(ConfigChangeEventContext context, String removedKeyPairName) {
        RepositoryConfigService repositoryConfigService =
                ContextHelper.get().beanForType(RepositoryConfigService.class);
        List<RepoConfig> virtualRepos = repositoryConfigService.getRepoConfigsByType(VIRTUAL);
        for (RepoConfig virtualRepoConfig : virtualRepos) {
            String keyPair = virtualRepoConfig.getSecurityConfig().getKeyPair();
            if (removedKeyPairName.equals(keyPair)) {
                String repoKey = virtualRepoConfig.getKey();
                log.debug("Removing key entry '{}' from virtual repository '{}'", removedKeyPairName, repoKey);
                RepoConfig repoConfig = context.getReposToUpdate().get(repoKey);
                if (repoConfig != null) {
                    virtualRepoConfig = repoConfig;
                }
                virtualRepoConfig.getSecurityConfig().setKeyPair(null);
                context.getReposToUpdate().put(virtualRepoConfig.getKey(), virtualRepoConfig);
                VirtualRepo virtualRepo = repositoryService.virtualRepositoryByKey(repoKey);
                if (virtualRepo != null) {
                    RepoPath repoPath = InternalRepoPathFactory.create(repoKey, "");
                    getTransactionalMe().undeployRepoInTransaction(virtualRepo, repoPath);
                }
            }
        }
    }

    private void cleanAllKeyPairsFromVirtualRepos(ConfigChangeEventContext context) {
        RepositoryConfigService repositoryConfigService =
                ContextHelper.get().beanForType(RepositoryConfigService.class);
        List<RepoConfig> virtualRepos = repositoryConfigService.getRepoConfigsByType(VIRTUAL);
        for (RepoConfig virtualRepo : virtualRepos) {
            if (StringUtils.isNotBlank(virtualRepo.getSecurityConfig().getKeyPair())) {
                log.debug("Removing key entry '{}' from virtual repository '{}'",
                        virtualRepo.getSecurityConfig().getKeyPair(), virtualRepo.getKey());
                RepoConfig repoConfig = context.getReposToUpdate().get(virtualRepo.getKey());
                if (repoConfig != null) {
                    virtualRepo = repoConfig;
                }
                virtualRepo.getSecurityConfig().setKeyPair(null);
                context.getReposToUpdate().put(virtualRepo.getKey(), virtualRepo);
            }
        }
    }

    private KeyStoreConfigChangeEventHandler getTransactionalMe() {
        return ContextHelper.get().beanForType(KeyStoreConfigChangeEventHandler.class);
    }
}
