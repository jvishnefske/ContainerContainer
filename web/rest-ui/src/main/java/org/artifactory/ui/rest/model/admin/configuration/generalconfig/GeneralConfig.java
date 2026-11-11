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

import lombok.Getter;
import lombok.Setter;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.subscription.Subscription;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.distribution.ReleaseBundlesConfig;
import org.artifactory.descriptor.download.DownloadRedirectConfigDescriptor;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.replication.GlobalReplicationsConfigDescriptor;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.rlm.RlmConfig;
import org.artifactory.descriptor.signature.SignedUrlConfig;
import org.artifactory.descriptor.subscription.SubscriptionConfig;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.platform.PlatformGeneralConfig;
import org.artifactory.platform.PlatformSystemMessageConfig;
import org.jfrog.common.config.validation.NotUnicode;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Chen Keinan
 */
public class GeneralConfig extends BasicGeneralConfig {

    @NotUnicode
    private String serverName;
    @NotUnicode
    private String customPlatformUrlBase;
    private Integer fileUploadMaxSize;
    private Integer itemsBrowserPagingSize;
    @NotUnicode
    private String dateFormat;
    private Boolean globalOfflineMode;
    private Boolean showAddonSettings;
    @NotUnicode
    private String logoUrl;
    private Boolean helpLinksEnabled;
    private Boolean forceBaseUrl;

    //System message
    private Boolean systemMessageEnabled;
    @NotUnicode
    private String systemMessageTitle;
    @NotUnicode
    private String systemMessageTitleColor;
    @NotUnicode
    private String systemMessage;
    //Folder download
    private Boolean folderDownloadEnabled;
    private Boolean folderDownloadEnabledForAnonymous;
    private Integer folderDownloadMaxSizeMb;
    private Long maxFolderDownloadFilesLimit;
    private Integer FolderDownloadMaxConcurrentRequests;
    private Boolean folderDownloadEnabledEmptyDirectories;
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
    // RLM settings
    @Getter
    @Setter
    private Boolean rlmBuildAutoPromotionEnabled;
    //Signed URL settings
    private Long signedUrlMaxValidForSecs;
    // Cloud storage download redirect
    private Integer downloadRedirectFileMinimumSize;
    private Subscription subscription;
    @Getter
    @Setter
    private RepositoriesGeneralConfigDescriptor repositoriesConfig;
    @NotUnicode
    private String federatedRepoUrlBase;

    public GeneralConfig() {
    }

    public GeneralConfig(CentralConfigDescriptor mutableDescriptor, Integer listeningPort) {
        super(mutableDescriptor);
        serverName = mutableDescriptor.getServerName();
        customPlatformUrlBase = mutableDescriptor.getJFrogUrlBase(listeningPort);
        fileUploadMaxSize = mutableDescriptor.getFileUploadMaxSizeMb();
        globalOfflineMode = mutableDescriptor.isOfflineMode();
        showAddonSettings = mutableDescriptor.getAddons().isShowAddonsInfo();
        itemsBrowserPagingSize = mutableDescriptor.getItemsBrowserPagingSize();

        FolderDownloadConfigDescriptor folderDownloadDescriptor = mutableDescriptor.getFolderDownloadConfig();
        folderDownloadEnabled = folderDownloadDescriptor.isEnabled();
        folderDownloadEnabledForAnonymous = folderDownloadDescriptor.isEnabledForAnonymous();
        folderDownloadMaxSizeMb = folderDownloadDescriptor.getMaxDownloadSizeMb();
        maxFolderDownloadFilesLimit = folderDownloadDescriptor.getMaxFiles();
        FolderDownloadMaxConcurrentRequests = folderDownloadDescriptor.getMaxConcurrentRequests();
        folderDownloadEnabledEmptyDirectories = folderDownloadDescriptor.isEnabledEmptyDirectories();

        TrashcanConfigDescriptor trashcanConfigDescriptor = mutableDescriptor.getTrashcanConfig();
        trashcanEnabled = trashcanConfigDescriptor.isEnabled();
        trashcanRetentionPeriodDays = trashcanConfigDescriptor.getRetentionPeriodDays();
        allowPermDeletes = trashcanConfigDescriptor.isAllowPermDeletes();

        GlobalReplicationsConfigDescriptor replicationsConfig = mutableDescriptor.getReplicationsConfig();
        blockPullReplications = replicationsConfig.isBlockPullReplications();
        blockPushReplications = replicationsConfig.isBlockPushReplications();

        SumoLogicConfigDescriptor sumoLogicConfig = mutableDescriptor.getSumoLogicConfig();
        sumoLogicEnabled = sumoLogicConfig.isEnabled();
        sumoLogicCollectorUrl = sumoLogicConfig.getCollectorUrl();

        ReleaseBundlesConfig releaseBundlesConfig = mutableDescriptor.getReleaseBundlesConfig();
        releaseBundlesCleanup = releaseBundlesConfig.getIncompleteCleanupPeriodHours();

        RlmConfig rlmConfig = mutableDescriptor.getRlmConfig();
        rlmBuildAutoPromotionEnabled = rlmConfig.getRlmBuildAutoPromotionEnabled();

        SignedUrlConfig signedUrlConfig = mutableDescriptor.getSignedUrlConfig();
        signedUrlMaxValidForSecs = signedUrlConfig.getMaxValidForSeconds();

        DownloadRedirectConfigDescriptor downloadRedirectConfig = mutableDescriptor.getDownloadRedirectConfig();
        downloadRedirectFileMinimumSize = downloadRedirectConfig.getFileMinimumSize();

        SubscriptionConfig subscriptionConfig = mutableDescriptor.getSubscriptionConfig();
        Set<String> emails = subscriptionConfig != null
                && subscriptionConfig.getEmails() != null ? subscriptionConfig.getEmails()
                : new HashSet<>();
        subscription = new Subscription(emails);

        repositoriesConfig = mutableDescriptor.getRepositoriesGeneralConfig();

        federatedRepoUrlBase = mutableDescriptor.getFederatedRepoUrlBase();

        PlatformGeneralConfig platformGeneralConfig = getPlatformConfigService().getPlatformGeneralConfig();
        dateFormat = platformGeneralConfig.getDateFormat();
        helpLinksEnabled = platformGeneralConfig.isHelpLinksEnabled();
        logoUrl = platformGeneralConfig.getLogo();

        PlatformSystemMessageConfig platformSystemMessageConfig =
                getPlatformConfigService().getPlatformSystemMessageConfig();
        systemMessageEnabled = platformSystemMessageConfig.isSystemMessageEnabled();
        systemMessageTitle = platformSystemMessageConfig.getSystemMessageTitle();
        systemMessageTitleColor = platformSystemMessageConfig.getSystemMessageTitleColor();
        systemMessage = platformSystemMessageConfig.getSystemMessage();
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getCustomUrlBase() {
        return customUrlBase;
    }

    public void setCustomUrlBase(String customUrlBase) {
        this.customUrlBase = customUrlBase;
    }

    public String getCustomPlatformUrlBase() {
        return customPlatformUrlBase;
    }

    public void setCustomPlatformUrlBase(String customPlatformUrlBase) {
        this.customPlatformUrlBase = customPlatformUrlBase;
    }

    public Integer getFileUploadMaxSize() {
        return fileUploadMaxSize;
    }

    public void setFileUploadMaxSize(Integer fileUploadMaxSize) {
        this.fileUploadMaxSize = fileUploadMaxSize;
    }

    public Integer getItemsBrowserPagingSize() {
        return itemsBrowserPagingSize;
    }

    public void setItemsBrowserPagingSize(Integer itemsBrowserPagingSize) {
        this.itemsBrowserPagingSize = itemsBrowserPagingSize;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
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

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public Boolean isHelpLinksEnabled() {
        return helpLinksEnabled;
    }

    public void setHelpLinksEnabled(Boolean helpLinksEnabled) {
        this.helpLinksEnabled = helpLinksEnabled;
    }

    public boolean isSystemMessageEnabled() {
        return systemMessageEnabled;
    }

    public void setSystemMessageEnabled(boolean systemMessageEnabled) {
        this.systemMessageEnabled = systemMessageEnabled;
    }

    public String getSystemMessageTitle() {
        return systemMessageTitle;
    }

    public void setSystemMessageTitle(String systemMessageTitle) {
        this.systemMessageTitle = systemMessageTitle;
    }

    public String getSystemMessageTitleColor() {
        return systemMessageTitleColor;
    }

    public void setSystemMessageTitleColor(String systemMessageTitleColor) {
        this.systemMessageTitleColor = systemMessageTitleColor;
    }

    public String getSystemMessage() {
        return systemMessage;
    }

    public void setSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
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

    public Boolean isFolderDownloadEnabledEmptyDirectories() {
        return folderDownloadEnabledEmptyDirectories;
    }

    public void setFolderDownloadEnabledEmptyDirectories(boolean folderDownloadEnabledEmptyDirectories) {
        this.folderDownloadEnabledEmptyDirectories = folderDownloadEnabledEmptyDirectories;
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

    public Boolean getForceBaseUrl() {
        return forceBaseUrl;
    }

    public void setForceBaseUrl(Boolean forceBaseUrl) {
        this.forceBaseUrl = forceBaseUrl;
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

    public Integer getDownloadRedirectFileMinimumSize() {
        return downloadRedirectFileMinimumSize;
    }

    public void setDownloadRedirectFileMinimumSize(Integer downloadRedirectFileMinimumSize) {
        this.downloadRedirectFileMinimumSize = downloadRedirectFileMinimumSize;
    }

    public Boolean getHasCloudProviderSupportingRedirection() {
        return hasCloudProviderSupportingRedirection;
    }

    public void setHasCloudProviderSupportingRedirection(Boolean hasCloudProviderSupportingRedirection) {
        this.hasCloudProviderSupportingRedirection = hasCloudProviderSupportingRedirection;
    }

    public String getFederatedRepoUrlBase() {
        return federatedRepoUrlBase;
    }

    public void setFederatedRepoUrlBase(String federatedRepoUrlBase) {
        this.federatedRepoUrlBase = federatedRepoUrlBase;
    }

    private PlatformConfigService getPlatformConfigService() {
        return ContextHelper.get().beanForType(PlatformConfigService.class);
    }
}
