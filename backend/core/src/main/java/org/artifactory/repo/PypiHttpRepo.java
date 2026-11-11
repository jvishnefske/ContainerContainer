package org.artifactory.repo;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.ResearchService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.PathOverrideRequestContext;
import org.artifactory.request.RequestContext;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.artifactory.util.HttpUtils;

import java.util.List;

/**
 * HttpRepo implementation for PyPI remote repositories.
 * When the repo URL matches the configured PyPI host list,
 * normalizes cache lookup paths by stripping the
 * {@code packages/} prefix so that a request arriving with it
 * can hit artifacts that were originally cached without it.
 *
 * @author Shahaf Golan
 */
@Slf4j
public class PypiHttpRepo extends HttpRepo {

    static final String PYPI_PACKAGES_PATH_PREFIX = "packages/";

    private final boolean isSupportStrippingPackages;

    PypiHttpRepo(HttpRepoDescriptor descriptor, RemoteRepoConfig repoConfig,
            InternalRepositoryService repositoryService, AddonsManager addonsManager,
            ResearchService researchService, RemoteRepo oldRemoteRepo,
            OnlineMonitorService onlineMonitorService, CentralConfigService configService,
            IRequestRateLimiterService rateLimiterService) {
        super(descriptor, repoConfig, repositoryService, addonsManager, researchService, oldRemoteRepo,
                onlineMonitorService, configService, rateLimiterService);
        this.isSupportStrippingPackages = shouldSupportStrippingPackages(descriptor);
    }

    private static boolean shouldSupportStrippingPackages(HttpRepoDescriptor descriptor) {
        if (!ConstantValues.pypiUrlsToDetectPackagesPrefixEnabled.getBoolean()) {
            return false;
        }
        String url = descriptor.getUrl();
        if (url == null) {
            return false;
        }
        List<String> pypiUrls = ConstantValues.pypiUrlsToDetectPackagesPrefix.getList();
        return pypiUrls.stream().anyMatch(url::startsWith);
    }

    @VisibleForTesting
    boolean isSupportStrippingPackages() {
        return isSupportStrippingPackages;
    }

    @VisibleForTesting
    String normalizePypiCachePath(String path) {
        if (isSupportStrippingPackages && path.startsWith(PYPI_PACKAGES_PATH_PREFIX)) {
            return path.substring(PYPI_PACKAGES_PATH_PREFIX.length());
        }
        return path;
    }

    /**
     * Normalizes the repo path before delegating to
     * {@code super.internalGetInfo}. This ensures the remote
     * resource returned by {@code retrieveInfo} carries the
     * normalized path, which in turn determines the cache
     * storage location in {@code doDownloadAndSave}.
     */
    @Override
    RepoResource internalGetInfo(RepoPath repoPath, InternalRequestContext context) {
        String path = repoPath.getPath();
        String normalizedPath = normalizePypiCachePath(path);
        if (!normalizedPath.equals(path)) {
            log.debug("PyPI cache storage path normalization: '{}' -> '{}'", path, normalizedPath);
            RepoPath normalizedRepoPath = InternalRepoPathFactory.create(getKey(), normalizedPath);
            return super.internalGetInfo(normalizedRepoPath, context);
        }
        return super.internalGetInfo(repoPath, context);
    }

    /**
     * Restores the {@code packages/} prefix when constructing
     * remote URLs. The prefix is stripped from internal cache
     * paths by {@link #internalGetInfo} but must be present in
     * the URL sent to {@code files.pythonhosted.org}.
     */
    @Override
    protected String appendAndGetUrl(String pathToAppend, RequestContext context) {
        if (isSupportStrippingPackages && !HttpUtils.isAbsolute(pathToAppend) &&
                !pathToAppend.startsWith(PYPI_PACKAGES_PATH_PREFIX)) {
            pathToAppend = PYPI_PACKAGES_PATH_PREFIX + pathToAppend;
        }
        return super.appendAndGetUrl(pathToAppend, context);
    }

    /**
     * Normalizes the context path used when un-expiring a
     * cached resource so it matches the actual cache location.
     */
    @Override
    String getResourceOfflineRepoPath(String path, String repoPath) {
        return super.getResourceOfflineRepoPath(normalizePypiCachePath(path), repoPath);
    }

    /**
     * Tries the original cache path first; on a miss, retries
     * with the {@code packages/} prefix stripped when
     * stripping is supported.
     */
    @Override
    protected RepoResource getInfoFromCache(InternalRequestContext context) {
        RepoResource cached = getLocalCacheRepo().getInfo(context);
        if (cached.isFound()) {
            return cached;
        }
        String path = context.getResourcePath();
        String normalizedPath = normalizePypiCachePath(path);
        if (!normalizedPath.equals(path)) {
            log.debug("PyPI cache path normalization: " + "'{}' -> '{}'", path, normalizedPath);
            InternalRequestContext normalizedCtx = new PathOverrideRequestContext(
                    context, InternalRepoPathFactory.create(getKey(), normalizedPath));
            return getLocalCacheRepo().getInfo(normalizedCtx);
        }
        return cached;
    }

    @Override
    protected RepoResource getCachedResource(String relPath) {
        RepoResource cached = super.getCachedResource(relPath);
        if (cached.isFound()) {
            return cached;
        }
        String normalizedPath = normalizePypiCachePath(relPath);
        if (!normalizedPath.equals(relPath)) {
            return super.getCachedResource(normalizedPath);
        }
        return cached;
    }

}
