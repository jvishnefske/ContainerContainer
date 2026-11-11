package org.artifactory.model;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.remote.RemoteCacheRepoTypeConfig;
import org.artifactory.util.LocalCacheDefaultPackageConfigSetter;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A non-serialized in-memory config of local cache repositories.
 *
 * @author shayb
 */
@Valid
@Getter
@Setter
public class LocalCacheRepoConfig implements LocalConfig {
    public static final String PATH_SUFFIX = "-cache";
    @NotUnicode
    private final String key;
    @Valid
    private final BaseConfig baseConfig;
    @Valid
    private final RemoteCacheRepoTypeConfig repoTypeConfig;
    private final RepoType packageType;
    private final Map<String, String> packageTypeConfig;
    @Valid
    private SecurityConfig securityConfig;
    private final RemoteRepoConfig remoteRepoConfig;

    private LocalCacheRepoConfig(RemoteRepoConfig remoteRepoConfig) {
        this.remoteRepoConfig = Objects.requireNonNull(remoteRepoConfig, "Remote repository config cannot be null");
        this.key = remoteRepoConfig.getKey() + PATH_SUFFIX;
        this.repoTypeConfig = new RemoteCacheRepoTypeConfig(remoteRepoConfig.getRepoTypeConfig());
        this.baseConfig = new BaseConfig(remoteRepoConfig.getBaseConfig());
        String remoteDescription = remoteRepoConfig.getBaseConfig().getDescription();
        if (remoteDescription != null && !remoteDescription.contains("(local file cache)")) {
            this.baseConfig.setDescription(remoteDescription + " (local file cache)");
        }
        this.securityConfig = new SecurityConfig(remoteRepoConfig.getSecurityConfig());
        this.packageTypeConfig = new HashMap<>(remoteRepoConfig.getPackageTypeConfig());
        this.packageType = remoteRepoConfig.getPackageType();
        LocalCacheDefaultPackageConfigSetter.setDefaultValues(packageType, packageTypeConfig);
    }

    /**
     * Factory method enabling exclusive access to instance creation
     *
     * @param remoteRepoConfig the remote repo config for which this cache repo config belongs to
     * @return Local cache repo configuration
     * @throws NullPointerException if {@code remoteRepoConfig} is null
     */
    public static LocalCacheRepoConfig create(RemoteRepoConfig remoteRepoConfig) {
        return new LocalCacheRepoConfig(remoteRepoConfig);
    }

    @Override
    public boolean isCache() {
        return true;
    }

    @Override
    public RepoDetailsType getRepoType() {
        return RepoDetailsType.LOCAL;
    }

    @Override
    public long getModification() {
        return remoteRepoConfig.getModification();
    }

    @Override
    public void setModification(long date) {
        // noop
    }

    @Override
    public long getCreation() {
        return remoteRepoConfig.getCreation();
    }

    @Override
    public void setCreation(long date) {
        // noop
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LocalCacheRepoConfig that = (LocalCacheRepoConfig) o;
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
