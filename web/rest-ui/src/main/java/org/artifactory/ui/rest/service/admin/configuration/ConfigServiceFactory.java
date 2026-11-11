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

package org.artifactory.ui.rest.service.admin.configuration;

import org.artifactory.rest.common.service.admin.reverse.proxies.CheckReverseProxyPortAvailabilityService;
import org.artifactory.rest.common.service.admin.reverse.proxies.CreateReverseProxyService;
import org.artifactory.rest.common.service.admin.reverse.proxies.GetReverseProxiesService;
import org.artifactory.rest.common.service.admin.reverse.proxies.UpdateReverseProxyService;
import org.artifactory.rest.common.service.admin.xray.AddIndexXrayService;
import org.artifactory.rest.common.service.admin.xray.DeleteIndexXrayService;
import org.artifactory.rest.common.service.admin.xray.GetIndexXrayService;
import org.artifactory.rest.common.service.admin.xray.GetNoneIndexXrayService;
import org.artifactory.rest.common.service.admin.xray.GetXrayIntegrationConfigService;
import org.artifactory.rest.common.service.admin.xray.UpdateAllowBlockedArtifactsDownloadService;
import org.artifactory.rest.common.service.admin.xray.UpdateAllowDownloadWhenXrayUnavailableService;
import org.artifactory.rest.common.service.admin.xray.UpdateEnableXrayService;
import org.artifactory.rest.common.service.admin.xray.UpdateXrayBasicSettings;
import org.artifactory.rest.common.service.admin.xray.UpdateXrayIndexRepos;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAllowedReposModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.general.DeleteUploadedLogoService;
import org.artifactory.ui.rest.service.admin.configuration.general.GetArtifactoryConfigService;
import org.artifactory.ui.rest.service.admin.configuration.general.GetGeneralConfigDataService;
import org.artifactory.ui.rest.service.admin.configuration.general.GetGeneralConfigService;
import org.artifactory.ui.rest.service.admin.configuration.general.GetPlatformBaseUrlService;
import org.artifactory.ui.rest.service.admin.configuration.general.GetPlatformConfigService;
import org.artifactory.ui.rest.service.admin.configuration.general.GetUploadLogoService;
import org.artifactory.ui.rest.service.admin.configuration.general.UpdateArtifactoryConfigService;
import org.artifactory.ui.rest.service.admin.configuration.general.UpdateGeneralConfigService;
import org.artifactory.ui.rest.service.admin.configuration.general.UpdatePlatformConfigService;
import org.artifactory.ui.rest.service.admin.configuration.general.UploadLogoService;
import org.artifactory.ui.rest.service.admin.configuration.ha.GetHighAvailabilityMembersService;
import org.artifactory.ui.rest.service.admin.configuration.ha.RemoveServerService;
import org.artifactory.ui.rest.service.admin.configuration.ha.license.AddClusterLicensesService;
import org.artifactory.ui.rest.service.admin.configuration.ha.license.GetClusterLicensesService;
import org.artifactory.ui.rest.service.admin.configuration.ha.license.RemoveClusterLicensesService;
import org.artifactory.ui.rest.service.admin.configuration.layouts.CreateLayoutService;
import org.artifactory.ui.rest.service.admin.configuration.layouts.DeleteLayoutService;
import org.artifactory.ui.rest.service.admin.configuration.layouts.GetLayoutInfoService;
import org.artifactory.ui.rest.service.admin.configuration.layouts.GetLayoutsService;
import org.artifactory.ui.rest.service.admin.configuration.layouts.ResolveRegexService;
import org.artifactory.ui.rest.service.admin.configuration.layouts.TestArtPathService;
import org.artifactory.ui.rest.service.admin.configuration.layouts.UpdateLayoutService;
import org.artifactory.ui.rest.service.admin.configuration.licenses.CreateArtifactLicenseService;
import org.artifactory.ui.rest.service.admin.configuration.licenses.DeleteArtifactLicenseService;
import org.artifactory.ui.rest.service.admin.configuration.licenses.ExportLicenseFileService;
import org.artifactory.ui.rest.service.admin.configuration.licenses.GetArtifactLicenseService;
import org.artifactory.ui.rest.service.admin.configuration.licenses.UpdateArtifactLicenseService;
import org.artifactory.ui.rest.service.admin.configuration.mail.GetMailService;
import org.artifactory.ui.rest.service.admin.configuration.mail.TestMailService;
import org.artifactory.ui.rest.service.admin.configuration.mail.UpdateMailService;
import org.artifactory.ui.rest.service.admin.configuration.projects.GetUnassignedResourcesStats;
import org.artifactory.ui.rest.service.admin.configuration.propertysets.CreatePropertySetService;
import org.artifactory.ui.rest.service.admin.configuration.propertysets.DeletePropertySetService;
import org.artifactory.ui.rest.service.admin.configuration.propertysets.GetConfigPropertySetNamesService;
import org.artifactory.ui.rest.service.admin.configuration.propertysets.GetConfigPropertySetService;
import org.artifactory.ui.rest.service.admin.configuration.propertysets.UpdatePropertySetService;
import org.artifactory.ui.rest.service.admin.configuration.proxies.CreateProxyService;
import org.artifactory.ui.rest.service.admin.configuration.proxies.DeleteProxyService;
import org.artifactory.ui.rest.service.admin.configuration.proxies.GetProxiesService;
import org.artifactory.ui.rest.service.admin.configuration.proxies.GetProxyKeysService;
import org.artifactory.ui.rest.service.admin.configuration.proxies.UpdateProxyService;
import org.artifactory.ui.rest.service.admin.configuration.registerpro.GetLicenseKeyService;
import org.artifactory.ui.rest.service.admin.configuration.registerpro.UpdateLicenseKeyService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.CreateRepositoryConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.DeleteRepositoryConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.GetRepositoryConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.GetRepositoryConfigServiceV2;
import org.artifactory.ui.rest.service.admin.configuration.repositories.RepoKeyLengthLimitsService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.UpdateRepositoryConfigService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.replication.ExecuteAllLocalReplicationsService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.replication.ExecuteLocalReplicationService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.replication.ExecuteRemoteReplicationService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.replication.TestLocalReplicationService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.replication.TestRemoteReplicationService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateDefaultJcenterRepoService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.DiscoverMirrorBaseUrlAndRepos;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.DiscoverMirrorRepos;
import org.artifactory.ui.rest.service.admin.configuration.repositories.GetLocalAvailableRepositories;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetAvailableRepositories;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetAvailableRepositoryFields;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetDefaultRepositoryValues;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetDockerRepoService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetFederatedBuildInfoDetailsService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetIndexerAvailableRepositories;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetPackageIndexingPercentilesService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetRemoteRepoUrlMappingService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetRemoteUrlsByType;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetRepositoryInfoService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetResolvedRepositories;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.GetVirtualAllowedRepos;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.IsJcenterConfiguredService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.MigrateLocalToFederatedRepository;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.RemoteRepositoryTestUrl;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.ReorderRepositoriesService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.MetadataCachePeriodExclusionService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.SmartRepoCapabilitiesDiscoveryService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateFederationPriorityService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.ValidateFederatedRepoMirrorsService;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.ValidateRepoNameService;
import org.artifactory.ui.rest.service.admin.configuration.servers.GetServersStatusService;
import org.artifactory.ui.rest.service.admin.configuration.trashcan.DisableTrashcanStrategyService;
import org.springframework.beans.factory.annotation.Lookup;

/**
 * @author Chen Keinan
 */
public abstract class ConfigServiceFactory {


    // mail services
    @Lookup
    public abstract UpdateMailService updateMailService();

    @Lookup
    public abstract GetMailService getMailService();

    @Lookup
    public abstract TestMailService testMailService();

    //register pro service
    @Lookup
    public abstract GetLicenseKeyService getLicenseKeyService();

    @Lookup
    public abstract UpdateLicenseKeyService updateLicenseKeyService();

    // Get licenses details from HA cluster
    @Lookup
    public abstract GetClusterLicensesService getClusterLicensesService();

    // Add one or more licenses to an HA cluster
    @Lookup
    public abstract AddClusterLicensesService addClusterLicensesService();

    // Remove one or more licenses from an HA cluster
    @Lookup
    public abstract RemoveClusterLicensesService removeClusterLicensesService();

    // proxies services
    @Lookup
    public abstract CreateProxyService createProxiesService();

    @Lookup
    public abstract UpdateProxyService updateProxiesService();

    @Lookup
    public abstract GetProxiesService getProxiesService();

    @Lookup
    public abstract DeleteProxyService deleteProxiesService();

    // reverse proxies services
    @Lookup
    public abstract CreateReverseProxyService createReverseProxy();

    @Lookup
    public abstract UpdateReverseProxyService updateReverseProxy();

    @Lookup
    public abstract GetReverseProxiesService getReverseProxies();

    // licenses services
    @Lookup
    public abstract ExportLicenseFileService exportLicenseFileService();

    @Lookup
    public abstract CreateArtifactLicenseService createArtifactLicenseService();

    @Lookup
    public abstract UpdateArtifactLicenseService updateArtifactLicenseService();

    @Lookup
    public abstract GetArtifactLicenseService getArtifactLicenseService();

    @Lookup
    public abstract DeleteArtifactLicenseService deleteArtifactLicenseService();

    @Lookup
    public abstract GetGeneralConfigService getGeneralConfig();

    @Lookup
    public abstract GetPlatformConfigService getPlatformConfig();

    @Lookup
    public abstract GetArtifactoryConfigService getArtifactoryConfig();

    @Lookup
    public abstract GetGeneralConfigDataService getGeneralConfigData();

    @Lookup
    public abstract UpdateGeneralConfigService updateGeneralConfig();

    @Lookup
    public abstract UpdateArtifactoryConfigService updateArtifactoryConfig();

    @Lookup
    public abstract UpdatePlatformConfigService updatePlatformConfig();

    @Lookup
    public abstract UploadLogoService uploadLogo();

    @Lookup
    public abstract GetUploadLogoService getUploadLogo();

    @Lookup
    public abstract DeleteUploadedLogoService deleteUploadedLogo();

    @Lookup
    public abstract CreatePropertySetService createPropertySet();

    @Lookup
    public abstract GetConfigPropertySetNamesService getPropertySetNames();

    @Lookup
    public abstract GetConfigPropertySetService getPropertySet();

    @Lookup
    public abstract UpdatePropertySetService updatePropertySet();

    @Lookup
    public abstract DeletePropertySetService deletePropertySet();

    // configuration repository service
    @Lookup
    public abstract CreateRepositoryConfigService createRepositoryConfig();

    @Lookup
    public abstract GetRepositoryConfigService getRepositoryConfig();

    @Lookup
    public abstract UpdateRepositoryConfigService updateRepositoryConfig();

    @Lookup
    public abstract DeleteRepositoryConfigService deleteRepositoryConfig();

    @Lookup
    public abstract GetRepositoryInfoService getRepositoriesInfo();

    @Lookup
    public abstract RemoteRepositoryTestUrl<RemoteRepositoryConfigModel> remoteRepositoryTestUrl();

    @Lookup
    public abstract SmartRepoCapabilitiesDiscoveryService<RemoteRepositoryConfigModel> discoverSmartRepoCapabilities();

    @Lookup
    public abstract MetadataCachePeriodExclusionService validateMetadataCachePeriod();

    @Lookup
    public abstract GetAvailableRepositoryFields getAvailableRepositoryFieldChoices();

    @Lookup
    public abstract GetDefaultRepositoryValues getDefaultRepositoryValues();

    @Lookup
    public abstract GetRemoteUrlsByType getRemoteUrlsByType();

    @Lookup
    public abstract GetRemoteRepoUrlMappingService getRemoteReposUrlMapping();

    @Lookup
    public abstract ValidateRepoNameService validateRepoName();

    @Lookup
    public abstract RepoKeyLengthLimitsService getRepoKeyLengthLimits();

    @Lookup
    public abstract ValidateFederatedRepoMirrorsService validateFederatedRepoMirrors();

    @Lookup
    public abstract MigrateLocalToFederatedRepository migrateLocalToFederatedRepository();

    @Lookup
    public abstract DiscoverMirrorRepos discoverMirrorRepos();

    @Lookup
    public abstract DiscoverMirrorBaseUrlAndRepos discoverMirrorBaseUrlAndRepos();

    @Lookup
    public abstract UpdateFederationPriorityService updateFederationPriority();

    @Lookup
    public abstract ExecuteRemoteReplicationService executeImmediateReplication();

    @Lookup
    public abstract TestLocalReplicationService testLocalReplication();

    @Lookup
    public abstract TestRemoteReplicationService testRemoteReplication();

    @Lookup
    public abstract GetLayoutsService getLayoutsService();

    @Lookup
    public abstract GetLayoutInfoService getLayoutInfoService();

    @Lookup
    public abstract UpdateLayoutService updateLayoutService();

    @Lookup
    public abstract CreateLayoutService createLayoutService();

    @Lookup
    public abstract DeleteLayoutService deleteLayoutService();

    @Lookup
    public abstract TestArtPathService testArtPathService();

    @Lookup
    public abstract ResolveRegexService resolveRegexService();

    @Lookup(value = "allAvailableRepositories")
    public abstract GetAvailableRepositories getAvailableRepositories();

    @Lookup
    public abstract GetIndexerAvailableRepositories getIndexerAvailableRepositories();

    @Lookup
    public abstract GetPackageIndexingPercentilesService getPackageIndexingPercentiles();

    @Lookup
    public abstract GetResolvedRepositories<VirtualRepositoryConfigModel> getResolvedRepositories();

    @Lookup
    public abstract GetVirtualAllowedRepos<VirtualAllowedReposModel> getVirtualAllowedRepos();

    @Lookup
    public abstract ExecuteAllLocalReplicationsService executeAllLocalReplications();

    @Lookup
    public abstract ExecuteLocalReplicationService<LocalRepositoryConfigModel> executeLocalReplication();

    @Lookup
    public abstract ReorderRepositoriesService reorderRepositories();

    @Lookup
    public abstract IsJcenterConfiguredService isJcenterConfigured();

    @Lookup
    public abstract GetDockerRepoService getDockerRepo();

    @Lookup
    public abstract CreateDefaultJcenterRepoService createDefaultJcenterRepo();

    @Lookup
    public abstract GetHighAvailabilityMembersService getHighAvailabilityMembers();

    @Lookup
    public abstract GetServersStatusService getServersStatus();

    @Lookup
    public abstract RemoveServerService removeServer();

    @Lookup
    public abstract CheckReverseProxyPortAvailabilityService checkReverseProxyPortAvailability();

    // xray
    @Lookup
    public abstract UpdateXrayIndexRepos updateXrayIndexRepos();

    @Lookup
    public abstract GetIndexXrayService getXrayIndexedRepo();

    @Lookup
    public abstract GetXrayIntegrationConfigService GetXrayIntegrationConfig();

    @Lookup
    public abstract UpdateXrayBasicSettings updateXrayBasicSettings();

    @Lookup
    public abstract UpdateEnableXrayService setXrayEnabled();

    @Lookup
    public abstract UpdateAllowDownloadWhenXrayUnavailableService setAllowDownloadWhenXrayUnavailable();

    @Lookup
    public abstract UpdateAllowBlockedArtifactsDownloadService setAllowBlockedArtifactsDownload();

    @Lookup
    public abstract GetNoneIndexXrayService getNoneXrayIndexedRepo();

    @Lookup
    public abstract AddIndexXrayService addXrayIndexedRepo();

    @Lookup
    public abstract DeleteIndexXrayService removeXrayIndexedRepo();

    @Lookup
    public abstract GetProxyKeysService getProxyKeysService();

    @Lookup
    public abstract GetPlatformBaseUrlService getPlatformBaseUrlService();

    @Lookup
    public abstract GetUnassignedResourcesStats getUnassignedResourcesStats();

    @Lookup
    public abstract GetFederatedBuildInfoDetailsService getFederatedActivatedBuildInfo();

    @Lookup
    public abstract DisableTrashcanStrategyService getDisableTrashcanStrategy();

    @Lookup
    public abstract GetRepositoryConfigServiceV2 getRepoConfigsByFilters();

    @Lookup
    public abstract GetLocalAvailableRepositories getLocalAvailableRepositories();
}
