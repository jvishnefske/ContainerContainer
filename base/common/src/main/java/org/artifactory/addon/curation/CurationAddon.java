package org.artifactory.addon.curation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import org.apache.http.Header;
import org.artifactory.common.addon.Addon;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.RequestContext;

import java.util.List;

/**
 * @author Dvir Twina
 * @author Tomer Slutsky
 * @author Andrey Tashlitsky
 */
public interface CurationAddon extends Addon {

    /**
     * Toggles the curation enabled/disabled flag in the curation settings
     *
     * @param body - Enable/disable the curation
     * @return If the operation was successful
     */
    Response toggleCuration(ToggleCuration body);

    boolean isUserAllowed(String currentUsername);

    boolean isCurationEntitled();

    boolean isCurationEnabled();

    boolean isCurationConfigValid(RemoteRepoConfig config);

    List<CurationSupportedPackageType> getSupportedPackageTypes();

    List<CurationSupportedRepository> getSupportedRepositories();

    CurationStatus getCurationStatus();

    CurationPackageStatus getCachedPackageStatus(RemoteRepoConfig config, RepoPath path, String downloadUrl,
            String mimeType);

    CurationCachedPackageStatus getPackageStatus(RemoteRepoConfig config, RepoPath path, String downloadUrl,
            Header[] responseHeaders, boolean fromCache);

    void invalidatePackageStatusCache();

    void sendCachedPackageEvent(RemoteRepoConfig config, RepoPath path);

    void audit(HttpServletRequest request, HttpServletResponse response, String path);

    boolean shouldPerformPassThrough(RemoteRepoConfig config, RequestContext context);

    void invalidateSettingsCache();

    boolean isBlockDownloadEnabled(RemoteRepoConfig config);

    void updateInterfaceSettings(CurationInterfaceSettings settings);

    CurationInterfaceSettings getInterfaceSettings();

    boolean isAllowDownloadWhenUnavailable();
}
