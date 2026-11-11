package org.artifactory.ph;


import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;
import static org.artifactory.util.PackageTypeConfigFields.TERRAFORM_REGISTRY_URL;

/**
 * Interceptor for handling changes to repository configurations.
 * This interceptor implements only the `onBeforeSave` method.
 * It serves as a pre-commit hook to handle repository configuration changes before they are saved.
 *
 * @author prasannasudharsanr
 */
@Component
@Slf4j
public class PackageRepoConfigChangeInterceptor implements RepoConfigChangeInterceptor {

    private static final String ENABLE_NORMALIZED_VERSION = "enableNormalizedVersion";

    /**
     * Transient marker key written by internal/migration code paths into a repo's
     * {@code packageTypeConfig} map to opt out of the {@link #ENABLE_NORMALIZED_VERSION} override
     * applied by {@link #handleNugetConfigurationChange(RepoConfigChange)}.
     * <p>
     * This is an in-band signal: the change object itself describes its intent. The interceptor
     * <strong>must</strong> remove this key from the config before the change is persisted so the
     * marker never reaches the database.
     * <p>
     * The {@code __} prefix is intentional: it is reserved for internal book-keeping and must not
     * collide with any real package-type configuration key.
     */
    public static final String SKIP_NORMALIZATION_OVERRIDE_MARKER = "__skipNormalizationOverride";

    private final Map<RepoType, Consumer<RepoConfigChange>> handlers = new EnumMap<>(RepoType.class);

    public PackageRepoConfigChangeInterceptor() {
        // register package specific handler
        handlers.put(RepoType.NuGet, this::handleNugetConfigurationChange);
        handlers.put(RepoType.HuggingFaceML, this::handleHuggingFaceConfigurationChange);
        handlers.put(RepoType.Terraform, this::handleTerraformConfigurationChange);
        handlers.put(RepoType.AIEditorExtensions, this::handleAiEditorExtensionsConfigurationChange);
    }

    private void handleTerraformConfigurationChange(RepoConfigChange repoConfigChange) {
        RepoConfig newRepoConfig = repoConfigChange.getNewRepoConfig();
        if (newRepoConfig != null) {
            String registryUrl = newRepoConfig.getPackageTypeConfig().get(TERRAFORM_REGISTRY_URL.getKey());
            if (("https://registry.terraform.io".equals(registryUrl) ||
                    "https://registry.opentofu.org".equals(registryUrl) ||
                    "https://tf.app.wiz.io".equals(registryUrl)) &&
                    newRepoConfig.getRepoTypeConfig() instanceof RemoteRepositoryRepoTypeConfig remoteRepoConfig) {
                remoteRepoConfig.setBypassHeadRequests(true);
            }
        }
    }

    /**
     * Intercepts the repository configuration changes before they are saved.
     *
     * @param mutableRepoConfigChanges a map of repository details type to a list of repository configuration changes
     */
    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        ofNullable(mutableRepoConfigChanges)
                .map(Map::values)
                .stream()
                .flatMap(Collection::stream)
                .flatMap(Collection::stream)
                .forEach(this::handleRepoConfigChange);
    }

    private void handleRepoConfigChange(RepoConfigChange configChange) {
        Optional<RepoType> packageType = getPackageType(configChange);
        // handle package specific configuration changes
        packageType.map(handlers::get).ifPresent(handler -> handler.accept(configChange));
    }

    private Optional<RepoType> getPackageType(RepoConfigChange change) {
        return ofNullable(change).map(RepoConfigChange::getNewRepoConfig)
                .map(RepoConfig::getPackageType);
    }

    /**
     * Handles the configuration changes for NuGet repositories.
     * If the action is an <b>update</b>, it ensures that the `ENABLE_NORMALIZED_VERSION` setting is carried over
     * from the old configuration to the new one silently.
     *
     * @param change the repository configuration change
     */
    private void handleNugetConfigurationChange(RepoConfigChange change) {
        if (isUpdateAction(change)) {
            log.debug("Executing NuGet update configuration interceptor for repository {}", change.getRepoKey());
            // Internal/migration code paths can request that the enableNormalizedVersion override
            // be skipped by writing SKIP_NORMALIZATION_OVERRIDE_MARKER alongside the real value in
            // the same packageTypeConfig update. The marker is consumed (removed) here so it never
            // reaches the database.
            if (consumeSkipNormalizationOverrideMarker(change)) {
                log.info("Skip-normalization-override marker detected for repository {} - "
                        + "preserving enableNormalizedVersion as set by caller", change.getRepoKey());
                return;
            }
            overrideEnableNormalizationConfiguration(change);
        }
    }

    /**
     * Returns {@code true} if the caller has tagged this update with the
     * {@link #SKIP_NORMALIZATION_OVERRIDE_MARKER} marker in {@code newRepoConfig.getPackageTypeConfig()}.
     * <p>
     * Importantly, the marker is <em>removed</em> from the config map before this method returns so it
     * is never persisted. This is safe because {@code packageTypeConfig} is a mutable {@code Map}
     * owned by the {@code RepoConfigChange}'s {@code newRepoConfig}, which is the same instance the
     * downstream save path will write to the DB.
     *
     * @param change the in-flight repo config change; may be null
     * @return {@code true} if the marker was present (and was consumed)
     */
    private boolean consumeSkipNormalizationOverrideMarker(RepoConfigChange change) {
        Map<String, String> newConfig = ofNullable(change)
                .map(RepoConfigChange::getNewRepoConfig)
                .map(RepoConfig::getPackageTypeConfig)
                .orElse(null);
        if (newConfig == null) {
            return false;
        }
        return "true".equals(newConfig.remove(SKIP_NORMALIZATION_OVERRIDE_MARKER));
    }

    /**
     * Handles the configuration changes for HuggingFaceML repositories.
     * If the action is a <b>create</b>, it enforces the repository layout to be `frogml`.
     * In case of an <b>update</b>, and changed value for FORCE_FROG_ML_LAYOUT exception is thrown.
     */
    private void handleHuggingFaceConfigurationChange(RepoConfigChange change) {
        boolean isDevMode = ConstantValues.dev.getBoolean();
        if (isCreateAction(change)) {
            log.debug("Executing HuggingFaceML create configuration interceptor for repository {}",
                    change.getRepoKey());
            ofNullable(change.getNewRepoConfig())
                    .ifPresent(config -> config.getPackageTypeConfig()
                            .put(IS_ML_REPO_LAYOUT,
                                    ConstantValues.huggingFaceMLEnableFrogMLLayout.getString()));
        } else if (isUpdateAction(change)) {
            log.debug("Executing HuggingFaceML update configuration interceptor for repository {}",
                    change.getRepoKey());
            RepoConfig oldRepoConfig = change.getOldRepoConfig();
            RepoConfig newRepoConfig = change.getNewRepoConfig();

            ofNullable(oldRepoConfig)
                    .map(RepoConfig::getPackageTypeConfig)
                    .map(config -> config.get(IS_ML_REPO_LAYOUT))
                    .ifPresent(value -> {
                        if (newRepoConfig != null && newRepoConfig.getPackageTypeConfig() != null &&
                                (!isDevMode &&
                                        !value.equals(newRepoConfig.getPackageTypeConfig().get(IS_ML_REPO_LAYOUT)))) {
                            throw new IllegalArgumentException(
                                    "Cannot change field '" + IS_ML_REPO_LAYOUT + "' for " +
                                            "repository: " + change.getRepoKey());
                        }
                    });
        }
    }

    private void handleAiEditorExtensionsConfigurationChange(RepoConfigChange change) {
        boolean isCreate = isCreateAction(change);
        boolean isUpdate = isUpdateAction(change);
        RepoConfig newRepoConfig = change.getNewRepoConfig();
        if (!(Objects.requireNonNull(newRepoConfig).getRepoTypeConfig()
                instanceof RemoteRepositoryRepoTypeConfig remoteRepoConfig)) {
            log.debug("Ai Editor Extensions config skipped for non-remote repo '{}'", change.getRepoKey());
            return;
        }
        if (isCreate) {
            log.debug("Executing AI-Editor Extensions create configuration interceptor for repository {}",
                    change.getRepoKey());
            remoteRepoConfig.setBypassHeadRequests(true);
            return;
        }
        if (isUpdate && change.getOldRepoConfig() != null) {
            log.debug("Executing AI-Editor Extensions update configuration interceptor for repository {}",
                    change.getRepoKey());
            RemoteRepositoryRepoTypeConfig oldRemoteRepoConfig =
                    (RemoteRepositoryRepoTypeConfig) change.getOldRepoConfig().getRepoTypeConfig();
            boolean oldValue = oldRemoteRepoConfig != null && oldRemoteRepoConfig.isBypassHeadRequests();
            boolean newValue = remoteRepoConfig.isBypassHeadRequests();
            // Always enforce 'bypassHeadRequests' to true for VS Code remote repositories in update action
            remoteRepoConfig.setBypassHeadRequests(true);
            // Throw an exception only if there is an explicit attempt to change from true to false.
            if (oldValue && !newValue) {
                String message = String.format(
                        "Changing 'bypass Head Requests' is not allowed for VS Code remote repository: %s",
                        change.getRepoKey());
                log.warn(message);
                throw new IllegalArgumentException(message);
            }
        }
    }

    private boolean isUpdateAction(RepoConfigChange change) {
        return change.getConfigAction() == REPO_UPDATE
                && !isNull(change.getNewRepoConfig())
                && !isNull(change.getOldRepoConfig());
    }

    private boolean isCreateAction(RepoConfigChange change) {
        return change.getConfigAction() == REPO_CREATE && !isNull(change.getNewRepoConfig());
    }

    /**
     * Overrides the configuration changes for NuGet repositories.
     * If the old configuration contains the `ENABLE_NORMALIZED_VERSION` setting, it is copied to the new configuration.
     * Otherwise, the setting is removed from the new configuration.
     *
     * @param change the repository configuration change
     */
    private void overrideEnableNormalizationConfiguration(RepoConfigChange change) {
        RepoConfig oldRepoConfig = change.getOldRepoConfig();
        RepoConfig newRepoConfig = change.getNewRepoConfig();

        ofNullable(oldRepoConfig)
                .map(RepoConfig::getPackageTypeConfig)
                .map(config -> config.get(ENABLE_NORMALIZED_VERSION))
                .ifPresentOrElse(
                        configValue -> resetNormalizationFlagInNewConfig(configValue, newRepoConfig),
                        () -> removeKeyInNewConfigIfPresent(newRepoConfig));
    }

    /**
     * Overrides the `ENABLE_NORMALIZED_VERSION` flag in the new repository configuration with the old value from the
     * repository.
     *
     * @param configValue   the value to set for the `ENABLE_NORMALIZED_VERSION` flag
     * @param newRepoConfig the new repository configuration
     */

    private void resetNormalizationFlagInNewConfig(String configValue, RepoConfig newRepoConfig) {
        ofNullable(newRepoConfig)
                .ifPresent(config -> config.getPackageTypeConfig()
                        .put(ENABLE_NORMALIZED_VERSION, configValue));
    }

    /**
     * Removes the `ENABLE_NORMALIZED_VERSION` key from the new repository configuration if it is present.
     *
     * @param newRepoConfig the new repository configuration
     */

    private void removeKeyInNewConfigIfPresent(RepoConfig newRepoConfig) {
        ofNullable(newRepoConfig)
                .ifPresent(config -> config.getPackageTypeConfig()
                        .remove(ENABLE_NORMALIZED_VERSION));
    }
}