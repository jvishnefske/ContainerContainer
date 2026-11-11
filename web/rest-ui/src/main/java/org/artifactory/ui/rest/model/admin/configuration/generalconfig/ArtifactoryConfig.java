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

package org.artifactory.ui.rest.model.admin.configuration.generalconfig;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.distribution.ReleaseBundlesConfig;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.replication.GlobalReplicationsConfigDescriptor;
import org.artifactory.descriptor.signature.SignedUrlConfig;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.rest.common.model.BaseModel;
import org.jfrog.common.config.validation.NotUnicode;

/**
 * @author Yoaz Menda
 */
public class ArtifactoryConfig extends BaseModel {
    private Integer fileUploadMaxSize;
    private Integer itemsBrowserPagingSize;
    private Boolean globalOfflineMode;
    private Boolean showAddonSettings;

    //Folder download
    private Boolean folderDownloadEnabled;
    private Boolean folderDownloadEnabledForAnonymous;
    private Integer folderDownloadMaxSizeMb;
    private Long maxFolderDownloadFilesLimit;
    private Integer FolderDownloadMaxConcurrentRequests;
    //Trashcan
    private Boolean trashcanEnabled;
    private Boolean allowPermDeletes;
    private Integer trashcanRetentionPeriodDays;
    //Global replication config
    private Boolean blockPullReplications;
    private Boolean blockPushReplications;
    //Sumo Logic
    private Boolean sumoLogicEnabled;
    @NotUnicode
    private String sumoLogicCollectorUrl;
    //Release bundle settings
    private Long releaseBundlesCleanup;
    //Signed URL settings
    private Long signedUrlMaxValidForSecs;
    // Cloud storage download redirect
    // Used only for GET config to signal the UI whether to display the download redirect
    private Boolean hasCloudProviderSupportingRedirection;

    public ArtifactoryConfig() {
    }

    public ArtifactoryConfig(CentralConfigDescriptor mutableDescriptor) {
        fileUploadMaxSize = mutableDescriptor.getFileUploadMaxSizeMb();
        itemsBrowserPagingSize = mutableDescriptor.getItemsBrowserPagingSize();
        globalOfflineMode = mutableDescriptor.isOfflineMode();
        showAddonSettings = mutableDescriptor.getAddons().isShowAddonsInfo();
        //Folder Download
        FolderDownloadConfigDescriptor folderDownloadDescriptor = mutableDescriptor.getFolderDownloadConfig();
        folderDownloadEnabled = folderDownloadDescriptor.isEnabled();
        folderDownloadEnabledForAnonymous = folderDownloadDescriptor.isEnabledForAnonymous();
        folderDownloadMaxSizeMb = folderDownloadDescriptor.getMaxDownloadSizeMb();
        maxFolderDownloadFilesLimit = folderDownloadDescriptor.getMaxFiles();
        FolderDownloadMaxConcurrentRequests = folderDownloadDescriptor.getMaxConcurrentRequests();
        //Trashcan
        TrashcanConfigDescriptor trashcanConfigDescriptor = mutableDescriptor.getTrashcanConfig();
        trashcanEnabled = trashcanConfigDescriptor.isEnabled();
        trashcanRetentionPeriodDays = trashcanConfigDescriptor.getRetentionPeriodDays();
        allowPermDeletes = trashcanConfigDescriptor.isAllowPermDeletes();
        //Global replication config
        GlobalReplicationsConfigDescriptor replicationsConfig = mutableDescriptor.getReplicationsConfig();
        blockPullReplications = replicationsConfig.isBlockPullReplications();
        blockPushReplications = replicationsConfig.isBlockPushReplications();
        // Sumo Logic config
        SumoLogicConfigDescriptor sumoLogicConfig = mutableDescriptor.getSumoLogicConfig();
        sumoLogicEnabled = sumoLogicConfig.isEnabled();
        sumoLogicCollectorUrl = sumoLogicConfig.getCollectorUrl();
        //Release bundles config
        ReleaseBundlesConfig releaseBundlesConfig = mutableDescriptor.getReleaseBundlesConfig();
        releaseBundlesCleanup = releaseBundlesConfig.getIncompleteCleanupPeriodHours();
        // Signed URL config
        SignedUrlConfig signedUrlConfig = mutableDescriptor.getSignedUrlConfig();
        signedUrlMaxValidForSecs = signedUrlConfig.getMaxValidForSeconds();
        hasCloudProviderSupportingRedirection = isCloudProviderSupportingRedirectionConfigured();
    }

    private Boolean isCloudProviderSupportingRedirectionConfigured() {
        return ContextHelper.get()
                .beanForType(AddonsManager.class)
                .isCloudProviderSupportingRedirectionConfigured() ? true : null;
    }

    public Integer getFileUploadMaxSize() {
        return fileUploadMaxSize;
    }

    public Integer getItemsBrowserPagingSize() {
        return itemsBrowserPagingSize;
    }

    public void setItemsBrowserPagingSize(Integer itemsBrowserPagingSize) {
        this.itemsBrowserPagingSize = itemsBrowserPagingSize;
    }

    public void setFileUploadMaxSize(Integer fileUploadMaxSize) {
        this.fileUploadMaxSize = fileUploadMaxSize;
    }

    public Boolean isGlobalOfflineMode() {
        return globalOfflineMode;
    }

    public void setGlobalOfflineMode(Boolean globalOfflineMode) {
        this.globalOfflineMode = globalOfflineMode;
    }

    public Boolean isShowAddonSettings() {
        return showAddonSettings;
    }

    public void setShowAddonSettings(Boolean showAddonSettings) {
        this.showAddonSettings = showAddonSettings;
    }

    public Boolean isFolderDownloadEnabled() {
        return folderDownloadEnabled;
    }

    public void setFolderDownloadEnabled(boolean folderDownloadEnabled) {
        this.folderDownloadEnabled = folderDownloadEnabled;
    }

    public Boolean isFolderDownloadEnabledForAnonymous() {
        return folderDownloadEnabledForAnonymous;
    }

    public void setFolderDownloadEnabledForAnonymous(boolean folderDownloadEnabledForAnonymous) {
        this.folderDownloadEnabledForAnonymous = folderDownloadEnabledForAnonymous;
    }

    public Integer getFolderDownloadMaxSizeMb() {
        return folderDownloadMaxSizeMb;
    }

    public void setFolderDownloadMaxSizeMb(int folderDownloadMaxSizeMb) {
        this.folderDownloadMaxSizeMb = folderDownloadMaxSizeMb;
    }

    public Long getMaxFolderDownloadFilesLimit() {
        return maxFolderDownloadFilesLimit;
    }

    public void setMaxFolderDownloadFilesLimit(long maxFolderDownloadFilesLimit) {
        this.maxFolderDownloadFilesLimit = maxFolderDownloadFilesLimit;
    }

    public Integer getFolderDownloadMaxConcurrentRequests() {
        return FolderDownloadMaxConcurrentRequests;
    }

    public void setFolderDownloadMaxConcurrentRequests(int folderDownloadMaxConcurrentRequests) {
        FolderDownloadMaxConcurrentRequests = folderDownloadMaxConcurrentRequests;
    }
    public Boolean getTrashcanEnabled() {
        return trashcanEnabled;
    }

    public void setTrashcanEnabled(Boolean trashcanEnabled) {
        this.trashcanEnabled = trashcanEnabled;
    }

    public Integer getTrashcanRetentionPeriodDays() {
        return trashcanRetentionPeriodDays;
    }

    public void setTrashcanRetentionPeriodDays(Integer trashcanRetentionPeriodDays) {
        this.trashcanRetentionPeriodDays = trashcanRetentionPeriodDays;
    }

    public Boolean getAllowPermDeletes() {
        return allowPermDeletes;
    }

    public void setAllowPermDeletes(Boolean allowPermDeletes) {
        this.allowPermDeletes = allowPermDeletes;
    }

    public Boolean getBlockPullReplications() {
        return blockPullReplications;
    }

    public void setBlockPullReplications(Boolean blockPullReplications) {
        this.blockPullReplications = blockPullReplications;
    }

    public Boolean getBlockPushReplications() {
        return blockPushReplications;
    }

    public void setBlockPushReplications(Boolean blockPushReplications) {
        this.blockPushReplications = blockPushReplications;
    }

    public Boolean getSumoLogicEnabled() {
        return sumoLogicEnabled;
    }

    public void setSumoLogicEnabled(Boolean sumoLogicEnabled) {
        this.sumoLogicEnabled = sumoLogicEnabled;
    }

    public String getSumoLogicCollectorUrl() {
        return sumoLogicCollectorUrl;
    }

    public void setSumoLogicCollectorUrl(String sumoLogicCollectorUrl) {
        this.sumoLogicCollectorUrl = sumoLogicCollectorUrl;
    }

    public Long getReleaseBundlesCleanup() {
        return releaseBundlesCleanup;
    }

    public void setReleaseBundlesCleanup(Long releaseBundlesCleanup) {
        this.releaseBundlesCleanup = releaseBundlesCleanup;
    }

    public Long getSignedUrlMaxValidForSecs() {
        return signedUrlMaxValidForSecs;
    }

    public void setSignedUrlMaxValidForSecs(Long signedUrlMaxValidForSecs) {
        this.signedUrlMaxValidForSecs = signedUrlMaxValidForSecs;
    }

    public Boolean getHasCloudProviderSupportingRedirection() {
        return hasCloudProviderSupportingRedirection;
    }

    public void setHasCloudProviderSupportingRedirection(Boolean hasCloudProviderSupportingRedirection) {
        this.hasCloudProviderSupportingRedirection = hasCloudProviderSupportingRedirection;
    }

}
