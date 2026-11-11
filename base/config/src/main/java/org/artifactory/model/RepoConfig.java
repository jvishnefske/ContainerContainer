package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.xray.XrayNotAvailableMode;

import java.util.Map;

import static org.artifactory.repo.xray.XrayNotAvailableMode.USE_SYSTEM_PROPERTY;

/**
 * Interface represents a repository configurations, implemented by the repository types classes.
 * Each repository-type (such as LOCAL/FEDERATED/REMOTE/VIRTUAL) configuration model is structured by the 4
 * sub-configurations defined in this interface, the model for the specific repository type implements the
 * {@link SpecificTypeRepositoryConfig}
 *
 * @author shayb
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = LocalRepoConfig.class, name = "local"),
        @JsonSubTypes.Type(value = RemoteRepoConfig.class, name = "remote"),
        @JsonSubTypes.Type(value = VirtualRepoConfig.class, name = "virtual"),
        @JsonSubTypes.Type(value = FederatedRepoConfig.class, name = "federated"),
        @JsonSubTypes.Type(value = ReleaseBundleRepoConfig.class, name = "releaseBundle")
})
public interface RepoConfig {

    /**
     * @return the repository key.
     */
    String getKey();

    /**
     * Basic repository configurations common for all the repository types, such as model version, description, notes,
     * repo layout reference, and includes/excludes pattern.
     *
     * @return basic repository configurations,such as model version, description, notes, repo layout reference,
     * and includes/excludes pattern.
     */
    BaseConfig getBaseConfig();

    /**
     * Specific repository type (Local/Federated/Remote/Virtual) configurations, such as URL of remote repository,
     * aggregated repos of virtual repository, mirrors of federated repositories etc.
     *
     * The {@link SpecificTypeRepositoryConfig} interface is implemented by the specific repository types of Artifactory
     *
     * @return the specific repository type configurations
     */
    SpecificTypeRepositoryConfig getRepoTypeConfig();

    /**
     * Configuration extension provided and managed by the package-handler, stored in Artifactory as a part of
     * the repository configuration model. The package configurations are provided as a key-value map
     *
     * @return map of package-configurations (i.e. debian specific configurations, helm specific configurations etc..)
     */
    Map<String, String> getPackageTypeConfig();

    /**
     * Security related repository configurations such as key-pairs, signed url configurations etc
     *
     * @return security repository configurations
     */
    SecurityConfig getSecurityConfig();

    /**
     * @return True if not a virtual repository (for example remote cached or local)
     */
    @JsonIgnore
    boolean isReal();

    /**
     * @return True if the repository is a remote cache repository
     */
    @JsonIgnore
    default boolean isCache() {
        return false;
    }

    @JsonIgnore
    default boolean isFederated() {
        return false;
    }

    RepoType getPackageType();

    RepoDetailsType getRepoType();

    @JsonIgnore
    long getModification();

    @JsonIgnore
    void setModification(long date);

    @JsonIgnore
    long getCreation();

    @JsonIgnore
    void setCreation(long date);

    @JsonIgnore
    default BasicRepoConfig toBasic() {
        return new BasicRepoConfig(this);
    }

    @JsonIgnore
    default XrayNotAvailableMode getXrayNotAvailableMode() {
        return USE_SYSTEM_PROPERTY;
    }

    @JsonIgnore
    default String getUrl() {
        return null;
    }
}