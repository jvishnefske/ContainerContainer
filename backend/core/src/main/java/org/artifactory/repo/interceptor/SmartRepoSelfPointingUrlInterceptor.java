package org.artifactory.repo.interceptor;

import jakarta.ws.rs.core.UriBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.BaseUrlModel;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.validation.RepoUrlValidator;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.spring.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.artifactory.repo.RepoConfigChange.ConfigAction;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.interceptor.SmartRepoSelfPointingUrlInterceptor.VALIDATE_SELF_POINTING_URL_PROPERTY_NAME;

/**
 * Interceptor to validate smart repository configurations on create and update actions.
 * Ensures that:
 * - Smart repository URLs are not self-pointing.
 *
 * @author Aram Kirakosyan
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = VALIDATE_SELF_POINTING_URL_PROPERTY_NAME, havingValue = "true")
public class SmartRepoSelfPointingUrlInterceptor implements RepoConfigChangeInterceptor {

    static final String VALIDATE_SELF_POINTING_URL_PROPERTY_NAME =
            "artifactory.validate.self.pointing.smart.repo.url";
    private final CentralConfigService centralConfigService;

    @Override
    public void onBeforeSave(RepoConfigChanges repoConfigChanges) {
        if (centralConfigService == null || centralConfigService.getDescriptor() == null) {
            log.debug("Central configuration descriptor is null, skipping self-pointing validation");
            return;
        }
        BaseUrlModel baseUrlModel = centralConfigService.getPlatformBaseUrl();
        String baseServerHost = getBaseServerHost(baseUrlModel);
        if (baseServerHost == null) {
            log.warn("Base server host is missing, skipping self-pointing validation");
            return;
        }
        List<RepoConfigChange> createRemoteRepoChanges = repoConfigChanges.filterBy(ConfigAction.REPO_CREATE, REMOTE);
        List<RepoConfigChange> updateRemoteRepoChanges = repoConfigChanges.filterBy(ConfigAction.REPO_UPDATE, REMOTE);
        List<RepoConfigChange> allRemoteRepoChanges = new ArrayList<>(createRemoteRepoChanges);
        allRemoteRepoChanges.addAll(updateRemoteRepoChanges);
        validate(baseServerHost, allRemoteRepoChanges);
    }

    private void validate(String baseServerHost, List<RepoConfigChange> remoteRepoChanges) {
        for (RepoConfigChange remoteRepoChange : remoteRepoChanges) {
            validateUrl(baseServerHost, remoteRepoChange);
        }
    }

    private void validateUrl(String baseServerHost, RepoConfigChange remoteChange) {
        RemoteRepoConfig repoConfig = (RemoteRepoConfig) remoteChange.getNewRepoConfig();
        if (repoConfig != null) {
            String url = repoConfig.getUrl();
            RepoUrlValidator.validateNoSelfPointingUrl(baseServerHost, repoConfig.getKey(), url);
        }
    }

    private String getBaseServerHost(BaseUrlModel baseUrlModel) {
        if (baseUrlModel == null || baseUrlModel.getBaseUrl() == null) {
            return null;
        }
        try {
            URI baseUri = UriBuilder.fromUri(baseUrlModel.getBaseUrl()).build();
            return baseUri != null ? baseUri.getHost() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}