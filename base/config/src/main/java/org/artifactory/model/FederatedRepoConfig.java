package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.DownloadRedirectRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.util.FederatedDefaultPackageConfigSetter;
import org.artifactory.util.ParamValidationUtils;
import org.jfrog.common.config.diff.DiffIgnore;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * Federated repository configurations.
 * The federated repository configurations are similar to a local repository, but it extends the local repository
 * config {@link LocalRepositoryRepoTypeConfig} with additional federation related configurations, see
 * {@link FederatedRepositoryRepoTypeConfig}
 *
 * @author shayb
 */
@Valid
@Getter
@MetadataVersion(metadataVersion = 1)
@GenerateDiffFunction
public class FederatedRepoConfig implements LocalConfig, ProxyRepoConfig {
    @NotUnicode
    @MetadataInfo
    private final String key;
    @Valid
    @Setter(AccessLevel.PRIVATE)
    private BaseConfig baseConfig;
    @Valid
    @Setter(AccessLevel.PRIVATE)
    private FederatedRepositoryRepoTypeConfig repoTypeConfig = new FederatedRepositoryRepoTypeConfig();
    @MetadataInfo(defaultValue = "generic")
    private final RepoType packageType;
    /** @see LocalRepoConfig#packageTypeConfig */
    @Setter(AccessLevel.PUBLIC)
    @DiffIgnore
    @MetadataInfo(skip = true)
    private Map<String, String> packageTypeConfig = new HashMap<>();
    @Valid
    @Setter(AccessLevel.PRIVATE)
    private SecurityConfig securityConfig = new SecurityConfig();
    @DiffIgnore
    @JsonIgnore
    @Setter
    @MetadataInfo(skip = true)
    private long modification;
    @DiffIgnore
    @JsonIgnore
    @Setter
    @MetadataInfo(skip = true)
    private long creation;

    @JsonCreator
    protected FederatedRepoConfig(@JsonProperty("key") String repoKey,
            @JsonProperty("packageType") RepoType packageType) {
        this.key = ParamValidationUtils.requireNonBlank(repoKey, "Repository key cannot be blank");
        this.packageType = Objects.requireNonNull(packageType, "Repository package type cannot be null");
        this.baseConfig = new BaseConfig();
        FederatedDefaultPackageConfigSetter.setDefaultValues(packageType, this.packageTypeConfig);
        this.repoTypeConfig.setDownloadRedirectConfig(new DownloadRedirectRepoConfig());
    }

    /**
     * Factory method enabling exclusive access to instance creation
     *
     * @param repoKey     the repository key
     * @param packageType the repository package type
     * @return Federated repo configuration
     * @throws IllegalArgumentException if {@code repoKey} is blank
     * @throws NullPointerException     if {@code packageType} is null
     */
    public static FederatedRepoConfig create(String repoKey, RepoType packageType) {
        return new FederatedRepoConfig(repoKey, packageType);
    }

    /**
     * Factory method enabling exclusive access to deep copy a federated repo config
     *
     * @param federatedRepoConfig the federated repo config to be copied
     * @return a copy of the federated repo config
     * @throws NullPointerException if {@code federatedRepoConfig} is null
     */
    static FederatedRepoConfig copy(FederatedRepoConfig federatedRepoConfig) {
        Objects.requireNonNull(federatedRepoConfig, "Federated repo config cannot be null");
        FederatedRepoConfig repoCopy =
                new FederatedRepoConfig(federatedRepoConfig.getKey(), federatedRepoConfig.getPackageType());
        repoCopy.setBaseConfig(new BaseConfig(federatedRepoConfig.getBaseConfig()));
        repoCopy.setRepoTypeConfig(new FederatedRepositoryRepoTypeConfig(federatedRepoConfig.getRepoTypeConfig()));
        repoCopy.setPackageTypeConfig(new HashMap<>(federatedRepoConfig.getPackageTypeConfig()));
        repoCopy.setSecurityConfig(new SecurityConfig(federatedRepoConfig.getSecurityConfig()));
        repoCopy.setModification(federatedRepoConfig.getModification());
        repoCopy.setCreation(federatedRepoConfig.getCreation());
        // Ensure federation data is in new location (lazy migration on copy)
        // Create a defensive copy to prevent shared references between original and copy
        List<FederatedMember> originalMembers = federatedRepoConfig.getFederatedMembers();
        if (originalMembers != null) {
            repoCopy.setFederatedMembers(originalMembers.stream()
                    .map(FederatedMember::new)
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
        repoCopy.setModificationDate(federatedRepoConfig.getModificationDate());
        return repoCopy;
    }

    @Override
    public void setProxyRef(String proxyKey) {
        getRepoTypeConfig().setProxyRef(proxyKey);
    }

    @Override
    public String getProxyRef() {
        return getRepoTypeConfig().getProxyRef();
    }

    @Override
    public void setDisableProxy(boolean disable) {
        getRepoTypeConfig().setDisableProxy(disable);
    }

    @Override
    public boolean isProxyDisabled() {
        return getRepoTypeConfig().isDisableProxy();
    }

    @Override
    public RepoDetailsType getRepoType() {
        return RepoDetailsType.FEDERATED;
    }

    @Override
    public boolean isFederated() {
        return true;
    }

    /**
     * Gets federated members with lazy migration support.
     * Reads from new location (baseConfig.federationConfig) first,
     * falls back to old location (repoTypeConfig) for unmigrated repos.
     *
     * @return list of federated members
     */
    @JsonIgnore
    public List<FederatedMember> getFederatedMembers() {
        List<FederatedMember> newLocationMembers = baseConfig.getFederationConfig().getMembers();
        if (newLocationMembers != null && !newLocationMembers.isEmpty()) {
            return newLocationMembers;
        }
        // Fall back to old location for unmigrated repos
        return repoTypeConfig.getFederatedMembers();
    }

    /**
     * Sets federated members with backward compatibility.
     * Writes to both new location (baseConfig.federationConfig) and
     * old location (repoTypeConfig) for API backward compatibility.
     *
     * @param members the federated members to set
     */
    @JsonIgnore
    public void setFederatedMembers(List<FederatedMember> members) {
        baseConfig.getFederationConfig().setMembers(members);
        repoTypeConfig.setFederatedMembers(members); // backward compatibility
    }

    /**
     * Gets modification date with lazy migration support.
     * Reads from new location first, falls back to old location.
     *
     * @return modification date
     */
    @JsonIgnore
    public long getModificationDate() {
        long newLocationDate = baseConfig.getFederationConfig().getModificationDate();
        if (newLocationDate > 0) {
            return newLocationDate;
        }
        return repoTypeConfig.getModificationDate();
    }

    /**
     * Sets modification date with backward compatibility.
     * Writes to both locations.
     *
     * @param modificationDate the modification date to set
     */
    @JsonIgnore
    public void setModificationDate(long modificationDate) {
        baseConfig.getFederationConfig().setModificationDate(modificationDate);
        repoTypeConfig.setModificationDate(modificationDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FederatedRepoConfig that)) {
            return false;
        }
        if (!Objects.equals(this.getFederatedMembers(), that.getFederatedMembers())) {
            return false;
        }
        if (!Objects.equals(this.getRepoTypeConfig().getProxyRef(), that.getRepoTypeConfig().getProxyRef())) {
            return false;
        }
        return this.getModificationDate() == that.getModificationDate() &&
                getKey().equals(that.getKey()) &&
                this.getPackageType() == that.getPackageType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.getFederatedMembers());
    }

    @Override
    public String toString() {
        return key;
    }
}