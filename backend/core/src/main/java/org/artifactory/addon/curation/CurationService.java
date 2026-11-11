package org.artifactory.addon.curation;

import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.Header;
import org.artifactory.addon.curation.wq.CurationCachedPackageEventWorkItem;
import org.artifactory.async.Async;
import org.artifactory.async.WorkQueueConfig;
import org.artifactory.common.repo.PackageType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.HeadersMultiMap;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.servicetrust.curation.CurationSettings;

import java.util.List;
import java.util.Set;

import static org.artifactory.addon.curation.wq.CurationCachedPackageEventWorkQueueInfoConfig.CURATION_EVENTS_WQ_NAME;

/**
 * @author Dvir Twina
 * @author Tomer Slutsky
 * @author Andrey Tashlitsky
 */
public interface CurationService {

    @Nullable
    CurationSettings getCurationSettings();

    void updateCurationSettings(CurationSettings curationSettings);

    boolean isEntitled();

    boolean isEnabled();

    boolean isBlockDownloadEnabled();

    boolean isIndexVersionsCompliantEnabled();

    boolean isAllowDownloadWhenUnavailable();

    Set<PackageType> getForceCVSPackages();

    boolean isCurationSupportedForPackageType(String packageType);

    /**
     * @param config a RemoteRepoConfig
     * @return is curation enabled in the given RemoteRepoConfig
     */
    boolean isCurationEnabledForRepo(RemoteRepoConfig config);

    @Async
    void notifyCurationService(CuratedRepoEvent event);

    List<CurationSupportedPackageType> getSupportedPackageTypes();

    List<CurationSupportedRepository> getSupportedRepositories();

    CurationStatus getCurationStatus();

    CurationPackageStatus getCachedPackageStatus(RemoteRepoConfig config, RepoPath path, String downloadUrl,
            String mimeType);

    CurationCachedPackageStatus getPackageStatus(RemoteRepoConfig config, RepoPath path, String downloadUrl,
            Header[] responseHeaders, boolean fromCache);

    void invalidatePackageStatusCache();

    @Async(workQueue = @WorkQueueConfig(name = CURATION_EVENTS_WQ_NAME))
    void sendCachedPackageEvent(CurationCachedPackageEventWorkItem workItem);

    void addCurationContext(RepoPath repoPath, Request request, HeadersMultiMap headers);

    void audit(HttpServletRequest request, HttpServletResponse response, String path);

    boolean shouldPerformPassThrough(RemoteRepoConfig remoteRepoConfig, RequestContext requestContext);

    void invalidateSettingsCache();

    /**
     * @param curationRequest data object of repo/package index data relevant for curation: package type, repo name,
     *                        package name, version, download link and release date
     * @return data object with the blocked versions
     */
    @Nullable
    CurationResponse filterBlockedVersions(@Nonnull CurationRequest curationRequest);
}
