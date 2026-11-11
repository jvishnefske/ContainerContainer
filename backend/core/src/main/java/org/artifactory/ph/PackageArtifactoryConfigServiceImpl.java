package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageRepoDescriptorException;
import com.jfrog.ph.acl.exception.PackageRepoNotFoundException;
import com.jfrog.ph.acl.model.KeyPair;
import com.jfrog.ph.acl.service.PackageArtifactoryConfigService;
import com.jfrog.ph.acl.vcs.configuration.PhVcsGitConfiguration;
import com.jfrog.ph.acl.vcs.configuration.PhVcsType;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;

import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyRepoConfig;
import org.artifactory.descriptor.repo.VcsConfiguration;
import org.artifactory.descriptor.repo.VcsGitConfiguration;
import org.artifactory.descriptor.repo.vcs.VcsType;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.ph.mapper.PhKeyPairConfigurationMapper;
import org.artifactory.ph.mapper.PhVcsConfigurationMapper;
import org.artifactory.repo.Repo;
import org.artifactory.repo.db.DbCacheRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.util.AolUtils;
import org.artifactory.util.HttpUtils;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.JsonUtils;

import java.util.Map;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.artifactory.ph.PackageRepoServiceImpl.DOES_NOT_EXIST;

/**
 * @author Ori Yafe
 */
@Slf4j
public class PackageArtifactoryConfigServiceImpl implements PackageArtifactoryConfigService {

    public static final String REPOSITORY = "Repository '";
    public static final String REMOTE_REPOSITORY = "Remote repository '";
    public static final String EMPTY_JSON = "{}";
    private static final String ENABLE_NORMALIZED_VERSION_KEY = "enableNormalizedVersion";
    private final InternalRepositoryService repositoryService;

    public PackageArtifactoryConfigServiceImpl(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public String getStringFromRepoConfig(String repoKey, String key, String def)
            throws PackageRepoDescriptorException {
        return getValue(repoKey, key, def, String.class);
    }

    @Override
    public int getIntFromRepoConfig(String repoKey, String key, int def) throws PackageRepoDescriptorException {
        return getValue(repoKey, key, def, Integer.class);
    }

    @Override
    public boolean getBooleanFromRepoConfig(String repoKey, String key, boolean def)
            throws PackageRepoDescriptorException {
        return getValue(repoKey, key, def, Boolean.class);
    }

    @Override
    public long getLongFromRepoConfig(String repoKey, String key, long def) throws PackageRepoDescriptorException {
        return getValue(repoKey, key, def, Long.class);
    }

    @Override
    public String getArtifactoryBaseUrl(HttpServletRequest httpRequest) {
        return PathUtils.trimTrailingSlashes(HttpUtils.getArtifactoryBaseUrl(httpRequest));
    }

    @Override
    public String getGlobalPackageConfiguration(PackageType packageType) {
        CentralConfigService centralConfig = ContextHelper.get().getCentralConfig();

        if (centralConfig.getDescriptor() == null ||
                centralConfig.getDescriptor().getRepositoriesGeneralConfig() == null || packageType == null) {
            log.debug("Get global configuration returns to ph empty json");
            return EMPTY_JSON;
        }

        Object config =
                getConfigForPackageType(centralConfig.getDescriptor().getRepositoriesGeneralConfig(), packageType);

        if (config == null) {
            log.debug("Get global configuration returns to ph empty json");
            return EMPTY_JSON;
        }

        return JsonUtils.getInstance().valueToString(config);
    }

    private Object getConfigForPackageType(RepositoriesGeneralConfigDescriptor generalConfigDescriptor,
            PackageType packageType) {
        return switch (packageType) {
            case HUGGINGFACEML -> generalConfigDescriptor.getHuggingfaceml();
            case PYPI -> generalConfigDescriptor.getPypi();
            default -> null;
        };
    }

    @Override
    public String getPlatformContextUrl(HttpServletRequest httpRequest) {
        return HttpUtils.getPlatformServletContextUrl(httpRequest);
    }

    @Override
    public PhVcsGitConfiguration getVcsConfigOfRemoteRepo(String remoteRepoKey) throws PackageRepoDescriptorException {
        VcsConfiguration vcs = getVcsConfiguration(remoteRepoKey);
        VcsGitConfiguration vcsGitConfiguration = vcs.getGit();
        if (vcsGitConfiguration == null) {
            String msg = REMOTE_REPOSITORY + remoteRepoKey + "' does not have VCS Git configuration";
            log.warn(msg);
            throw new PackageRepoDescriptorException(SC_BAD_REQUEST, msg, msg);
        }
        return PhVcsConfigurationMapper.from(vcsGitConfiguration);
    }

    @Override
    public PhVcsType getVcsTypeOfRemoteRepo(String remoteRepoKey) throws PackageRepoDescriptorException {
        VcsConfiguration vcs = getVcsConfiguration(remoteRepoKey);
        VcsType vcsType = vcs.getType();
        if (vcsType == null) {
            String msg = REMOTE_REPOSITORY + remoteRepoKey + "' does not have VCS Type configuration";
            log.warn(msg);
            throw new PackageRepoDescriptorException(SC_BAD_REQUEST, msg, msg);
        }
        return PhVcsType.fromString(vcsType.getVal());
    }

    @Nonnull
    private VcsConfiguration getVcsConfiguration(String remoteRepoKey) throws PackageRepoDescriptorException {
        RepoDescriptor repoDescriptor = repositoryService.repoDescriptorByKey(remoteRepoKey);
        if (repoDescriptor == null) {
            String msg = REMOTE_REPOSITORY + remoteRepoKey + "' was not found";
            throw new PackageRepoDescriptorException(SC_NOT_FOUND, msg, msg);
        }
        if (repoDescriptor instanceof LocalCacheRepoDescriptor cacheRepoDescriptor) {
            repoDescriptor = cacheRepoDescriptor.getRemoteRepo();
        }
        if (!(repoDescriptor instanceof RemoteRepoDescriptor remoteRepoDescriptor)) {
            String msg = REMOTE_REPOSITORY + remoteRepoKey + "' is not a remote repository";
            log.warn(msg);
            throw new PackageRepoDescriptorException(SC_BAD_REQUEST, msg, msg);
        }
        VcsConfiguration vcs = remoteRepoDescriptor.getVcs();
        if (vcs == null) {
            String msg = REMOTE_REPOSITORY + remoteRepoKey + "' does not have VCS configuration";
            log.warn(msg);
            throw new PackageRepoDescriptorException(SC_BAD_REQUEST, msg, msg);
        }
        return vcs;
    }

    public String getDockerAccessMethodType() {
        CentralConfigService centralConfig = ContextHelper.get().getCentralConfig();
        ReverseProxyDescriptor reverseProxy = centralConfig.getDescriptor().getCurrentReverseProxy();
        if (reverseProxy != null) {
            return reverseProxy.getDockerReverseProxyMethod().getVal();
        }

        return null;
    }

    public int getRepoPort(String repoKey) {
        CentralConfigService centralConfig = ContextHelper.get().getCentralConfig();
        ReverseProxyDescriptor reverseProxy = centralConfig.getDescriptor().getCurrentReverseProxy();
        if (reverseProxy != null) {
            ReverseProxyRepoConfig perRepo = reverseProxy.getReverseProxyRepoConfig(repoKey);
            if (perRepo != null) {
                return perRepo.getPort();
            }
        }
        return -1; // Default value if no port is set for the repository
    }

    public boolean isSaaS() {
        return AolUtils.isAol();
    }

    public boolean isSkipDockerDns() {
        return AolUtils.skipDockerDns();
    }

    @Override
    public boolean shouldVerifyBadClientChecksum(String repoKey) throws PackageRepoDescriptorException {
        Repo<?, ?> repo = repositoryService.repositoryByKey(repoKey);
        if (repo == null) {
            String msg = REPOSITORY + repoKey + "' was not found";
            throw new PackageRepoDescriptorException(SC_NOT_FOUND, msg, msg);
        }
        return repo.getChecksumPolicy().shouldVerifyBadClientChecksum();
    }

    @Override
    public KeyPair getRepoKeyPair(String repoKey) throws PackageRepoDescriptorException {
        RepoDescriptor repoDescriptor = repositoryService.repoDescriptorByKey(repoKey);
        if (repoDescriptor == null) {
            String msg = REPOSITORY + repoKey + "' was not found";
            throw new PackageRepoDescriptorException(SC_NOT_FOUND, msg, msg);
        }
        KeyPairDescriptor keyPairDescriptor = repoDescriptor.getPrimaryKeyPairDescriptor();
        return keyPairDescriptor == null
                ? null
                : PhKeyPairConfigurationMapper.from(keyPairDescriptor);
    }

    @Override
    public Map<String, String> getPackageTypeConfig(String repoKey) throws PackageRepoNotFoundException {
        RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoKey);
        if (repoConfig == null) {
            throw new PackageRepoNotFoundException(REPOSITORY + repoKey + "' was not found");
        }
        if (repoConfig instanceof LocalCacheRepoConfig cacheRepoConfig) {
            repoConfig = cacheRepoConfig.getRemoteRepoConfig();
        }
        return repoConfig.getPackageTypeConfig();
    }

    @Override
    public void setIsRepoBlackedOut(String repoKey, boolean isRepoBlackedOut) {
        RealRepoConfig old = repositoryService.getRealRepoConfigByKey(repoKey);
        if (old == null) {
            log.warn("Failed to black out repository '{}'", repoKey);
            return;
        }
        old.getRepoTypeConfig().setBlackedOut(isRepoBlackedOut);
        repositoryService.updateRepoConfig(old);
    }

    @Override
    public void setPackageTypeConfigValue(String repoKey, String key, String value) {
        RealRepoConfig repoConfig = repositoryService.getRealRepoConfigByKey(repoKey);
        if (repoConfig == null) {
            log.warn("Failed to set package type config for repository '{}': not found or not a real repository",
                    repoKey);
            return;
        }
        repoConfig.getPackageTypeConfig().put(key, value);
        repositoryService.updateRepoConfig(repoConfig);
    }

    @Override
    public void markRepositoryAsNormalized(String repoKey) {
        RealRepoConfig repoConfig = repositoryService.getRealRepoConfigByKey(repoKey);
        if (repoConfig == null) {
            log.warn("Failed to mark repository '{}' as normalized: not found or not a real repository", repoKey);
            return;
        }
        // Two writes in one updateRepoConfig() so the interceptor sees them atomically:
        //   1. enableNormalizedVersion=true  - the real config we want to persist.
        //   2. SKIP_NORMALIZATION_OVERRIDE_MARKER=true - transient bypass marker that tells
        //      PackageRepoConfigChangeInterceptor to NOT override (1). The interceptor strips the
        //      marker from the map before the change is persisted, so it never reaches the DB.
        Map<String, String> packageTypeConfig = repoConfig.getPackageTypeConfig();
        packageTypeConfig.put(ENABLE_NORMALIZED_VERSION_KEY, "true");
        packageTypeConfig.put(PackageRepoConfigChangeInterceptor.SKIP_NORMALIZATION_OVERRIDE_MARKER, "true");
        repositoryService.updateRepoConfig(repoConfig);
    }

    @Override
    public void createLocalRepository(String repoKey, String packageType, String description) {
        RepoType repoType =
                RepoType.fromType(packageType);
        if (repoType == RepoType.Generic && !"generic".equalsIgnoreCase(packageType)) {
            throw new IllegalArgumentException("Unknown package type: " + packageType);
        }
        LocalRepoConfig localRepoConfig =
                LocalRepoConfig.create(repoKey, repoType);
        if (description != null) {
            localRepoConfig.getBaseConfig().setDescription(description);
        }
        repositoryService.upsertRepoConfig(localRepoConfig);
    }

    @Override
    public boolean removeRepository(String repoKey) {
        try {
            BasicStatusHolder status = repositoryService.removeRepository(repoKey);
            if (status.isError()) {
                log.warn("Failed to remove repository '{}': {}", repoKey, status.getLastError().getMessage());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to remove repository '{}': {}", repoKey, e.getMessage());
            return false;
        }
    }

    private <T> T getValue(String repoKey, String key, T def, Class<T> clazz) throws PackageRepoDescriptorException {
        T result;
        Repo<?, ?> repository = repositoryService.repositoryByKey(repoKey);
        if (repository == null) {
            throw new PackageRepoDescriptorException(SC_NOT_FOUND, DOES_NOT_EXIST,
                    "Repo with name:" + repoKey + DOES_NOT_EXIST);
        }
        if (repository instanceof DbCacheRepo dbCacheRepo) {
            repository = dbCacheRepo.getRemoteRepo();
        }
        RepoDescriptor repoDescriptor = repository.getDescriptor();
        try {
            Object fieldResults = MethodUtils.invokeMethod(repoDescriptor, key);
            result = clazz.cast(fieldResults);
        } catch (NoSuchMethodException e) {
            log.debug("The key: {} is not exists in repository: {}. Returning default value: {}", key, repoKey, def);
            return def;
        } catch (Exception e) {
            log.debug("Failed to get key: {} from repository: {}", key, repoKey, e);
            throw new PackageRepoDescriptorException(SC_INTERNAL_SERVER_ERROR, "Internal error", e.getMessage(), e);
        }
        if (result == null) {
            log.debug("Got null value for key: {} from repository: {}. Returning default value: {}", key, repoKey, def);
            return def;
        }
        return result;
    }
}
