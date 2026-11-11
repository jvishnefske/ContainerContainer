package org.artifactory.repo.interceptor.config;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.crypto.CryptoService;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.util.StopWatchUtil;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;

/**
 * Interceptor that runs during repository save. Encrypts sensitive fields (marked with
 * {@link org.artifactory.sensitive.Sensitive} or {@link org.artifactory.sensitive.DynamicSensitive}) before
 * persistence; validation is part of that encrypt path.
 *
 * @author Fawaz Atwa
 */
@Slf4j
@Component
public class RepoConfigSensitiveFieldChangeInterceptor implements RepoConfigChangeInterceptor {

    private final CryptoService cryptoService;

    public RepoConfigSensitiveFieldChangeInterceptor(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public void onBeforeSave(RepoConfigChanges repoConfigChanges) {
        List<RepoConfigChange> createOrUpdate = repoConfigChanges.filterBy(REPO_CREATE, REPO_UPDATE);
        StopWatchUtil.measureAndLogTime(log, Level.INFO,
                "Sensitive repo config encrypt took: {}ms",
                () -> runEncryptSensitiveFields(createOrUpdate));
    }

    private void runEncryptSensitiveFields(List<RepoConfigChange> createOrUpdate) {
        List<RepoConfig> configs = createOrUpdate.stream()
                .map(RepoConfigChange::getNewRepoConfig)
                .filter(Objects::nonNull)
                .toList();
        cryptoService.encryptSensitiveFields(configs);
    }
}
