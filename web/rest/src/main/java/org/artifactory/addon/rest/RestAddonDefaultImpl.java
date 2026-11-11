/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.addon.rest;

import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.api.BuildArtifactsRequest;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.license.LicenseStatus;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.api.download.FolderDownloadResult;
import org.artifactory.api.rest.artifact.ItemPermissions;
import org.artifactory.api.rest.replication.ReplicationRequest;
import org.artifactory.api.rest.search.result.ArtifactVersionsResult;
import org.artifactory.api.rest.search.result.LicensesSearchResult;
import org.artifactory.download.FolderDownloadException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.rest.exception.MissingRestAddonException;
import org.artifactory.rest.resource.artifact.legacy.DownloadResource;
import org.artifactory.security.permissions.PermissionTargetModel;
import org.jfrog.common.StringList;
import org.jfrog.common.archive.ArchiveType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of the rest add-on.
 *
 * <p>Almost every method throws {@link MissingRestAddonException}. The user-plugin endpoints are the
 * exception: plugin execution, reload, plugin info and plugin status are forwarded to the
 * {@link PluginsAddon} (the fork's Groovy plugin engine) when a non-default one is installed. With the stock
 * no-op {@code PluginsAddonDefaultImpl}, or when this class is built without an {@link AddonsManager}, they
 * keep throwing {@link MissingRestAddonException}. Deploying, downloading and deleting plugin files still
 * throw.
 *
 * @author Yoav Landman
 */
@Component
public class RestAddonDefaultImpl implements RestAddon {

    private final AddonsManager addonsManager;

    /** Creates an instance with no plugin engine; every method throws {@link MissingRestAddonException}. */
    public RestAddonDefaultImpl() {
        this(null);
    }

    /**
     * @param addonsManager used to look up the {@link PluginsAddon} serving the user-plugin endpoints;
     *                      {@code null} disables them; injected lazily to avoid construction cycles
     */
    @Autowired
    public RestAddonDefaultImpl(@Lazy @Nullable AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    /** @return the installed plugin engine, or throws {@link MissingRestAddonException} when there is none */
    private PluginsAddon pluginEngine() {
        if (addonsManager == null) {
            throw new MissingRestAddonException();
        }
        PluginsAddon plugins = addonsManager.addonByType(PluginsAddon.class);
        if (plugins == null || plugins.isDefault()) {
            throw new MissingRestAddonException();
        }
        return plugins;
    }

    @Override
    public boolean isDefault() {
        return true;
    }

    @Override
    public Response copy(String path, String target, int dryRun, int suppressLayouts, int failFast, String atomic)
            throws Exception {
        throw new MissingRestAddonException();
    }

    @Override
    public Response move(String path, String target, int dryRun, int suppressLayouts, int failFast, String atomic)
            throws Exception {
        throw new MissingRestAddonException();
    }

    @Override
    public Response copyFlat(String path, String target, int dryRun, int failFast) throws Exception {
        throw new MissingRestAddonException();
    }

    @Override
    public Response moveFlat(String path, String target, int dryRun, int failFast) throws Exception {
        throw new MissingRestAddonException();
    }

    @Override
    public Response download(String path, DownloadResource.Content content, int mark,
            HttpServletResponse response) throws Exception {
        throw new MissingRestAddonException();
    }

    @Override
    public Set<String> searchArtifactsByPattern(String pattern) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response replicate(RepoPath repoPath, List<ReplicationRequest> replicationRequests, String forcedStrategy) {
        throw new MissingRestAddonException();
    }

    @Override
    public ItemInfo getLastModified(String pathToSearch) {
        throw new MissingRestAddonException();
    }

    @Override
    public LicensesSearchResult findLicensesInRepos(LicenseStatus status, Set<String> repos, String servletContextUrl) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response deleteRepository(String repoKey) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response deleteRepositories(LinkedHashSet<String> repoKeys) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response getRepositoryConfiguration(String repoKey, MediaType mediaType) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response getRepositoryConfigurations(List<String> repoKeys, MediaType mediaType) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response getAllRepositoryConfigurations(StringList packageTypes, StringList repoTypes) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response hasRepositoriesByProjectAndTypes(String projectKey, Set<String> repoTypes) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response getRepositoryConfigurationV2(String repoKey, MediaType mediaType) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response createRepository(String repoKey, Map<String, Object> repositoryConfig, MediaType mediaType) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response createRepositories(List<Map<String, Object>> repoConfigs, MediaType mediaType) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response updateRepository(String repoKey, Map<String, Object> repositoryConfig, MediaType mediaType) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response updateRepositories(List<Map<String, Object>> repoConfigs, MediaType mediaType) {
        throw new MissingRestAddonException();
    }

    @Override
    public Set<RepoPath> searchArtifactsByChecksum(String md5Checksum, String sha1Checksum, String sha256Checksum,
            StringList reposToSearch) {
        throw new MissingRestAddonException();
    }

    @Override
    @Nonnull
    public Response searchBadChecksumArtifacts(String type, StringList reposToSearch, HttpServletRequest request) {
        throw new MissingRestAddonException();
    }

    @Override
    public ResponseCtx runPluginExecution(String executionName, String method, Map params, ResourceStreamHandle body,
            boolean async) {
        return pluginEngine().execute(executionName, method, params, body, async);
    }

    @Override
    public Response getStagingStrategy(String strategyName, String buildName, String buildRepo, Map params) {
        throw new MissingRestAddonException();
    }

    @Override
    public ItemPermissions getItemPermissions(HttpServletRequest request, String path) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response searchDependencyBuilds(HttpServletRequest request, @Nullable String sha1, @Nullable String sha2,
            String buildRepo)
            throws UnsupportedEncodingException {
        throw new MissingRestAddonException();
    }

    @Override
    public Response calculateYumMetadata(String repoKey, String path, boolean async, String passphrase) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response getAllPermissionTargetsV2(HttpServletRequest request) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response getUserPermissionsSecurityV2(String entityKey) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response getGroupPermissionsSecurityV2(String entityKey) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response getPermissionTargetV2(String entityKey) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response deletePermissionTargetV2(String entityKey) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response isPermissionTargetExistsV2(String entityKey) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response createPermissionTargetV2(String entityType, PermissionTargetModel permissionTargetModel) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response updatePermissionTargetV2(String entityType, PermissionTargetModel permissionTargetModel) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response getReplicationStatus(RepoPath repoPath) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response runMavenIndexer(List<String> reposToIndex, int force) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response getUserPluginInfo(@Nullable String pluginType) {
        PluginsAddon plugins = pluginEngine();
        Map<String, Object> info = new LinkedHashMap<>(plugins.getPluginInfo(pluginType));
        if (pluginType == null) {
            info.put("plugins", plugins.getPluginsInfoSupportBundleDumpAsMap());
        }
        return Response.ok(info, MediaType.APPLICATION_JSON_TYPE).build();
    }

    @Override
    public Map<BuildFileInfo, String> getBuildArtifacts(BuildArtifactsRequest buildArtifactsRequest) {
        throw new MissingRestAddonException();
    }

    @Override
    public File getBuildArtifactsArchive(BuildArtifactsRequest buildArtifactsRequest) {
        throw new MissingRestAddonException();
    }

    @Override
    public FolderDownloadResult downloadFolderOrRepo(RepoPath pathToDownload, ArchiveType archiveType,
            boolean includeChecksumFiles) throws IOException, FolderDownloadException {
        throw new MissingRestAddonException();
    }

    @Override
    public ResponseCtx promoteBuildPlugin(String promotionName, String buildName, String buildNumber,
            String buildRepo, Map params) {
        throw new MissingRestAddonException();
    }

    @Override
    public ResponseCtx deployPlugin(Reader pluginContent, String scriptName) {
        throw new MissingRestAddonException();
    }

    @Override
    public ResponseCtx reloadPlugins(boolean internal) {
        return pluginEngine().reloadPlugins(true, internal);
    }

    @Override
    public ArtifactVersionsResult getArtifactVersions(String groupId, String artifactId, @Nullable String version,
            @Nullable StringList reposToSearch, boolean remote, boolean limitSearch, boolean exact) {
        throw new MissingRestAddonException();
    }

    @Override
    public void writeStreamingFileList(HttpServletResponse response, String requestUrl, String path, int deep,
            int depth, int listFolders, int mdTimestamps, int statsTimestamps, int includeRootPath, int propertiesMd5) {
        throw new MissingRestAddonException();
    }

    @Override
    public Response getLatestVersionByProperties(String repoKey, String path, Map<String, String[]> parameterMap,
            HttpServletRequest request) {
        throw new MissingRestAddonException();
    }

    @Override
    public ResponseCtx downloadPlugin(String pluginName) {
        throw new MissingRestAddonException();
    }

    @Override
    public ResponseCtx getPluginStatus() {
        return pluginEngine().getInstalledPluginsStatus();
    }

    @Override
    public ResponseCtx deletePlugin(String pluginName) {
        throw new MissingRestAddonException();
    }

}
