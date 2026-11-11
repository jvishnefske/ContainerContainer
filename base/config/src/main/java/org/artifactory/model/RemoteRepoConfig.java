package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.DownloadRedirectRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.util.ParamValidationUtils;
import org.artifactory.util.RemoteDefaultPackageConfigSetter;
import org.jfrog.common.config.diff.DiffIgnore;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Remote repository configurations
 *
 * @author shayb
 */
@Slf4j
@Valid
@Getter
@GenerateDiffFunction
public class RemoteRepoConfig implements RealRepoConfig, ProxyRepoConfig {
    @NotUnicode
    private final String key;
    @Valid
    @Setter(AccessLevel.PRIVATE)
    private BaseConfig baseConfig;
    @Valid
    @Setter(AccessLevel.PRIVATE)
    private RemoteRepositoryRepoTypeConfig repoTypeConfig = new RemoteRepositoryRepoTypeConfig();
    private RepoType packageType;
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
    private RemoteRepoConfig(@JsonProperty("key") String repoKey, @JsonProperty("packageType") RepoType packageType) {
        this.key = ParamValidationUtils.requireNonBlank(repoKey, "Repository key cannot be blank");
        this.packageType = Objects.requireNonNull(packageType, "Repository package type cannot be null");
        this.baseConfig = new BaseConfig();
        RemoteDefaultPackageConfigSetter.setDefaultValues(packageType, this.packageTypeConfig);
        this.repoTypeConfig.setDownloadRedirectConfig(new DownloadRedirectRepoConfig());
    }

    /**
     * Factory method enabling exclusive access to instance creation
     *
     * @param repoKey     the repository key
     * @param packageType the repository package type
     * @return Remote repo configuration
     * @throws IllegalArgumentException if {@code repoKey} is blank
     * @throws NullPointerException     if {@code packageType} is null
     */
    public static RemoteRepoConfig create(String repoKey, RepoType packageType) {
        return new RemoteRepoConfig(repoKey, packageType);
    }

    /**
     * Factory method enabling exclusive access to deep copy a remote repo config
     *
     * @param remoteRepoConfig the remote repo config to be copied
     * @return a copy of the remote repo config
     * @throws NullPointerException if {@code remoteRepoConfig} is null
     */
    static RemoteRepoConfig copy(RemoteRepoConfig remoteRepoConfig) {
        Objects.requireNonNull(remoteRepoConfig, "Remote repo config cannot be null");
        return copyWithKey(remoteRepoConfig, remoteRepoConfig.getKey());
    }

    /**
     * Factory method to create a copy of a remote repo config with a different key.
     * Used for federation where the repo key on the remote server may be different.
     *
     * @param remoteRepoConfig the remote repo config to be copied
     * @param newKey           the new repository key
     * @return a copy of the remote repo config with the new key
     * @throws NullPointerException if {@code remoteRepoConfig} is null
     */
    public static RemoteRepoConfig copyWithKey(RemoteRepoConfig remoteRepoConfig, String newKey) {
        Objects.requireNonNull(remoteRepoConfig, "Remote repo config cannot be null");
        RemoteRepoConfig repoCopy = new RemoteRepoConfig(newKey, remoteRepoConfig.getPackageType());
        repoCopy.setBaseConfig(new BaseConfig(remoteRepoConfig.getBaseConfig()));
        repoCopy.setRepoTypeConfig(new RemoteRepositoryRepoTypeConfig(remoteRepoConfig.getRepoTypeConfig()));
        repoCopy.setPackageTypeConfig(new HashMap<>(remoteRepoConfig.getPackageTypeConfig()));
        repoCopy.setSecurityConfig(new SecurityConfig(remoteRepoConfig.getSecurityConfig()));
        repoCopy.setModification(remoteRepoConfig.getModification());
        repoCopy.setCreation(remoteRepoConfig.getCreation());
        return repoCopy;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    /**
     * Extract the credentials if they exist in the URL and set them in the config
     */
    public void processUrlCredentials() {
        final String[] credentials = extractCredentialsFromUrl(getRepoTypeConfig().getUrl());
        if (credentials != null) {
            getRepoTypeConfig().setUsername(credentials[0]);
            getRepoTypeConfig().setPassword(credentials[1]);
            getRepoTypeConfig().setUrl(extractUrlWithoutCredentials(getRepoTypeConfig().getUrl()));
        }
    }

    String[] extractCredentialsFromUrl(String url) {
        return Optional.ofNullable(url)
                .map(l -> {
                    try {
                        return new URI(l);
                    } catch (URISyntaxException e) {
                        log.warn("Can not parse URL: {}", url);
                        return null;
                    }
                })
                .map(URI::getUserInfo)
                .map(l -> l.split(":"))
                .filter(l -> l.length > 1)
                .orElse(null);
    }

    String extractUrlWithoutCredentials(String url) {
        try {
            URI uri = new URI(url);
            URI newUri = new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    null,
                    null
            );
            return newUri.toString();
        } catch (URISyntaxException e) {
            log.warn("Can not parse URL: {}. \n{}", url, e.getMessage());
            return null;
        }
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
        return RepoDetailsType.REMOTE;
    }

    /**
     * Checks if this remote repository is federated.
     * Delegates to BaseConfig.isFederated() which checks for federation members.
     *
     * @return true if this remote repo has federation configured
     */
    @Override
    public boolean isFederated() {
        return baseConfig.isFederated();
    }

    public boolean isSmart() {
        return getRepoTypeConfig().getContentSynchronisation().isEnabled();
    }

    public boolean isStatsSyncEnabled() {
        return getRepoTypeConfig().getContentSynchronisation().getStatistics().isEnabled();
    }

    public boolean isPropertiesSyncEnabled() {
        return getRepoTypeConfig().getContentSynchronisation().getProperties().isEnabled();
    }

    public boolean isOriginAbsenceDetectionEnabled() {
        return getRepoTypeConfig().getContentSynchronisation().getSource().isOriginAbsenceDetection();
    }

    @JsonIgnore
    @Override
    public String getUrl() {
        return getRepoTypeConfig().getUrl();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RemoteRepoConfig that = (RemoteRepoConfig) o;
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
