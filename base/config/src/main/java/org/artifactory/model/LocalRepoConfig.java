package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.DownloadRedirectRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.util.LocalDefaultPackageConfigSetter;
import org.artifactory.util.ParamValidationUtils;
import org.jfrog.common.config.diff.DiffIgnore;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Local repository configurations
 *
 * @author shayb
 */
@Valid
@Getter
@GenerateDiffFunction
public class LocalRepoConfig implements LocalConfig {

    @NotUnicode
    private final String key;
    @Valid
    @Setter(AccessLevel.PROTECTED)
    private BaseConfig baseConfig;
    @Valid
    @Setter(AccessLevel.PROTECTED)
    private LocalRepositoryRepoTypeConfig repoTypeConfig = new LocalRepositoryRepoTypeConfig();
    private final RepoType packageType;
    /**
     * Live map of package-type config entries. {@code @DiffIgnore} suppresses the atomic blob diff; per-key diffs
     * are emitted at {@code packageTypeConfig»<key>} by the repo-config diff layer.
     */
    @Setter(AccessLevel.PROTECTED)
    @DiffIgnore
    private Map<String, String> packageTypeConfig = new HashMap<>();
    @Valid
    @Setter(AccessLevel.PROTECTED)
    private SecurityConfig securityConfig = new SecurityConfig();
    @DiffIgnore
    @Setter
    @JsonIgnore
    private long modification;
    @DiffIgnore
    @JsonIgnore
    @Setter
    private long creation;

    /**
     * Federation export data - only populated when converting from FederatedRepoConfig for export.
     * This field preserves federation details (members, proxy, disableProxy) when exporting
     * federated repos as local repos. It's ignored during normal operations.
     * Use {@link #fromFederated(FederatedRepoConfig)} to create a LocalRepoConfig with federation data.
     */
    @DiffIgnore
    @Setter(AccessLevel.PROTECTED)
    @Getter
    private FederationExportData federation;

    @JsonCreator
    LocalRepoConfig(@JsonProperty("key") String repoKey, @JsonProperty("packageType") RepoType packageType) {
        this.key = ParamValidationUtils.requireNonBlank(repoKey, "Repository key cannot be blank");
        this.packageType = Objects.requireNonNull(packageType, "Repository package type cannot be null");
        this.baseConfig = new BaseConfig();
        LocalDefaultPackageConfigSetter.setDefaultValues(packageType, this.packageTypeConfig);
        this.repoTypeConfig.setDownloadRedirectConfig(new DownloadRedirectRepoConfig());
    }

    /**
     * Factory method enabling exclusive access to instance creation
     *
     * @param repoKey     the repository key
     * @param packageType the repository package type
     * @return Local repo configuration
     * @throws IllegalArgumentException if {@code repoKey} is blank
     * @throws NullPointerException     if {@code packageType} is null
     */
    public static LocalRepoConfig create(String repoKey, RepoType packageType) {
        return new LocalRepoConfig(repoKey, packageType);
    }

    /**
     * Factory method enabling exclusive access to deep copy a local repo config
     *
     * @param localRepoConfig the local repo config to be copied
     * @return a copy of the local repo config
     * @throws NullPointerException if {@code localRepoConfig} is null
     */
    static LocalRepoConfig copy(LocalRepoConfig localRepoConfig) {
        Objects.requireNonNull(localRepoConfig, "Local repo config cannot be null");
        return getLocalRepoConfig(localRepoConfig.getKey(), localRepoConfig.getPackageType(),
                localRepoConfig.getBaseConfig(), localRepoConfig.getRepoTypeConfig(),
                localRepoConfig.getPackageTypeConfig(), localRepoConfig.getSecurityConfig(),
                localRepoConfig.getModification(), localRepoConfig.getCreation());
    }

    /**
     * Creates a new local repo config from federated repo config.
     * Preserves federation details (members, proxy, disableProxy) in the federation field for export.
     *
     * @param federatedRepoConfig the federated repo config to be copied
     * @return a new local repo config with the same content as federatedRepoConfig, including federation details
     * @throws NullPointerException if {@code federatedRepoConfig} is null
     */
    public static LocalRepoConfig fromFederated(@NonNull FederatedRepoConfig federatedRepoConfig) {
        LocalRepoConfig localRepoConfig = getLocalRepoConfig(federatedRepoConfig.getKey(),
                federatedRepoConfig.getPackageType(),
                federatedRepoConfig.getBaseConfig(), federatedRepoConfig.getRepoTypeConfig(),
                federatedRepoConfig.getPackageTypeConfig(), federatedRepoConfig.getSecurityConfig(),
                federatedRepoConfig.getModification(), federatedRepoConfig.getCreation());

        // Extract and preserve federation details for export
        FederatedRepositoryRepoTypeConfig federatedRepoTypeConfig = federatedRepoConfig.getRepoTypeConfig();
        if (federatedRepoTypeConfig != null) {
            FederationExportData federationData = new FederationExportData();

            // Copy members (preserve original list)
            if (federatedRepoTypeConfig.getFederatedMembers() != null) {
                List<FederatedMember> members = new ArrayList<>();
                for (FederatedMember member : federatedRepoTypeConfig.getFederatedMembers()) {
                    members.add(new FederatedMember(member));
                }
                federationData.setMembers(members);
            }

            // Copy proxy settings
            federationData.setProxy(federatedRepoTypeConfig.getProxyRef());
            federationData.setDisableProxy(federatedRepoTypeConfig.isDisableProxy());

            localRepoConfig.setFederation(federationData);
        }

        return localRepoConfig;
    }

    private static LocalRepoConfig getLocalRepoConfig(String key, RepoType packageType, BaseConfig baseConfig,
            LocalRepositoryRepoTypeConfig repoTypeConfig, Map<String, String> packageTypeConfig,
            SecurityConfig securityConfig, long modification, long creation) {
        LocalRepoConfig repoConfig = new LocalRepoConfig(key,
                packageType);
        repoConfig.setBaseConfig(new BaseConfig(baseConfig));
        repoConfig.setRepoTypeConfig(new LocalRepositoryRepoTypeConfig(repoTypeConfig));
        repoConfig.setPackageTypeConfig(new HashMap<>(packageTypeConfig));
        repoConfig.setSecurityConfig(new SecurityConfig(securityConfig));
        repoConfig.setModification(modification);
        repoConfig.setCreation(creation);
        return repoConfig;
    }

    @Override
    public RepoDetailsType getRepoType() {
        return RepoDetailsType.LOCAL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LocalRepoConfig that = (LocalRepoConfig) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public String toString() {
        return key;
    }

    /**
     * Federation export data structure.
     * Contains federation details (members, proxy, disableProxy) that are preserved
     * when exporting federated repos as local repos.
     */
    @Data
    @NoArgsConstructor
    public static class FederationExportData {
        private List<FederatedMember> members;
        private String proxy;
        private boolean disableProxy;
    }

    @Override
    public boolean isFederated() {
        return baseConfig.isFederated();
    }
}
