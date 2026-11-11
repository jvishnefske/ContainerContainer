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

package org.artifactory.addon;

import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.artifactory.addon.analytics.AnalyticsAddon;
import org.artifactory.addon.ansible.AnsibleAddon;
import org.artifactory.addon.bower.BowerAddon;
import org.artifactory.addon.cargo.CargoAddon;
import org.artifactory.addon.chef.ChefAddon;
import org.artifactory.addon.cocoapods.CocoaPodsAddon;
import org.artifactory.addon.composer.ComposerAddon;
import org.artifactory.addon.conan.ConanAddon;

import org.artifactory.addon.curation.CachedPackageEvent;
import org.artifactory.addon.curation.CuratedRepoEvent;
import org.artifactory.addon.curation.CurationAddon;
import org.artifactory.addon.curation.CurationCachedPackageStatus;
import org.artifactory.addon.curation.CurationInterfaceSettings;
import org.artifactory.addon.curation.CurationPackageStatus;
import org.artifactory.addon.curation.CurationPackageStatusPayload;
import org.artifactory.addon.curation.SecurityDecision;
import org.artifactory.addon.curation.CurationStatus;
import org.artifactory.addon.curation.CurationSupportedPackageType;
import org.artifactory.addon.curation.CurationSupportedRepository;
import org.artifactory.addon.curation.ToggleCuration;
import org.artifactory.addon.debian.DebianAddon;
import org.artifactory.addon.distribution.DistributionAddon;
import org.artifactory.addon.docker.DockerAddon;
import org.artifactory.addon.federated.FederatedRepoAddon;
import org.artifactory.addon.filteredresources.FilteredResourcesAddon;
import org.artifactory.addon.gems.GemsAddon;
import org.artifactory.addon.git.GitAddon;
import org.artifactory.addon.ha.workitem.HaMessageWorkItem;
import org.artifactory.addon.helmoci.oci.HelmOCIAddon;
import org.artifactory.addon.jfevent.JfEventAddon;
import org.artifactory.addon.keys.KeysAddon;
import org.artifactory.addon.ldapgroup.LdapUserGroupAddon;
import org.artifactory.addon.leadartifactdetector.LeadArtifactDetectorAddon;
import org.artifactory.addon.license.LicensesAddon;
import org.artifactory.addon.nuget.UiNuGetAddon;
import org.artifactory.addon.oauth.OAuthSsoAddon;
import org.artifactory.addon.observability.ObservabilityAddon;
import org.artifactory.addon.oci.OciAddon;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.addon.opkg.OpkgAddon;
import org.artifactory.addon.packagesarchive.PackagesArchiveAddon;
import org.artifactory.addon.properties.ArtifactPropertiesAddon;
import org.artifactory.addon.pub.PubAddon;
import org.artifactory.addon.puppet.PuppetAddon;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.addon.replication.PushReplicationSettings;
import org.artifactory.addon.replication.RemoteReplicationSettings;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.addon.replication.event.ReplicationEventQueueData;
import org.artifactory.addon.replication.event.ReplicationOwnerModel;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.addon.security.JvmConflictGuardProvider;
import org.artifactory.addon.signed.url.SignedUrlAddon;
import org.artifactory.addon.smartrepo.EdgeSmartRepoAddon;
import org.artifactory.addon.smartrepo.SmartRepoAddon;
import org.artifactory.addon.sso.CentralOAuthSSOAddon;
import org.artifactory.addon.sso.HttpSsoAddon;
import org.artifactory.addon.sso.crowd.CrowdAddon;
import org.artifactory.addon.sso.saml.SamlSsoAddon;
import org.artifactory.addon.support.SupportAddon;
import org.artifactory.addon.swift.SwiftAddon;
import org.artifactory.addon.terraform.TerraformAddon;
import org.artifactory.addon.watch.ArtifactWatchAddon;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.addon.worker.WorkerEventsModel;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.addon.yum.YumAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.addon.Addon;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.federated.FederatedRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.Request;
import org.artifactory.request.RequestContext;
import org.artifactory.resource.UnfoundRepoResource;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.props.auth.model.OAuth2TokenModel;
import org.artifactory.storage.fs.lock.FsItemsVault;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.util.UnsupportedByLicenseException;
import org.glassfish.jersey.internal.guava.Sets;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.metrics.backoffice.exception.BackofficeMetricsException;
import org.jfrog.metrics.backoffice.model.Metric;
import org.jfrog.worker.proto.execution.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.artifactory.util.RepoLayoutUtils.GRADLE_DEFAULT_DEPRECATED;
import static org.artifactory.util.RepoLayoutUtils.IVY_DEFAULT;
import static org.artifactory.util.RepoLayoutUtils.MAVEN_1_DEFAULT;
import static org.artifactory.util.RepoLayoutUtils.MAVEN_2_DEFAULT;

/**
 * Default implementation of the core-related addon factories.
 *
 * @author Yossi Shaul
 */
@Component
public class CoreAddonsImpl
        implements WebstartAddon, LicensesAddon, LdapGroupAddon, CrowdAddon, PropertiesAddon, LayoutsCoreAddon,
        FilteredResourcesAddon, ReplicationAddon, YumAddon, NuGetAddon, RestCoreAddon, KeysAddon,
        GemsAddon, HaAddon, BowerAddon, DebianAddon, OpkgAddon, PuppetAddon, DockerAddon,
        VagrantAddon, ArtifactWatchAddon, UiNuGetAddon, LdapUserGroupAddon,
        CocoaPodsAddon, ArtifactWebstartAddon, SamlSsoAddon, OAuthSsoAddon, CentralOAuthSSOAddon, HttpSsoAddon,
        SmartRepoAddon, SwiftAddon, EdgeSmartRepoAddon, ArtifactPropertiesAddon, SupportAddon, XrayAddon,
        ComposerAddon, ConanAddon, ChefAddon, ReleaseBundleAddon, CargoAddon, PubAddon,
        DistributionAddon, FederatedRepoAddon,
        SecurityResourceAddon, TerraformAddon, SignedUrlAddon, ObservabilityAddon, JfEventAddon,
        GitAddon, ArtifactRetentionAddon, OnBoardingAddon, AnalyticsAddon, WorkersAddon,
        CurationAddon, OciAddon, HelmOCIAddon, Addon, PackagesArchiveAddon, LeadArtifactDetectorAddon,
        MetricsLoggerAddon, AnsibleAddon {

    private static final Logger log = LoggerFactory.getLogger(CoreAddonsImpl.class);
    private static final int DEFAULT_LAYOUT_NUMBER = 25;
    private static final CurationPackageStatus PERMITTED_CURATION_NOT_ENABLED =
            new CurationPackageStatus(true, "Curation not enabled", SecurityDecision.CURATION_NOT_ENABLED);

    private final JvmConflictGuardProvider jvmConflictGuardProvider = new JvmConflictGuardProvider();

    public CoreAddonsImpl() {
        log.debug("Initializing JVM locking provider");
    }

    private JvmConflictGuardProvider getConflictGuardProvider() {
        return jvmConflictGuardProvider;
    }

    @Override
    public boolean isDefault() {
        return true;
    }

    @Override
    public void addExternalGroups(String userName, Set<UserGroupInfo> groups) {
        //noop
    }

    @Override
    public RepoResource getFilteredResource(Request request, FileInfo fileInfo, InputStream fileInputStream) {
        return new UnfoundRepoResource(fileInfo.getRepoPath(),
                "Creation of a filtered resource requires the Properties add-on.", HttpStatus.SC_FORBIDDEN);
    }

    @Override
    public RepoResource getZipResource(Request request, FileInfo fileInfo, InputStream stream) {
        return new UnfoundRepoResource(fileInfo.getRepoPath(),
                "Direct resource download from zip requires the Filtered resources add-on.", HttpStatus.SC_FORBIDDEN);
    }

    @Override
    public void assertLayoutConfigurationsBeforeSave(CentralConfigDescriptor newDescriptor) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        // Even that we are in the CoreAddonsImpl, it is not for sure that we are on OSS, it might be that we got
        // the CoreAddonsImpl because there is not yet license activated, so we got this bean from the
        // AddonManager#addonByType due to the missing license
        if (addonsManager.getArtifactoryRunningMode().isOss()) {
            List<RepoLayout> repoLayouts = newDescriptor.getRepoLayouts();
            if ((repoLayouts == null) || repoLayouts.isEmpty()) {
                throw new ConfigurationException("Could not find any repository layouts.");
            }

            repoLayouts = newDescriptor.getRepoLayouts().stream().filter(layout ->
                    !GRADLE_DEFAULT_DEPRECATED.getName().equals(layout.getName())).collect(toList());


            if (repoLayouts.size() != DEFAULT_LAYOUT_NUMBER) {
                throw new ConfigurationException(
                        "There should be " + DEFAULT_LAYOUT_NUMBER + " default (excluding deprecated gradle-default) " +
                                "repository layouts, but found: " + repoLayouts.size());
            }
            assertLayoutsExistsAndEqual(repoLayouts, MAVEN_2_DEFAULT, IVY_DEFAULT, MAVEN_1_DEFAULT);
        }
    }

    private void assertLayoutsExistsAndEqual(List<RepoLayout> repoLayouts, RepoLayout... expectedLayouts) {
        for (RepoLayout expectedLayout : expectedLayouts) {
            assertLayoutExistsAndEqual(repoLayouts, expectedLayout);
        }
    }

    private void assertLayoutExistsAndEqual(List<RepoLayout> repoLayouts, RepoLayout expectedLayout) {
        if (!repoLayouts.contains(expectedLayout)) {
            throw new ConfigurationException("Could not find the default repository layout: " +
                    expectedLayout.getName());
        }

        RepoLayout existingLayoutConfig = repoLayouts.get(repoLayouts.indexOf(expectedLayout));
        if (!expectedLayout.equals(existingLayoutConfig)) {
            throw new ConfigurationException("The configured repository layout '" + expectedLayout.getName() +
                    "' is different from the default configuration.");
        }
    }

    @Override
    public BasicStatusHolder performRemoteReplication(RemoteReplicationSettings settings) {
        return getReplicationRequiredStatusHolder();
    }

    @Override
    public BasicStatusHolder performLocalReplication(PushReplicationSettings settings) {
        return getReplicationRequiredStatusHolder();
    }

    @Override
    public void scheduleImmediateLocalReplicationTask(LocalReplicationDescriptor replicationDescriptor,
            BasicStatusHolder statusHolder) {
        printMissingReplicationTaskSupportError(statusHolder, HttpStatus.SC_BAD_REQUEST);
    }

    @Override
    public void scheduleImmediateRemoteReplicationTask(RemoteReplicationDescriptor replicationDescriptor,
            BasicStatusHolder statusHolder) {
        printMissingReplicationTaskSupportError(statusHolder, HttpStatus.SC_BAD_REQUEST);
    }

    private void printMissingReplicationTaskSupportError(BasicStatusHolder statusHolder, int status) {
        statusHolder.error("Error: the replication addon is required for this operation.", status, log);
    }

    @Override
    public BasicStatusHolder filterIfMultiPushIsNotAllowed(List<LocalReplicationDescriptor> pushReplications) {
        BasicStatusHolder multiStatusHolder = new BasicStatusHolder();
        multiStatusHolder.error("Error: an HA license and the replication addon are required for this operation.",
                HttpStatus.SC_BAD_REQUEST, log);
        return multiStatusHolder;
    }

    private BasicStatusHolder getReplicationRequiredStatusHolder() {
        BasicStatusHolder multiStatusHolder = new BasicStatusHolder();
        multiStatusHolder.error("Error: the replication addon is required for this operation.",
                HttpStatus.SC_BAD_REQUEST, log);
        return multiStatusHolder;
    }

    @Override
    public void deployArchiveBundle(ArtifactoryRequest request, ArtifactoryResponse response, LocalRepo repo)
            throws IOException {
        response.sendError(HttpStatus.SC_BAD_REQUEST, "This REST API is available only in Artifactory Pro.", log);
    }

    /**
     * <pre>@Async</pre> implementors
     */

    @Override
    public void reindexAsync(String repoKey) {
        //noop
    }

    @Override
    public void notifyAsync(HaMessageWorkItem workItem) {
        //noop
    }

    @Override
    public void propagateReplicationListener(ReplicationOwnerModel replicationChannelModel) {
        //noop
    }

    @Override
    public void propagateRemoveReplicationListener(ReplicationOwnerModel replicationChannelModel) {
        //noop
    }

    @Override
    public void propagateReplicationEvents(String target, ReplicationEventQueueData queue) {
        //noop
    }

    @Override
    public void propagateStopSha256Migration(long sleepIntervalMillis) {
        //noop
    }

    @Override
    public void propagateAddColumnMonitorMigration() {
        //noop
    }

    @Override
    public void extractNuPkgInfo(FileInfo fileInfo, MutableStatusHolder statusHolder, boolean addToCache) {
        //noop
    }

    @Override
    public void extractSNuPkgInfo(FileInfo fileInfo, MutableStatusHolder statusHolder) {
        //noop
    }

    @Override
    public void addNuPkgToRepoCacheAsync(RepoPath repoPath, Properties properties) {
        //noop
    }

    /**
     * End @Async implementors
     **/

    @Override
    public void afterRepoInit(String repoKey) {
        //noop
    }

    @Override
    public void requestAsyncReindexNuPkgs(String repoKey) {
        //noop
    }

    @Override
    public void requestAsyncReindexSNuPkgs(String repoKey) {
        //noop
    }

    @Override
    public void handleAddAfterCommit(FileInfo info, @Nullable String version) {
        //noop
    }

    @Override
    public void addPodAfterCommit(FileInfo info) {
        //noop
    }

    @Override
    public void cachePodProperties(FileInfo info) {
        //noop
    }

    @Override
    public void removePod(FileInfo info, Properties properties) {
        //noop
    }

    @Override
    public void indexRepos(List<String> repos) {
        //noop
    }

    @Override
    public boolean isXrayVersionSupportProjects() {
        return false;
    }

    @Override
    public String getXrayVersion() {
        return "Unavailable";
    }

    @Override
    public void reportReposIndexDiff(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges) {
        //Unsupported in OSS
    }

    @Override
    public boolean isCurationSupportedForRepo(String packageType, String url) {
        return false;
    }

    @Override
    public ConflictGuard getConflictGuard(String key) {
        return getConflictGuardProvider().getConflictGuard(key);
    }

    @Override
    public FsItemsVault getFsFileItemVault() {
        return getConflictGuardProvider().getFileLockingMap();
    }

    @Override
    public FsItemsVault getFsFolderItemVault() {
        return getConflictGuardProvider().getFolderLockingMap();
    }

    @Override
    public <T> ConflictsGuard<T> getConflictsGuard(String mapName) {
        return getConflictGuardProvider().getConflictsGuard(mapName);
    }

    @Override
    public void recalculateAll(String repoKey, boolean async) {
        // Unsupported in OSS
    }

    @Override
    public void exportTo(ExportSettings exportSettings) {
        // Unsupported in OSS
    }

    @Override
    public void importFrom(ImportSettings importSettings) {
        // Unsupported in OSS
    }

    /**
     * Creates new FederatedRepo instance.
     * Not supported in opensource
     *
     * @param repoService Repository service
     * @param descriptor  The repository descriptor
     * @return New virtual repository
     */
    @Override
    public FederatedRepo createFederatedRepo(InternalRepositoryService repoService,
            FederatedRepoDescriptor descriptor,
            FederatedRepoConfig repoConfig,
            DbLocalRepo<FederatedRepoDescriptor, FederatedRepoConfig> oldFederatedRepo) {
        throw new UnsupportedOperationException("Unsupported repository type: federated");
    }

    @Override
    public Set<String> getUnstableRepositories() {
        return Sets.newHashSet();
    }

    @Override
    public Set<String> getImportConflictsErrors() {
        return Sets.newHashSet();
    }


    @Override
    public void runWorker(@NonNull Action action, Object... args) {
        // empty
    }

    @Override
    public void runWorkerAutoInjectedSecurityAndRepoConfig(@NonNull Action action, Object... args) {
        // empty
    }

    @Override
    public void runWorkerAsyncAutoInjectedSecurityAndRepoConfig(@NonNull Action action, Object... args) {
        // empty
    }

    @Override
    public void run(@NonNull SupportedUnifiedEvents event, Object... args) {
        // empty
    }

    @Override
    public List<WorkerEventsModel> getAvailableEvents() {
        return new ArrayList<WorkerEventsModel>() {
        };
    }

    @Override
    public void refreshMasterToken() {
        // Unsupported in OSS
    }

    @Override
    public Response toggleCuration(ToggleCuration body) {
        throw new UnsupportedByLicenseException(
                "Curation is only available on Pro+ licensed Artifactory instances.");
    }

    @Override
    public boolean isUserAllowed(String currentUsername) {
        return false;
    }

    @Override
    public boolean isCurationEntitled() {
        return false;
    }

    @Override
    public boolean isCurationEnabled() {
        return false;
    }

    @Override
    public boolean isCurationConfigValid(RemoteRepoConfig config) {
        return true;
    }

    @Override
    public List<CurationSupportedPackageType> getSupportedPackageTypes() {
        return Collections.emptyList();
    }

    @Override
    public List<CurationSupportedRepository> getSupportedRepositories() {
        return Collections.emptyList();
    }

    @Override
    public CurationStatus getCurationStatus() {
        return CurationStatus.DISABLED;
    }

    @Override
    public CurationPackageStatus getCachedPackageStatus(RemoteRepoConfig config, RepoPath path, String downloadUrl,
            String mimeType) {
        return PERMITTED_CURATION_NOT_ENABLED;
    }

    @Override
    public CurationCachedPackageStatus getPackageStatus(RemoteRepoConfig config, RepoPath path, String downloadUrl,
            Header[] headers, boolean fromCache) {
        return new CurationCachedPackageStatus(null, PERMITTED_CURATION_NOT_ENABLED);
    }

    @Override
    public void invalidatePackageStatusCache() {
        throw new UnsupportedByLicenseException("Curation is only available on Pro+ licensed Artifactory instances.");
    }

    @Override
    public void sendCachedPackageEvent(RemoteRepoConfig config, RepoPath path) {
        // Unsupported in OSS
    }

    @Override
    public void audit(HttpServletRequest request, HttpServletResponse response, String path) {
        throw new UnsupportedByLicenseException("Curation is only available on Pro+ licensed Artifactory instances.");
    }

    @Override
    public boolean shouldPerformPassThrough(RemoteRepoConfig config, RequestContext context) {
        return false;
    }

    @Override
    public void invalidateSettingsCache() {
        throw new UnsupportedByLicenseException("Curation is only available on Pro+ licensed Artifactory instances.");
    }

    @Override
    public List<CurationSupportedPackageType> getCurationPackageTypes() {
        return Collections.emptyList();
    }

    @Override
    public CurationPackageStatus getCurationCachedPackageStatus(CurationPackageStatusPayload payload,
            boolean allowDownloadWhenUnavailable) {
        return PERMITTED_CURATION_NOT_ENABLED;
    }

    @Override
    public CurationPackageStatus getCurationPackageStatus(CurationPackageStatusPayload payload, boolean fromCache,
            boolean allowDownloadWhenUnavailable) {
        return PERMITTED_CURATION_NOT_ENABLED;
    }

    @Override
    public void invalidateCurationPackageStatusCache() {
        // Unsupported in OSS
    }

    @Override
    public boolean isBlockDownloadEnabled(RemoteRepoConfig config) {
        return false;
    }

    @Override
    public void updateInterfaceSettings(CurationInterfaceSettings settings) {
        throw new UnsupportedByLicenseException("Curation is not supported in OSS");
    }

    @Override
    public CurationInterfaceSettings getInterfaceSettings() {
        return new CurationInterfaceSettings(false);
    }

    @Override
    public boolean isAllowDownloadWhenUnavailable() {
        return false;
    }

    @Override
    public void sendCuratedRepoEvent(CuratedRepoEvent event) {
        // Unsupported in OSS
    }

    @Override
    public void sendCachedPackageEvent(CachedPackageEvent event) {
        // Unsupported in OSS
    }

    @Override
    public boolean log(Metric metric) throws BackofficeMetricsException {
        return false;
    }

    @Override
    @Nullable
    public CurationResponse filterBlockedVersions(@Nonnull CurationRequest curationRequest) {
        return null;
    }

    @Override
    @Nullable
    public OAuth2TokenModel fetchSsoToken(String authUrl, String refreshToken, String repoKey) {
        return null;
    }
}
