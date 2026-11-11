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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.filteredresources.FilteredResourcesAddon;
import org.artifactory.addon.license.LicensesAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.license.LicenseInfo;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.VirtualRepoItem;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.StatsInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.licenses.GeneralTabLicenseModel;
import org.jfrog.storage.common.StorageUnit;

import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.utils.ObfuscationUtils.obfuscateIfNeeded;

/**
 * @author Chen Keinan
 */
@JsonPropertyOrder(
        {
                "name", "repositoryPath",
                "moduleID", "deployedBy", "size", "created", "lastModified", "licenses", "downloaded",
                "lastDownloadedBy", "lastDownloaded", "remoteDownloaded", "lastRemoteDownloadedBy",
                "lastRemoteDownloaded",
                "watchingSince", "showFilteredResourceCheckBox", "filtered"
        }
)
public class FileInfoUiModel extends BaseInfo {

    private String moduleID;
    private String deployedBy;
    private String size;
    private String created;
    private String lastModified;
    private Set<GeneralTabLicenseModel> licenses = Sets.newHashSet();

    private Long downloaded;
    private String lastDownloaded;
    private String lastDownloadedBy;

    private Long remoteDownloaded;
    private String lastRemoteDownloaded;
    private String lastRemoteDownloadedBy;

    private String watchingSince;
    private Boolean filtered;
    private Boolean showFilteredResourceCheckBox;

    public FileInfoUiModel() {
    }

    public FileInfoUiModel(FileInfo fileInfo) {
        populateFileInfo(fileInfo);
    }

    public FileInfoUiModel(RepoPath repoPath, VirtualRepoItem item) {
        populateVirtualRemoteFileInfo(repoPath, item);
    }

    private void populateFileInfo(FileInfo fileInfo) {
        RepositoryService repoService = ContextHelper.get().beanForType(RepositoryService.class);
        AuthorizationService authService = ContextHelper.get().getAuthorizationService();
        RepoPath repoPath = fileInfo.getRepoPath();
        setName(repoPath.getName());
        setRepositoryPath(repoPath.getRepoKey() + "/" + repoPath.getPath());
        setCreated(formatIsoDate(fileInfo.getCreated()));
        setDeployedBy(obfuscateIfNeeded(fileInfo.getModifiedBy()));
        setLastModified(formatIsoDate(fileInfo.getLastModified()));
        setSize(StorageUnit.toReadableString(fileInfo.getSize()));
        updateFileInfoModuleID(repoService, repoPath);
        updateFileInfoStats(repoService, fileInfo);
        // update file info licenses
        updateFileInfoLicenses(repoPath, fileInfo);
        // set watching since
        setWatchingSince(fetchWatchingSince(repoPath));
        // set RemoteDeleted indication and externalUrl from properties
        Properties properties = updateFileProperties(fileInfo);
        // update filtered file info
        updateFilteredResourceInfo(fileInfo, properties, authService);
    }

    private void populateVirtualRemoteFileInfo(RepoPath repoPath, VirtualRepoItem item) {
        // update name
        setName(repoPath.getName());
        // update path
        setRepositoryPath(repoPath.getRepoKey() + "/" + repoPath.getPath());
        // init licenses to null not require for remote and virtual
        licenses = null;

        addVirtualItemDetails(item);
    }

    private Properties updateFileProperties(ItemInfo itemInfo) {
        PropertiesService propertiesService = ContextHelper.get().beanForType(PropertiesService.class);
        Properties properties = propertiesService.getProperties(itemInfo);
        String externalUrl = properties.getFirst("externalUrl");
        if (externalUrl != null) {
            this.setExternalUrl(externalUrl);
        }
        if (isSmartRepo() != null && isSmartRepo()) {
            boolean remoteDeleted = properties.entries().parallelStream()
                    .anyMatch(p -> p.getKey().equals("sourceDeleted") && p.getValue().equals("true"));
            this.setRemoteDeleted(remoteDeleted);
        }
        return properties;
    }

    private void updateFileInfoStats(RepositoryService repoService, FileInfo fileInfo) {
        StatsInfo statsInfo = repoService.getStatsInfo(fileInfo);
        if (statsInfo == null) {
            statsInfo = InfoFactoryHolder.get().createStats();
        }

        // local stats
        this.setDownloaded(statsInfo.getDownloadCount());
        this.setLastDownloadedBy(obfuscateIfNeeded(statsInfo.getLastDownloadedBy()));
        if (statsInfo.getLastDownloaded() != 0) {
            this.setLastDownloaded(formatIsoDate(statsInfo.getLastDownloaded()));
        }

        // remote stats
        this.setRemoteDownloaded(statsInfo.getRemoteDownloadCount());
        if (Strings.isNullOrEmpty(statsInfo.getOrigin())) {
            this.setLastRemoteDownloadedBy(obfuscateIfNeeded(statsInfo.getRemoteLastDownloadedBy()));
        } else {
            this.setLastRemoteDownloadedBy(obfuscateIfNeeded(statsInfo.getRemoteLastDownloadedBy()
                    + "@" + statsInfo.getOrigin()));
        }
        if (statsInfo.getRemoteLastDownloaded() != 0) {
            this.setLastRemoteDownloaded(formatIsoDate(statsInfo.getRemoteLastDownloaded()));
        }
    }

    /**
     * update file info module id
     *
     * @param repoService - repository service
     * @param repoPath    - repository path
     */
    private void updateFileInfoModuleID(RepositoryService repoService, RepoPath repoPath) {
        ModuleInfo moduleInfo = repoService.getItemModuleInfo(repoPath);
        String moduleID;
        if (moduleInfo.isValid()) {
            moduleID = moduleInfo.getPrettyModuleId();
        } else {
            moduleID = "N/A";
        }
        this.setModuleID(moduleID);
    }

    /**
     * update file filtered info
     */
    private void updateFilteredResourceInfo(ItemInfo itemInfo, Properties properties,
            AuthorizationService authService) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        if (authService.canAnnotate(itemInfo.getRepoPath()) &&
                addonsManager.isAddonSupported(AddonType.FILTERED_RESOURCES)) {
            showFilteredResourceCheckBox = true;
            filtered = addonsManager.addonByType(FilteredResourcesAddon.class).isFilteredResourceFile(properties);
        } else {
            showFilteredResourceCheckBox = false;
        }
    }

    /**
     * Returns a list of all licenses set as properties on this path, including black duck licenses if available
     */
    private void updateFileInfoLicenses(RepoPath path, ItemInfo itemInfo) {
        boolean hasLicenses = false;
        if (itemInfo.isFolder()) {
            return;
        }
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        Set<LicenseInfo> pathLicensesByProps = addonsManager.addonByType(
                LicensesAddon.class).getPathLicensesByProps(path);
        if (pathLicensesByProps != null) {
            hasLicenses = true;
            licenses.addAll(pathLicensesByProps
                    .stream()
                    .map(GeneralTabLicenseModel::new)
                    .collect(Collectors.toList()));

            //Remove not found - UI gets an empty array and handles.
            licenses.remove(GeneralTabLicenseModel.NOT_FOUND);
        }
        if (!hasLicenses) {
            licenses = null;
        }
    }

    public VirtualRepoItem getVirtualRepoItem(RepoPath repoPath) {
        ArtifactoryContext context = ContextHelper.get();
        RepositoryService repoService = context.getRepositoryService();
        VirtualRepoDescriptor virtual = repoService.virtualRepoDescriptorByKey(repoPath.getRepoKey());
        VirtualRepoItem item = null;
        if (virtual != null) {
            RepositoryBrowsingService browsingService = context.beanForType(RepositoryBrowsingService.class);
            item = browsingService.getVirtualRepoItem(repoPath);
        }
        return item;
    }

    private void addVirtualItemDetails(VirtualRepoItem item) {
        if (item != null && item.getItemInfo() != null) {
            FileInfo fileInfo = (FileInfo) item.getItemInfo();
            created = formatIsoDate(fileInfo.getCreated());
            lastModified = formatIsoDate(fileInfo.getLastModified());
            size = StorageUnit.toReadableString(fileInfo.getSize());
        }
    }

    public String getModuleID() {
        return moduleID;
    }

    public void setModuleID(String moduleID) {
        this.moduleID = moduleID;
    }

    public String getDeployedBy() {
        return deployedBy;
    }

    public void setDeployedBy(String deployedBy) {
        this.deployedBy = deployedBy;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public Set<GeneralTabLicenseModel> getLicenses() {
        return licenses;
    }

    public void setLicenses(Set<GeneralTabLicenseModel> licenses) {
        this.licenses = licenses;
    }

    public Long getDownloaded() {
        return downloaded;
    }

    public void setDownloaded(Long downloaded) {
        this.downloaded = downloaded;
    }

    public String getLastDownloaded() {
        return lastDownloaded;
    }

    public void setLastDownloaded(String lastDownloaded) {
        this.lastDownloaded = lastDownloaded;
    }

    public String getLastDownloadedBy() {
        return lastDownloadedBy;
    }

    public void setLastDownloadedBy(String lastDownloadedBy) {
        this.lastDownloadedBy = lastDownloadedBy;
    }

    public Long getRemoteDownloaded() {
        return remoteDownloaded;
    }

    public void setRemoteDownloaded(Long remoteDownloaded) {
        this.remoteDownloaded = remoteDownloaded;
    }

    public String getLastRemoteDownloaded() {
        return lastRemoteDownloaded;
    }

    public void setLastRemoteDownloaded(String lastRemoteDownloaded) {
        this.lastRemoteDownloaded = lastRemoteDownloaded;
    }

    public String getLastRemoteDownloadedBy() {
        return lastRemoteDownloadedBy;
    }

    public void setLastRemoteDownloadedBy(String lastRemoteDownloadedBy) {
        this.lastRemoteDownloadedBy = lastRemoteDownloadedBy;
    }

    public String getWatchingSince() {
        return watchingSince;
    }

    public void setWatchingSince(String watchingSince) {
        this.watchingSince = watchingSince;
    }

    public Boolean getFiltered() {
        return filtered;
    }

    public void setFiltered(Boolean filtered) {
        this.filtered = filtered;
    }

    public Boolean getShowFilteredResourceCheckBox() {
        return showFilteredResourceCheckBox;
    }

    public void setShowFilteredResourceCheckBox(Boolean showFilteredResourceCheckBox) {
        this.showFilteredResourceCheckBox = showFilteredResourceCheckBox;
    }
}
