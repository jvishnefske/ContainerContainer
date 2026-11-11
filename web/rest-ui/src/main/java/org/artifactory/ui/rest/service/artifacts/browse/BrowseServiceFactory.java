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

package org.artifactory.ui.rest.service.artifacts.browse;

import org.artifactory.rest.common.service.admin.reverse.proxies.ReverseProxySnippetService;
import org.artifactory.rest.common.service.artifact.AddSha256ToArtifactService;
import org.artifactory.rest.common.service.trash.EmptyTrashService;
import org.artifactory.rest.common.service.trash.RestoreArtifactService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.properties.DeletePropertyModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.watchers.DeleteWatchersModel;
import org.artifactory.ui.rest.service.artifacts.browse.generic.BrowseNativeHtmlService;
import org.artifactory.ui.rest.service.artifacts.browse.generic.BrowseNativeModelService;
import org.artifactory.ui.rest.service.artifacts.browse.generic.BrowseNativeModelV2Service;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.CopyArtifactService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.DeleteArtifactService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.DownloadArtifactService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.DownloadArtifactWithSignedUrlService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.DownloadFolderArchiveService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.GetDownloadFolderInfoService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.MigrateRepositoryService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.MoveArtifactService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.RecalculateDebianCoordinatesService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.RecalculateIndexService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.RestoreLayoutService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.SetXrayAllowDownloadService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.ViewArtifactService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.WatchArtifactService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.ZapArtifactService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.ZapCachesVirtualService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.deleteversions.DeleteVersionService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions.deleteversions.GetVersionUnitsService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.bower.BowerViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.cargo.CargoViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.checksums.FixChecksumsService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.chefview.ChefViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.composer.ComposerViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.conan.ConanPackageViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.conan.ConanViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.debianview.DebianViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.docker.DockerAncestryViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.docker.DockerV2ManifestNativeService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.docker.DockerV2ManifestTreeService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.docker.DockerViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.gemsview.GemsViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.GetArtifactsCountAndSizeService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.GetAssociatedVirtualReposService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.GetDependencyDeclarationService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.GetGeneralArtifactsService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.GetIncludedReposService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.SetFilteredResourceService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.licenses.GetAllAvailableLicensesService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.licenses.GetArchiveLicenseFileService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.licenses.ScanArtifactForLicensesService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.licenses.SetLicensesOnPathService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.nugetview.NugetViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.permission.GetRepoEffectivePermissionService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.properties.CreatePropertyService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.properties.DeletePropertyService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.properties.GetPropertyService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.properties.UpdatePropertyService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.pub.PubViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.puppet.PuppetViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.rpm.RpmViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.swift.SwiftViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.terraform.TerraformBackendViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.terraform.TerraformViewService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.viewsource.ArchiveViewSourceService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.watchers.GetWatchersService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.watchers.RemoveWatchersService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.watchers.WatchStatusService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.xray.GetXrayTabService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.xray.IsArtifactBlockedService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tree.BrowseTreeNodesService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tree.BrowseTreeNodesV2Service;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tree.GetTreeNodeOrderService;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tree.GetTreeNodeTabsAndActionsService;
import org.springframework.beans.factory.annotation.Lookup;

/**
 * @author Chen Keinan
 */
public abstract class BrowseServiceFactory {

    @Lookup
    public abstract BrowseTreeNodesService getBrowseTreeNodesService();

    @Lookup
    public abstract BrowseTreeNodesV2Service getBrowseTreeNodesV2Service();

    @Lookup
    public abstract GetTreeNodeTabsAndActionsService getTreeNodeTabsAndActionsService();

    @Lookup
    public abstract GetTreeNodeOrderService getTreeNodeOrderService();

    @Lookup
    public abstract GetGeneralTreeConfigService getGeneralTreeConfigService();

    @Lookup
    public abstract GetGeneralArtifactsService getGetGeneralArtifactsService();

    @Lookup
    public abstract GetArtifactsCountAndSizeService getArtifactsCountAndSizeService();

    @Lookup
    public abstract GetAssociatedVirtualReposService getAssociatedVirtualReposService();

    @Lookup
    public abstract GetIncludedReposService getIncludedReposService();

    @Lookup
    public abstract GetDependencyDeclarationService getGetDependencyDeclarationService();

    @Lookup
    public abstract CreatePropertyService getCreatePropertyService();

    @Lookup
    public abstract GetPropertyService getGetPropertyService();

    @Lookup
    public abstract UpdatePropertyService getUpdatePropertyService();

    @Lookup
    public abstract DeletePropertyService<DeletePropertyModel> getDeletePropertyService();

    @Lookup
    public abstract GetRepoEffectivePermissionService getRepoEffectivePermissionService();

    @Lookup
    public abstract GemsViewService gemsViewService();

    @Lookup
    public abstract DebianViewService DebianViewService();

    @Lookup
    public abstract GetWatchersService getWatchersService();

    @Lookup
    public abstract RemoveWatchersService<DeleteWatchersModel> getRemoveWatchersService();

    @Lookup
    public abstract DownloadArtifactService downloadArtifactService();

    @Lookup
    public abstract DownloadArtifactWithSignedUrlService downloadArtifactWithSignedUrlService();

    @Lookup
    public abstract GetDownloadFolderInfoService getDownloadFolderInfo();

    @Lookup
    public abstract DownloadFolderArchiveService downloadFolder();

    @Lookup
    public abstract EmptyTrashService emptyTrashService();

    @Lookup
    public abstract RestoreArtifactService restoreArtifactService();

    @Lookup
    public abstract WatchArtifactService watchArtifactService();

    @Lookup
    public abstract CopyArtifactService copyArtifactService();

    @Lookup
    public abstract MoveArtifactService moveArtifactService();

    @Lookup
    public abstract ZapArtifactService zapArtifactService();

    @Lookup
    public abstract ZapCachesVirtualService zapCachesVirtual();

    @Lookup
    public abstract DeleteArtifactService deleteArtifactService();

    @Lookup
    public abstract ViewArtifactService viewArtifactService();

    @Lookup
    public abstract WatchStatusService watchStatusService();

    @Lookup
    public abstract GetVersionUnitsService getDeleteVersionsService();

    @Lookup
    public abstract DeleteVersionService deleteVersionService();

    @Lookup
    public abstract ArchiveViewSourceService archiveViewSourceService();

    @Lookup
    public abstract NugetViewService nugetViewService();

    @Lookup
    public abstract RpmViewService rpmViewService();

    @Lookup
    public abstract PuppetViewService puppetViewService();

    @Lookup
    public abstract BowerViewService bowerViewService();

    @Lookup
    public abstract ConanViewService conanViewService();

    @Lookup
    public abstract ConanPackageViewService conanPackageViewService();

    @Lookup
    public abstract DockerViewService dockerViewService();

    @Lookup
    public abstract DockerV2ManifestTreeService dockerV2ViewTreeService();

    @Lookup
    public abstract DockerV2ManifestNativeService dockerV2ViewNativeService();

    @Lookup
    public abstract DockerAncestryViewService dockerAncestryViewService();

    @Lookup
    public abstract ComposerViewService composerViewService();

    @Lookup
    public abstract ReverseProxySnippetService dockerProxyViewService();

    @Lookup
    public abstract FixChecksumsService fixChecksums();

    @Lookup
    public abstract SetFilteredResourceService setFilteredResource();

    @Lookup
    public abstract GetAllAvailableLicensesService getAllAvailableLicenses();

    @Lookup
    public abstract SetLicensesOnPathService setLicensesOnPath();

    @Lookup
    public abstract ScanArtifactForLicensesService scanArtifactForLicenses();

    @Lookup
    public abstract GetArchiveLicenseFileService getArchiveLicenseFile();

    @Lookup
    public abstract BrowseNativeHtmlService browseNativeHtml();

    @Lookup
    public abstract BrowseNativeModelService browseNativeModel();

    @Lookup
    public abstract BrowseNativeModelV2Service browseNativeModelV2();


    @Lookup
    public abstract RecalculateIndexService recalculateIndex();

    @Lookup
    public abstract RecalculateDebianCoordinatesService calculateDebianCoordinates();

    @Lookup
    public abstract AddSha256ToArtifactService addSha256ToArtifact();

    @Lookup
    public abstract SetXrayAllowDownloadService setAllowDownload();

    @Lookup
    public abstract MigrateRepositoryService migrateRepositoryService();

    @Lookup
    public abstract RestoreLayoutService restoreLayoutService();

    @Lookup
    public abstract ChefViewService chefViewService();

    @Lookup
    public abstract TerraformViewService terraformViewService();

    @Lookup
    public abstract TerraformBackendViewService terraformBackendViewService();

    @Lookup
    public abstract GetXrayTabService getXrayTabService();

    @Lookup
    public abstract IsArtifactBlockedService isArtifactBlocked();

    @Lookup
    public abstract CargoViewService cargoViewService();

    @Lookup
    public abstract PubViewService pubViewService();

    @Lookup
    public abstract SwiftViewService swiftViewService();
}
