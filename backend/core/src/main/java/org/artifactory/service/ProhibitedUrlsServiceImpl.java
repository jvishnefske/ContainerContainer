package org.artifactory.service;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.common.ConstantValues;
import org.artifactory.cron.CronUtils;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.io.checksum.ChecksumUtils;
import org.artifactory.metrics.TimeRandomizer;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.interceptor.modle.AllowedRemoteUrlsConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.jobs.ProhibitedUrlsJob;
import org.jfrog.common.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.artifactory.storage.fs.service.ConfigsServiceKey.PACKAGES_ALLOWED_REMOTE_URLS;

/**
 * Manages the allowed remote URLs configuration. Fetches an allowlist JSON from an external repository
 * on a daily cron schedule, persists it in the database, and exposes it for validation during repository
 * create/edit and runtime artifact downloads. Repositories with disallowed URLs are automatically set to
 * offline mode.
 *
 * @author Dor Shriki
 */
@Slf4j
@Service
public class ProhibitedUrlsServiceImpl implements ProhibitedUrlsService {

    private final boolean isProhibitedUrlsCheckEnabled = ConstantValues.enableRemoteProhibitedURLs.getBoolean();
    static final String ALLOWED_URLS_SOURCE_URL =
            "https://releases.jfrog.io/artifactory/download-center-example-generic/" +
                    "artifactory-packages-allowed-remote-urls/latest/artifactory-packages-allowed-remote-urls.json";
    private static final String SOURCE_USERNAME = "admin";
    private static final String SOURCE_PASSWORD = "password";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private TaskService taskService;
    private InternalRepositoryService repositoryService;
    private ConfigsService configsService;
    private AddonsManager addonsManager;

    @Getter
    private AllowedRemoteUrlsConfig allowedRemoteUrlsConfig;
    private String currentChecksum;

    @Override
    public void onContextCreated() {
        if (!isProhibitedUrlsCheckEnabled) {
            log.debug("Allowed remote URLs check is disabled.");
            return;
        }
        loadConfigFromDb();
        startTasks();
    }

    @Override
    public void fetchAllowedRemoteUrls() {
        if (!isProhibitedUrlsCheckEnabled) {
            log.debug("Allowed remote URLs check is disabled.");
            return;
        }
        log.debug("Fetching allowed remote URLs");
        fetchFromRemote();
        validateAndSetOfflineInvalidRemoteUrls();
    }

    @Override
    public void syncAllowedRemoteUrls() {
        log.debug("Syncing allowed remote URLs from DB (HA propagation)");
        loadConfigFromDb();
        validateAndSetOfflineInvalidRemoteUrls();
    }

    // Loads the allowed URLs config from the configs DB table into memory.
    private void loadConfigFromDb() {
        AllowedRemoteUrlsConfig config =
                configsService.getConfig(PACKAGES_ALLOWED_REMOTE_URLS, AllowedRemoteUrlsConfig.class);
        if (config != null) {
            log.debug("Loaded allowed remote URLs config from DB");
            this.allowedRemoteUrlsConfig = config;
            updateChecksumFromCurrentConfig();
        } else {
            log.info("No allowed remote URLs config in DB yet, will be fetched by cron job");
        }
    }

    // Persists the given config to the configs DB table.
    private void persistConfig(AllowedRemoteUrlsConfig config) {
        configsService.addOrUpdateConfig(PACKAGES_ALLOWED_REMOTE_URLS, config, System.currentTimeMillis());
    }

    // Recomputes the SHA-256 checksum from the current in-memory config.
    private void updateChecksumFromCurrentConfig() {
        if (allowedRemoteUrlsConfig != null) {
            String json = JsonUtils.getInstance().valueToString(allowedRemoteUrlsConfig);
            currentChecksum = computeSha256FromString(json);
        }
    }

    // Registers the daily cron job and an immediate manual run.
    private void startTasks() {
        taskService.startTask(
                TaskUtils.createCronTask(ProhibitedUrlsJob.class, buildRandomQuartzExp(),
                        "Allowed Remote URLs Check Job"),
                false);
        taskService.startTask(TaskUtils.createManualTask(ProhibitedUrlsJob.class, 0), false);
    }

    // Fetches the allowlist JSON from the remote source URL.
    private void fetchFromRemote() {
        try {
            HttpResponse<String> response = getResponseFromUrl(ALLOWED_URLS_SOURCE_URL);
            handleFetchResponse(response, ALLOWED_URLS_SOURCE_URL);
        } catch (InterruptedException e) {
            log.warn("Interrupted while fetching allowed remote URLs from '{}'", ALLOWED_URLS_SOURCE_URL);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Failed to fetch allowed remote URLs from '{}'", ALLOWED_URLS_SOURCE_URL, e);
        }
    }

    // Processes the HTTP response: updates config if checksum changed.
    private void handleFetchResponse(HttpResponse<String> response, String sourceUrl) {
        if (response.statusCode() != HttpStatus.SC_OK) {
            log.warn("Fetching allowed remote URLs from '{}' returned status {}", sourceUrl, response.statusCode());
            return;
        }
        String body = response.body();
        String newChecksum = computeSha256FromString(body);
        if (newChecksum.equals(currentChecksum)) {
            log.debug("Allowed remote URLs unchanged (checksum match)");
            return;
        }
        AllowedRemoteUrlsConfig config = parseConfig(body);
        if (config == null) {
            return;
        }
        persistConfig(config);
        this.allowedRemoteUrlsConfig = config;
        this.currentChecksum = newChecksum;
        log.info("Updated allowed remote URLs from '{}'", sourceUrl);
        propagateAllowedRemoteUrlsSyncIfNeeded();
    }

    // Parses a JSON string into an AllowedRemoteUrlsConfig instance.
    private AllowedRemoteUrlsConfig parseConfig(String json) {
        try {
            return JsonUtils.getInstance().readValue(json, AllowedRemoteUrlsConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse allowed remote URLs JSON", e);
            return null;
        }
    }

    // Sends an HTTP GET request to the given URL with basic auth.
    @VisibleForTesting
    HttpResponse<String> getResponseFromUrl(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        log.debug("Sending request to URL: {}", url);
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
        log.debug("Received response with status code: {}", response.statusCode());
        return response;
    }

    // Sets online repos with disallowed URLs to offline mode.
    private void validateAndSetOfflineInvalidRemoteUrls() {
        AllowedRemoteUrlsConfig config = this.allowedRemoteUrlsConfig;
        if (config == null) {
            return;
        }
        repositoryService.getRemoteRepoConfigs().stream()
                .filter(r -> StringUtils.isNotBlank(r.getRepoTypeConfig().getUrl()))
                .filter(r -> !r.getRepoTypeConfig().isOffline())
                .filter(r -> !isRepoUrlAllowed(r, config))
                .forEach(this::setRemoteRepoOffline);
    }

    // Checks whether a repo's URL is allowed for its package type.
    private boolean isRepoUrlAllowed(RemoteRepoConfig repoConfig, AllowedRemoteUrlsConfig config) {
        String url = repoConfig.getRepoTypeConfig().getUrl();
        RepoType packageType = repoConfig.getPackageType();
        return config.isUrlAllowed(url, packageType);
    }

    // Marks a remote repo as offline and logs a warning.
    private void setRemoteRepoOffline(RemoteRepoConfig remoteRepoConfig) {
        String repoKey = remoteRepoConfig.getKey();
        String url = remoteRepoConfig.getRepoTypeConfig().getUrl();
        remoteRepoConfig.getRepoTypeConfig().setOffline(true);
        repositoryService.updateRepoConfig(remoteRepoConfig);
        log.warn("Invalid remote URL detected. Repository '{}' has been set to offline mode. "
                        + "URL '{}' is not allowed for package type '{}'.",
                repoKey, url, remoteRepoConfig.getPackageType());
    }

    // Builds a cron expression, using a predefined one or a random nightly schedule.
    @VisibleForTesting
    String buildRandomQuartzExp() {
        String predefinedExp = ConstantValues.remoteProhibitedURLsCheckCron.getString();
        if (StringUtils.isNotBlank(predefinedExp) && CronUtils.isValid(predefinedExp)) {
            return predefinedExp;
        }
        return String.format(
                "0 %d %d ? * *",
                TimeRandomizer.randomMinute(),
                TimeRandomizer.randomHourAtNight()
        );
    }

    // Notifies other HA nodes to reload the config from DB.
    private void propagateAllowedRemoteUrlsSyncIfNeeded() {
        if (addonsManager == null) {
            return;
        }
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        if (haAddon.isHaEnabled()) {
            haAddon.propagateAllowedRemoteUrlsSync();
        }
    }

    // Computes SHA-256 checksum for the given string content.
    private static String computeSha256FromString(String content) {
        return ChecksumUtils.calculateSha2(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Autowired
    public void setRepositoryService(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setConfigsService(ConfigsService configsService) {
        this.configsService = configsService;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }
}
