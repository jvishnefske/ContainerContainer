package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.util.ParamValidationUtils;
import org.artifactory.util.VirtualDefaultPackageConfigSetter;
import org.jfrog.common.config.diff.DiffIgnore;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Virtual repository configurations
 *
 * @author shayb
 */
@Valid
@Getter
@GenerateDiffFunction
public class VirtualRepoConfig implements RepoConfig {
    @NotUnicode
    private final String key;
    @Valid
    @Setter(AccessLevel.PRIVATE)
    private BaseConfig baseConfig;
    @Valid
    @Setter(AccessLevel.PRIVATE)
    private VirtualRepositoryRepoTypeConfig repoTypeConfig = new VirtualRepositoryRepoTypeConfig();
    private final RepoType packageType;
    /** @see LocalRepoConfig#packageTypeConfig */
    @Setter(AccessLevel.PRIVATE)
    @DiffIgnore
    private Map<String, String> packageTypeConfig = new HashMap<>();
    @Valid
    @Setter(AccessLevel.PRIVATE)
    private SecurityConfig securityConfig = new SecurityConfig();
    @DiffIgnore
    @JsonIgnore
    @Setter
    private long modification;
    @DiffIgnore
    @JsonIgnore
    @Setter
    private long creation;

    @JsonCreator
    private VirtualRepoConfig(@JsonProperty("key") String repoKey, @JsonProperty("packageType") RepoType packageType) {
        this.key = ParamValidationUtils.requireNonBlank(repoKey, "Repository key cannot be blank");
        this.packageType = Objects.requireNonNull(packageType, "Repository package type cannot be null");
        this.baseConfig = new BaseConfig();
        VirtualDefaultPackageConfigSetter.setDefaultValues(packageType, packageTypeConfig);
    }

    /**
     * Factory method enabling exclusive access to instance creation
     *
     * @param repoKey     the repository key
     * @param packageType the repository package type
     * @return Virtual repo configuration
     * @throws IllegalArgumentException if {@code repoKey} is blank
     * @throws NullPointerException if {@code packageType} is null
     */
    public static VirtualRepoConfig create(String repoKey, RepoType packageType) {
        return new VirtualRepoConfig(repoKey, packageType);
    }

    /**
     * Factory method enabling exclusive access to deep copy a virtual repo config
     *
     * @param virtualRepoConfig the virtual repo config to be copied
     * @return a copy of the virtual repo config
     * @throws NullPointerException if {@code virtualRepoConfig} is null
     */
    static VirtualRepoConfig copy(VirtualRepoConfig virtualRepoConfig) {
        Objects.requireNonNull(virtualRepoConfig, "Virtual repo config cannot be null");
        VirtualRepoConfig repoCopy =
                new VirtualRepoConfig(virtualRepoConfig.getKey(), virtualRepoConfig.getPackageType());
        repoCopy.setBaseConfig(new BaseConfig(virtualRepoConfig.getBaseConfig()));
        repoCopy.setRepoTypeConfig(new VirtualRepositoryRepoTypeConfig(virtualRepoConfig.getRepoTypeConfig()));
        repoCopy.setPackageTypeConfig(new HashMap<>(virtualRepoConfig.getPackageTypeConfig()));
        repoCopy.setSecurityConfig(new SecurityConfig(virtualRepoConfig.getSecurityConfig()));
        repoCopy.setModification(virtualRepoConfig.getModification());
        repoCopy.setCreation(virtualRepoConfig.getCreation());
        return repoCopy;
    }

    @Override
    public boolean isReal() {
        return false;
    }

    @Override
    public RepoDetailsType getRepoType() {
        return RepoDetailsType.VIRTUAL;
    }

    /**
     * Checks if this virtual repository is federated.
     * Delegates to BaseConfig.isFederated() which checks for federation members.
     *
     * @return true if this virtual repo has federation configured
     */
    @Override
    public boolean isFederated() {
        return baseConfig.isFederated();
    }

    public boolean removeRepository(String repoRef) {
        return repoTypeConfig.getRepositoryRefs().remove(repoRef);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        VirtualRepoConfig that = (VirtualRepoConfig) o;
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
}
