package org.artifactory.repo.migration.mappers;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypes;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.repo.RepositoryConfigSource;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.RealRepoConfigType;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.SecurityConfig;
import org.artifactory.model.XrayRepoConfig;
import org.artifactory.repo.CdnRedirectRepoConfig;
import org.artifactory.repo.DownloadRedirectRepoConfig;
import org.artifactory.repo.federated.FederatedMember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Boolean.parseBoolean;
import static org.artifactory.descriptor.repo.RepoType.P2;
import static org.artifactory.util.PackageTypeConfigFields.ENABLE_NUGET_NORMALIZED_VERSION;
import static org.artifactory.util.PackageTypeConfigFields.ENCRYPT_STATES;

@Slf4j
@UtilityClass
public class RepoConfigMigrationPatchUtils {
    public static void buildBaseConfig(@Nonnull BaseConfig baseConfig, Map<String, Object> newValues,
            CentralConfigDescriptor descriptor) {
        for (Map.Entry<String, Object> newValue : newValues.entrySet()) {
            try {
                switch (newValue.getKey()) {
                    case "description" -> baseConfig.setDescription(customValueOf(newValue.getValue()));
                    case "notes" -> baseConfig.setNotes(customValueOf(newValue.getValue()));
                    case "repoLayout" -> {
                        String repoLayoutRef = customValueOf(newValue.getValue());
                        if (descriptor.getRepoLayout(repoLayoutRef) == null) {
                            log.debug("Repo layout '{}' does not exist in the config descriptor", repoLayoutRef);
                        } else {
                            baseConfig.setRepoLayoutRef(repoLayoutRef);
                        }
                    }
                    case "includesPattern" -> baseConfig.setIncludesPattern(customValueOf(newValue.getValue()));
                    case "excludesPattern" -> baseConfig.setExcludesPattern(customValueOf(newValue.getValue()));
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("No values under " + newValue.getKey());
            }
        }
    }

    @Nonnull
    public static SecurityConfig buildSecurityConfig(SecurityConfig securityConfig, Map<String, Object> newValues,
            CentralConfigDescriptor descriptor) {
        for (Map.Entry<String, Object> newValue : newValues.entrySet()) {
            try {
                switch (newValue.getKey()) {
                    case "xray" -> handleXrayConfig(securityConfig, newValue);
                    case "primaryKeyPairDescriptor" ->
                            handlePrimaryKeyPairDescriptor(securityConfig, descriptor, newValue);
                    case "secondaryKeyPairDescriptor" ->
                            handleSecondaryKeyPairDescriptor(securityConfig, descriptor, newValue);
                    case "signedUrlTtl" -> securityConfig.setSignedUrlTtl((Integer) newValue.getValue());
                    case "keyPair" -> securityConfig.setKeyPair(customValueOf(newValue.getValue()));
                    default -> { //Do nothing
                    }
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("No values under " + newValue.getKey());
            }
        }
        return securityConfig;
    }

    private static void handleXrayConfig(SecurityConfig securityConfig, Map.Entry<String, Object> newValue) {
        XrayRepoConfig xrayRepoConfig = new XrayRepoConfig();
        if (securityConfig.getXrayConfig() != null) {
            xrayRepoConfig = securityConfig.getXrayConfig();
        }
        if (newValue.getValue() != null) {
            Map<?, ?> newXrayRepoConfig = (Map<?, ?>) newValue.getValue();
            if (newXrayRepoConfig.get("enabled") != null) {
                xrayRepoConfig.setEnabled((Boolean) newXrayRepoConfig.get("enabled"));
            }
            if (newXrayRepoConfig.get("dataTtl") != null) {
                xrayRepoConfig.setDataTtl((Integer) newXrayRepoConfig.get("dataTtl"));
            }
            if (newXrayRepoConfig.get("xrayNotAvailableMode") != null) {
                xrayRepoConfig.xrayNotAvailableModeFromString(
                        (String) newXrayRepoConfig.get("xrayNotAvailableMode"));
            }
        }
        securityConfig.setXrayConfig(xrayRepoConfig);
    }

    private static void handlePrimaryKeyPairDescriptor(SecurityConfig securityConfig,
            CentralConfigDescriptor descriptor, Map.Entry<String, Object> newValue) {
        String keyPairRef = (String) newValue.getValue();
        if (descriptor.getKeyPair(keyPairRef) == null) {
            log.debug("Primary key pair '{}' does not exist in the config descriptor", keyPairRef);
        } else {
            securityConfig.setPrimaryKeyPairRef((String) newValue.getValue());
        }
    }

    private static void handleSecondaryKeyPairDescriptor(SecurityConfig securityConfig,
            CentralConfigDescriptor descriptor, Map.Entry<String, Object> newValue) {
        String keyPairRef = (String) newValue.getValue();
        if (descriptor.getKeyPair(keyPairRef) == null) {
            log.debug("Secondary key pair '{}' does not exist in the config descriptor", keyPairRef);
        } else {
            securityConfig.setSecondaryKeyPairRef((String) newValue.getValue());
        }
    }

    public static void externalDependenciesToConfig(@Nonnull Map<String, String> packageTypeConfig,
            Map<String, Object> newValues) {
        if (newValues.get("externalDependencies") != null) {
            Map<?, ?> externalDependenciesNewValues =
                    (Map<?, ?>) newValues.get("externalDependencies");
            for (Map.Entry<?, ?> externalDependenciesNewValue :
                    externalDependenciesNewValues.entrySet()) {
                if ("enabled".equals(externalDependenciesNewValue.getKey())) {
                    packageTypeConfig.put("externalDependenciesEnabled",
                            String.valueOf(externalDependenciesNewValue.getValue()));
                } else if ("patterns".equals(externalDependenciesNewValue.getKey())) {
                    Map<String, Object> properties =
                            (Map<String, Object>) externalDependenciesNewValue.getValue();
                    List<String> patternsList = properties.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(Map.Entry::getValue)
                            .map(String.class::cast)
                            .toList();
                    RepositoryConfigSource.entityToFlatMapKeys(patternsList, "externalDependenciesPatterns",
                            packageTypeConfig);
                }
            }
        }
    }

    /**
     * Copies the package-type specific keys of {@code repoType} from a YAML patch ({@code newValues}) into
     * {@code packageTypeConfig}; other keys are ignored. For {@code Generic} the only such key is
     * {@code pluginPackageType}, which names a plugin-defined package type and is validated when the
     * configuration is saved.
     */
    public static void buildLocalPackageTypeConfig(Map<String, String> packageTypeConfig,
            RepoType repoType,
            Map<String, Object> newValues, RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor) {
        for (Map.Entry<String, Object> newValue : newValues.entrySet()) {
            try {
                switch (repoType) {
                    case Maven, Ivy, Gradle, P2, SBT -> {
                        addValueToPackageTypeConfigIfNeeded("suppressPomConsistencyChecks", packageTypeConfig,
                                newValue);
                        addValueToPackageTypeConfigIfNeeded("forceMavenAuthentication", packageTypeConfig, newValue);
                        if (P2.equals(repoType)) {
                            addValueToPackageTypeConfigIfNeeded("forceP2Authentication", packageTypeConfig, newValue);
                        }
                    }
                    case Cargo -> {
                        addValueToPackageTypeConfigIfNeeded("cargoAnonymousAccess", packageTypeConfig, newValue);
                        addValueToPackageTypeConfigIfNeeded("cargoInternalIndex", packageTypeConfig, newValue);
                    }
                    case Composer -> addValueToPackageTypeConfigIfNeeded("enableComposerV1Indexing", packageTypeConfig,
                            newValue);
                    case Debian -> {
                        addValueToPackageTypeConfigIfNeeded("debianTrivialLayout", packageTypeConfig, newValue);
                        addValueToPackageTypeConfigIfNeeded("ddebSupported", packageTypeConfig, newValue);
                    }
                    case Docker -> {
                        addValueToPackageTypeConfigIfNeeded("dockerTagRetention", packageTypeConfig, newValue);
                        addValueToPackageTypeConfigIfNeeded("dockerApiVersion", packageTypeConfig, newValue);
                        addValueToPackageTypeConfigIfNeeded("blockPushingSchema1", packageTypeConfig, newValue);
                        addValueToPackageTypeConfigIfNeeded("maxUniqueTags", packageTypeConfig, newValue);
                    }
                    case OCI, HelmOCI -> {
                        addValueToPackageTypeConfigIfNeeded("dockerTagRetention", packageTypeConfig, newValue);
                        addValueToPackageTypeConfigIfNeeded("maxUniqueTags", packageTypeConfig, newValue);
                    }
                    case NuGet -> {
                        addValueToPackageTypeConfigIfNeeded("forceNugetAuthentication", packageTypeConfig,
                                newValue);
                        setCustomOrGlobalNugetNormalization(packageTypeConfig, newValue,
                                repositoriesGeneralConfigDescriptor);
                    }
                    case YUM -> {
                        addValueToPackageTypeConfigIfNeeded("calculateYumMetadata", packageTypeConfig, newValue);
                        addValueToPackageTypeConfigIfNeeded("yumRootDepth", packageTypeConfig, newValue);
                        addValueToPackageTypeConfigIfNeeded("enableFileListsIndexing", packageTypeConfig, newValue);
                        addValueToPackageTypeConfigIfNeeded("yumGroupFileNames", packageTypeConfig, newValue);
                    }
                    case Terraform -> addValueToPackageTypeConfigIfNeeded("terraformType", packageTypeConfig, newValue);
                    case TerraformBackend ->
                            addValueToPackageTypeConfigIfNeeded(ENCRYPT_STATES.getKey(), packageTypeConfig, newValue);
                    case Conan -> addValueToPackageTypeConfigIfNeeded("forceConanAuthentication", packageTypeConfig,
                            newValue);
                    // Fork: a Generic repository may name a plugin-defined package type (validated on save).
                    case Generic -> addValueToPackageTypeConfigIfNeeded(PluginPackageTypes.CONFIG_KEY,
                            packageTypeConfig, newValue);
                    default -> {return;}
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("No values under " + newValue.getKey());
            }
        }
    }

    private static void addValueToPackageTypeConfigIfNeeded(String key, Map<String, String> packageTypeConfig,
            Map.Entry<String, Object> entry) {
        if (key.equals(entry.getKey())) {
            packageTypeConfig.put(key, customValueOf(entry.getValue()));
        }
    }

    private static List<String> getFilteredProperties(Object value, Set<String> existingPropertySetKeys) {
        Map<String, Object> properties = (Map<String, Object>) value;
        return properties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .map(String.class::cast)
                .filter(propertySetKey -> {
                    if (!existingPropertySetKeys.contains(propertySetKey)) {
                        log.debug("Property set '{}' does not exist in the config descriptor",
                                propertySetKey);
                        return false;
                    }
                    return true;
                })
                .toList();
    }

    static void setPropertySetRefs(CentralConfigDescriptor descriptor, RealRepoConfigType repositoryRepoTypeConfig,
            Object value) {
        Set<String> existingPropertySetKeys = descriptor.getPropertySets().stream()
                .map(PropertySet::getName)
                .collect(Collectors.toSet());
        repositoryRepoTypeConfig.setPropertySetRefs(
                value == null ? Collections.emptyList() : getFilteredProperties(value, existingPropertySetKeys)
        );
    }

    static void setCdnRedirectRepoConfig(RealRepoConfigType repositoryRepoTypeConfig, Object value) {
        CdnRedirectRepoConfig cdnRedirectRepoConfig = new CdnRedirectRepoConfig();
        cdnRedirectRepoConfig.setEnabled(Boolean.parseBoolean(value.toString()));
        repositoryRepoTypeConfig.setCdnRedirectRepoConfig(cdnRedirectRepoConfig);
    }

    static void setDownloadRedirectRepoConfig(RealRepoConfigType repositoryRepoTypeConfig, Object value) {
        DownloadRedirectRepoConfig downloadRedirectRepoConfig = new DownloadRedirectRepoConfig();
        if (value instanceof Boolean enabled) {
            downloadRedirectRepoConfig.setEnabled(enabled);
        } else {
            downloadRedirectRepoConfig.setEnabled(parseBoolean(((Map<?, ?>) value).get("enabled").toString()));
        }
        repositoryRepoTypeConfig.setDownloadRedirectConfig(downloadRedirectRepoConfig);
    }

    static String customValueOf(Object o) {
        return (o == null) ? "" : o.toString();
    }

    public static void setCustomOrGlobalNugetNormalization(Map<String, String> packageTypeConfig,
            Map.Entry<?, ?> newValue,
            RepositoriesGeneralConfigDescriptor repositoriesGeneralConfigDescriptor) {
        if (ENABLE_NUGET_NORMALIZED_VERSION.getKey().equals(newValue.getKey())) {
            boolean globalNormalizationEnabled =
                    repositoriesGeneralConfigDescriptor.getNuget().isEnableGlobalNormalization();
            packageTypeConfig.put(ENABLE_NUGET_NORMALIZED_VERSION.getKey(),
                    globalNormalizationEnabled ? Boolean.TRUE.toString() : customValueOf(newValue.getValue()));
        }
    }

    /**
     * Parses federation members from various input formats.
     * Checks for members in: "members", "federatedMembers", "federationConfig.members"
     *
     * @param newValues the input map containing member data
     * @return list of FederatedMember or empty list if no members found
     */
    @SuppressWarnings("unchecked")
    public static List<FederatedMember> parseFederationMembers(Map<String, Object> newValues) {
        Object membersObj = newValues.get("members");
        if (membersObj == null) {
            membersObj = newValues.get("federatedMembers");
        }
        if (membersObj == null) {
            Object federationConfigObj = newValues.get("federationConfig");
            if (federationConfigObj instanceof Map) {
                membersObj = ((Map<String, Object>) federationConfigObj).get("members");
            }
        }
        if (membersObj == null) {
            return Collections.emptyList();
        }

        List<FederatedMember> result = new ArrayList<>();
        if (membersObj instanceof Map) {
            ((Map<String, Object>) membersObj).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .map(x -> (Map<String, Object>) x)
                    .forEach(memberMap -> result.add(createFederatedMember(memberMap)));
        } else if (membersObj instanceof List) {
            ((List<?>) membersObj).forEach(member -> {
                if (member instanceof Map) {
                    result.add(createFederatedMember((Map<String, Object>) member));
                }
            });
        }
        return result;
    }

    private static FederatedMember createFederatedMember(Map<String, Object> memberMap) {
        FederatedMember member = new FederatedMember();
        memberMap.forEach((key, value) -> {
            switch (key) {
                case "enabled" -> member.setEnabled((boolean) value);
                case "mode" -> member.setMode(String.valueOf(value));
                case "url" -> member.setUrlOrTemplate(String.valueOf(value));
                default -> { /* ignore unknown keys */ }
            }
        });
        return member;
    }

    /**
     * Parses and sets federation members on any RepoConfig that supports federation.
     * Works with FederatedRepoConfig, RemoteRepoConfig, and VirtualRepoConfig.
     *
     * @param config    the RepoConfig to update
     * @param newValues the input map containing member data
     */
    public static void setFederationMembers(RepoConfig config, Map<String, Object> newValues) {
        List<FederatedMember> members = parseFederationMembers(newValues);
        if (!members.isEmpty()) {
            config.getBaseConfig().getFederationConfig().setMembers(members);
        }
    }
}
