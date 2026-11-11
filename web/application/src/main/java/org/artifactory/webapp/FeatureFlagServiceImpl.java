package org.artifactory.webapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.web.FeatureFlagService;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.io.checksum.ChecksumUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static org.artifactory.entitlement.EntitlementType.AI_CATALOG;
import static org.artifactory.entitlement.EntitlementType.PACKAGES_IDE_EXTENSION;
import static org.artifactory.util.InternalStringUtils.capitalize;

/**
 * @author Dvir Twina
 */
@Service
@Slf4j
public class FeatureFlagServiceImpl implements FeatureFlagService {

    private static final Set<ConstantValues> IDE_PLUGIN_FLAGS = EnumSet.of(
            ConstantValues.jetbrainsPluginsSetMeUpRepositoryEnabled,
            ConstantValues.jetbrainsPluginsRepositoryEnabled,
            ConstantValues.aieditorExtensionsSetMeUpRepositoryEnabled,
            ConstantValues.aieditorExtensionsRepositoryEnabled
    );

    private static final Set<ConstantValues> AI_CATALOG_ENTITLEMENT_FLAGS = EnumSet.of(
            ConstantValues.skillsUIXrayIntegration
    );

    /**
     * When adding feature flags to this list, each one should be run by AppSec team to make sure it's safe.
     */
    private static final Set<ConstantValues> FEATURE_FLAGS = EnumSet.of(
            ConstantValues.policyCleanupBuildEnabled,
            ConstantValues.retentionEnabled,
            ConstantValues.treeBrowserStreamEnabled,
            ConstantValues.treeBrowserRuntimeAlertEnabled,
            ConstantValues.treeBrowserNewPageWithSearchEnabled,
            ConstantValues.policyCleanupPackageEnabled,
            ConstantValues.policyArchivePackageEnabled,
            ConstantValues.policyRestorePackageEnabled,
            ConstantValues.policyCleanupBundleEnabled,
            ConstantValues.nimModelRepositoryEnabled,
            ConstantValues.nimModelSetMeUpRepositoryEnabled,
            ConstantValues.hexRepositoryEnabled,
            ConstantValues.packagesPackagesListNewUi,
            ConstantValues.packagesPackagesDetailsNewUi,
            ConstantValues.packagesOnemodel,
            ConstantValues.jemRepositoryEnabled,
            ConstantValues.repositoriesRemoteEnabletokenbearercheckbox,
            ConstantValues.curationFailOpenUiEnabled,
            ConstantValues.huggingFaceMLVirtualEnabled,
            ConstantValues.bazelModulesRepositoryEnabled,
            ConstantValues.bazelModulesSetMeUpRepositoryEnabled,
            ConstantValues.debianDscLocalRepoSupportEnabled,
            ConstantValues.downloaduploadDirectfromartifactory,
            ConstantValues.repositoriesPageV2,
            ConstantValues.nugetPackageHandlerSupport,
            ConstantValues.jetbrainsPluginsSetMeUpRepositoryEnabled,
            ConstantValues.jetbrainsPluginsRepositoryEnabled,
            ConstantValues.aieditorExtensionsSetMeUpRepositoryEnabled,
            ConstantValues.aieditorExtensionsRepositoryEnabled,
            ConstantValues.setMeUpApi,
            ConstantValues.nixRepositoryPackagesEnabled,
            ConstantValues.nixSetMeUpRepositoryEnabled,
            ConstantValues.luaRocksRepositoryPackagesEnabled,
            ConstantValues.luaRocksSetMeUpRepositoryEnabled,
            ConstantValues.skillsRepositoryPackagesEnabled,
            ConstantValues.skillsSetMeUpRepositoryEnabled,
            ConstantValues.skillsUIXrayIntegration,
            ConstantValues.condaShardsEnabled,
            ConstantValues.repositoryMigrationToolEnabled,
            ConstantValues.agentPluginsRepositoryPackagesEnabled,
            ConstantValues.agentPluginsSetMeUpRepositoryEnabled,
            ConstantValues.agentPackagesRepositoryPackagesEnabled,
            ConstantValues.agentPackagesSetMeUpRepositoryEnabled,
            ConstantValues.repositoriesFederationsAttributeEnabled,
            ConstantValues.rtuiMfeControlledRolloutEnabled,
            ConstantValues.retentionMfeControlledRolloutEnabled,
            ConstantValues.packagesMfeControlledRolloutEnabled,
            ConstantValues.buildinfomfeMfeControlledRolloutEnabled
            );

    private String allFlagsJson = "";
    private String allFlagsHash = "";
    boolean isIdeEntitled = false;
    boolean isAICatalogEntitled = false;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @PostConstruct
    public void init() {
        recalculate();
    }

    private void recalculate() {
        isIdeEntitled = isEntitledIdeExt();
        isAICatalogEntitled = isEntitledAICatalog();
        log.trace("IDE plugin feature flags are {}", isIdeEntitled ? "enabled" : "disabled - not entitled");

        Map<String, Boolean> flags = FEATURE_FLAGS.stream().collect(Collectors.toMap(
                flag -> replaceDotWithConsecutiveCapitalLetters(flag.getPropertyName()),
                this::resolveFlag)
        );

        try {
            allFlagsJson = OBJECT_MAPPER.writeValueAsString(flags);
        } catch (JsonProcessingException e) {
            log.error("Could not write Feature Flags object as a json: ", e);
        }

        log.trace("Feature Flags: {}", allFlagsJson);
        allFlagsHash = ChecksumUtils.getChecksum(IOUtils.toInputStream(allFlagsJson, StandardCharsets.UTF_8),
                ChecksumType.md5);
    }

    private static boolean isEntitledIdeExt() {
        return ContextHelper.get().beanForType(AddonsManager.class).isEntitled(PACKAGES_IDE_EXTENSION)
                || ConstantValues.forcePluginsRepositoryEnabled.getBoolean();
    }

    private static boolean isEntitledAICatalog() {
        return ContextHelper.get().beanForType(AddonsManager.class).isEntitled(AI_CATALOG);
    }

    /**
     * Returns a string representation of a json containing all available feature flags and their values.
     * Note: currently the only feature flag supported is Treebrowser Stream.
     * in the future it will be extended to support all other feature flags.
     */
    @Override
    public String getAllFeatureFlags() {
        if (isEntitlementUnChanged()) {
            return allFlagsJson;
        }
        recalculate();
        return allFlagsJson;
    }

    @Override
    public String featureFlagsHash() {
        if (isEntitlementUnChanged()) {
            return allFlagsHash;
        }
        recalculate();
        return allFlagsHash;
    }

    private boolean resolveFlag(ConstantValues flag) {
        if (IDE_PLUGIN_FLAGS.contains(flag)) {
            return isIdeEntitled && flag.getBoolean();
        } else if (AI_CATALOG_ENTITLEMENT_FLAGS.contains(flag)) {
            return isAICatalogEntitled && flag.getBoolean();
        }
        return flag.getBoolean();
    }

    private boolean isEntitlementUnChanged() {
        return isEntitledIdeExt() == isIdeEntitled
                && isEntitledAICatalog() == isAICatalogEntitled;
    }

    private String replaceDotWithConsecutiveCapitalLetters(String flagName) {
        String[] parts = flagName.split("\\.");
        return range(0, parts.length).
                mapToObj(i -> i == 0 ? parts[i] : capitalize(parts[i]))
                .collect(joining());
    }
}
