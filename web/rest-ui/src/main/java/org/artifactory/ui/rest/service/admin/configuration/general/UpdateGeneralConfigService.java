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

package org.artifactory.ui.rest.service.admin.configuration.general;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.rest.release.ReleaseBundlesConfigModel;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.download.DownloadRedirectConfigDescriptor;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.replication.GlobalReplicationsConfigDescriptor;
import org.artifactory.descriptor.repo.DebianGeneralConfig;
import org.artifactory.descriptor.repo.DockerConfigResolver;
import org.artifactory.descriptor.repo.DockerGeneralConfig;
import org.artifactory.descriptor.repo.HelmGeneralConfig;
import org.artifactory.descriptor.repo.HuggingFaceMLConfigResolver;
import org.artifactory.descriptor.repo.HuggingFaceMlGeneralConfig;
import org.artifactory.descriptor.repo.NugetGeneralConfig;
import org.artifactory.descriptor.repo.PypiConfigResolver;
import org.artifactory.descriptor.repo.PypiGeneralConfig;
import org.artifactory.descriptor.repo.RpmGeneralConfig;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.rlm.RlmConfig;
import org.artifactory.descriptor.signature.SignedUrlConfig;
import org.artifactory.descriptor.subscription.SubscriptionConfig;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.service.ConfigValidationService;
import org.artifactory.ui.rest.model.admin.configuration.generalconfig.GeneralConfig;
import org.jfrog.common.validator.NameValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.artifactory.common.ConstantValues.uiItemsBrowserPagingMaxLimit;

/**
 * @author Chen Keinan
 */
@Component
@Slf4j
public class UpdateGeneralConfigService implements RestService {

    private final CentralConfigService centralConfigService;
    private final AddonsManager addonsManager;
    private final ConfigValidationService configValidationService;
    private final PlatformConfigService platformConfigService;
    private final DockerConfigResolver dockerConfigResolver;
    private final Pattern logoUrlPattern =
            Pattern.compile("^(http)?s?:?(//[^\"']*\\.(?:jpg|jpeg|gif|png|apng|avif|webp|svg))$");

    @Autowired
    public UpdateGeneralConfigService(CentralConfigService centralConfigService, AddonsManager addonsManager,
            ConfigValidationService configValidationService, PlatformConfigService platformConfigService,
            DockerConfigResolver dockerConfigResolver) {
        this.centralConfigService = centralConfigService;
        this.addonsManager = addonsManager;
        this.configValidationService = configValidationService;
        this.platformConfigService = platformConfigService;
        this.dockerConfigResolver = dockerConfigResolver;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        try {
            GeneralConfig generalConfig = (GeneralConfig) request.getImodel();
            updateDescriptorAndSave(generalConfig);
            response.info("Successfully updated settings");
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            log.error(message);
            log.debug(message, e);

            response.error(message);
            response.responseCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * update config descriptor with general config setting and save
     *
     * @param generalConfig - general setting sent from client
     */
    void updateDescriptorAndSave(GeneralConfig generalConfig) {
        assertGeneralConfigValidValues(generalConfig);
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        mutableDescriptor.setServerName(generalConfig.getServerName());
        mutableDescriptor.setUrlBase(generalConfig.getCustomUrlBase());
        mutableDescriptor.setFederatedRepoUrlBase(generalConfig.getFederatedRepoUrlBase());
        mutableDescriptor.setFileUploadMaxSizeMb(generalConfig.getFileUploadMaxSize());
        mutableDescriptor.setItemsBrowserPagingSize(generalConfig.getItemsBrowserPagingSize());
        mutableDescriptor.setOfflineMode(generalConfig.isGlobalOfflineMode());
        mutableDescriptor.getAddons().setShowAddonsInfo(generalConfig.isShowAddonSettings());
        List<Descriptor> descriptorList = new ArrayList<>();
        //Folder download config
        descriptorList.add(updateFolderDownloadConfig(generalConfig, mutableDescriptor));
        //Trashcan config
        descriptorList.add(updateTrashcanConfig(generalConfig, mutableDescriptor));
        //Update global replication config
        descriptorList.add(updateGlobalReplicationConfig(generalConfig, mutableDescriptor));
        // Update Sumo Logic config
        descriptorList.add(updateSumoLogicConfig(generalConfig, mutableDescriptor));
        // Update Release Bundles config
        ReleaseBundlesConfigModel model = updateReleaseBundlesConfig(generalConfig, mutableDescriptor);
        // Update RLM config
        descriptorList.add(updateRlmConfig(generalConfig, mutableDescriptor));
        //Update Signed Url config
        descriptorList.add(updateSignedUrlConfig(generalConfig, mutableDescriptor));
        // Update Cloud storage download redirect
        descriptorList.add(updateDownloadRedirectConfig(generalConfig, mutableDescriptor));
        // Update repositories general config
        updateRepositoriesGeneralConfig(generalConfig, mutableDescriptor);
        // Update Subscription config
        updateSubscriptionConfig(generalConfig, mutableDescriptor);
        centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor, generalConfig, descriptorList, model);

        platformConfigService.updatePlatformGeneralConfig(generalConfig.getDateFormat(),
                generalConfig.isHelpLinksEnabled(), generalConfig.getLogoUrl());
        platformConfigService.updatePlatformSystemMessageConfig(generalConfig.isSystemMessageEnabled(),
                generalConfig.getSystemMessageTitle(), generalConfig.getSystemMessageTitleColor(),
                generalConfig.getSystemMessage());
    }

    private void updateRepositoriesGeneralConfig(GeneralConfig generalConfig,
            MutableCentralConfigDescriptor mutableDescriptor) {
        if (generalConfig.getRepositoriesConfig() != null) {
            RepositoriesGeneralConfigDescriptor repositoriesGeneralConfig = new RepositoriesGeneralConfigDescriptor();
            HelmGeneralConfig newHelmGeneralConfig = generalConfig.getRepositoriesConfig().getHelm();
            repositoriesGeneralConfig.setHelm(newHelmGeneralConfig);
            NugetGeneralConfig newNugetGeneralConfig = generalConfig.getRepositoriesConfig().getNuget();
            repositoriesGeneralConfig.setNuget(newNugetGeneralConfig);
            RpmGeneralConfig newRpmGeneralConfig = generalConfig.getRepositoriesConfig().getRpm();
            logRpmConfigUpdate(newRpmGeneralConfig, mutableDescriptor.getRepositoriesGeneralConfig().getRpm());
            repositoriesGeneralConfig.setRpm(newRpmGeneralConfig);
            repositoriesGeneralConfig.setDocker(
                    createDockerConfig(generalConfig, mutableDescriptor.getRepositoriesGeneralConfig().getDocker()));
            repositoriesGeneralConfig.setHuggingfaceml(createHuggingFaceConfig(generalConfig,
                    mutableDescriptor.getRepositoriesGeneralConfig().getHuggingfaceml()));
            repositoriesGeneralConfig.setPypi(createPypiConfig(generalConfig,
                    mutableDescriptor.getRepositoriesGeneralConfig().getPypi()));
            DebianGeneralConfig incomingDebian = generalConfig.getRepositoriesConfig().getDebian();
            if (incomingDebian != null) {
                repositoriesGeneralConfig.getDebian().setBlockUnsafeChecksums(
                        incomingDebian.isBlockUnsafeChecksums());
            }

            mutableDescriptor.setRepositoriesGeneralConfig(repositoriesGeneralConfig);
        }
    }

    private void logRpmConfigUpdate(RpmGeneralConfig newConfig, RpmGeneralConfig currentConfig) {
        if (newConfig == null || currentConfig == null) {
            return;
        }
        log.info("Updating current rpm config {} to new config: {}", currentConfig, newConfig);
    }

    private DockerGeneralConfig createDockerConfig(GeneralConfig newRepoConfig,
            DockerGeneralConfig existingDockerConfig) {
        return dockerConfigResolver.mergeFromUiRequest(
                newRepoConfig.getRepositoriesConfig().getDocker(), existingDockerConfig);
    }


    /**
     * returns the resolved HF configuration
     * This prevents marking the config as "UI/API configured" when the user didn't actually touch the HF setting.
     */
    private HuggingFaceMlGeneralConfig createHuggingFaceConfig(GeneralConfig newRepoConfig,
            HuggingFaceMlGeneralConfig existingHFConfig) {
        HuggingFaceMlGeneralConfig newHFConfig = newRepoConfig.getRepositoriesConfig().getHuggingfaceml();
        if (newHFConfig != null) {
            boolean currentValue = HuggingFaceMLConfigResolver.resolveIsXetEnabled(existingHFConfig);
            boolean newValue = newHFConfig.isXetEnabled();
            if (currentValue != newValue) {
                log.info("HF is xet enabled changed from {} to {} - marking as UI/API configured",
                        currentValue, newValue);
                return HuggingFaceMLConfigResolver.createExplicitConfiguredInstance(newValue);
            }
        }
        return existingHFConfig;
    }

    /**
     * returns the resolved PyPI configuration
     * This prevents marking the config as "UI/API configured" when the user didn't actually touch the PyPI setting.
     */
    private PypiGeneralConfig createPypiConfig(GeneralConfig newRepoConfig, PypiGeneralConfig existingPypiConfig) {
        PypiGeneralConfig newPypiConfig = newRepoConfig.getRepositoriesConfig().getPypi();
        if (newPypiConfig != null) {
            boolean currentValue = PypiConfigResolver.resolveIsSimpleJsonFormatEnabled(existingPypiConfig);
            boolean newValue = newPypiConfig.isSimpleJsonFormatEnabled();
            if (currentValue != newValue) {
                log.info("PyPI simpleJsonFormatEnabled changed from {} to {} - marking as UI/API configured",
                        currentValue, newValue);
                return PypiConfigResolver.createExplicitConfiguredInstance(newValue);
            }
        }
        return existingPypiConfig;
    }


    private void assertGeneralConfigValidValues(GeneralConfig generalConfig) {
        assertGeneralConfigServerName(generalConfig.getServerName());
        assertGeneralConfigLogoUrl(generalConfig.getLogoUrl());
        assertGeneralConfigItemsBrowserPagingSize(generalConfig.getItemsBrowserPagingSize());
    }

    private void assertGeneralConfigItemsBrowserPagingSize(Integer itemsBrowserPagingSize) {
        if (itemsBrowserPagingSize == null ||
                itemsBrowserPagingSize <= 0 ||
                itemsBrowserPagingSize > uiItemsBrowserPagingMaxLimit.getInt()) {
            throw new IllegalArgumentException("The number of items per browser page must be in the range: 1 - " +
                    uiItemsBrowserPagingMaxLimit.getInt() + ".");
        }
    }

    private void assertGeneralConfigLogoUrl(String logoUrl) {
        if (!StringUtils.isBlank(logoUrl) &&
                !logoUrlPattern.matcher(logoUrl).matches()) {
            throw new IllegalArgumentException("Logo URL is not valid");
        }
    }

    private void assertGeneralConfigServerName(String serverName) {
        if (!StringUtils.isBlank(serverName)) {
            Optional<String> serverNamePossibleError = NameValidator.validate(serverName);
            if (serverNamePossibleError.isPresent()) {
                throw new IllegalArgumentException(serverNamePossibleError.get());
            }
        }
    }

    private void updateSubscriptionConfig(GeneralConfig generalConfig,
            MutableCentralConfigDescriptor mutableDescriptor) {
        if (generalConfig.getSubscription() != null && generalConfig.getSubscription().getEmails() != null) {
            SubscriptionConfig subscriptionConfig = new SubscriptionConfig();
            subscriptionConfig.setEmails(generalConfig.getSubscription().getEmails());
            // We validate here to avoid complicating the code by returning optionals
            this.configValidationService.validate(subscriptionConfig);
            mutableDescriptor.setSubscriptionConfig(subscriptionConfig);
        }
    }

    //Does not override defaults if UI sent empty model.
    private FolderDownloadConfigDescriptor updateFolderDownloadConfig(GeneralConfig generalConfig,
            MutableCentralConfigDescriptor descriptor) {
        FolderDownloadConfigDescriptor folderDownloadConfig = descriptor.getFolderDownloadConfig();
        folderDownloadConfig.setEnabled(
                Optional.ofNullable(generalConfig.isFolderDownloadEnabled()).orElse(folderDownloadConfig.isEnabled()));
        folderDownloadConfig.setEnabledForAnonymous(
                Optional.ofNullable(generalConfig.isFolderDownloadEnabledForAnonymous())
                        .orElse(folderDownloadConfig.isEnabledForAnonymous()));
        folderDownloadConfig.setMaxConcurrentRequests(Optional.ofNullable(
                        generalConfig.getFolderDownloadMaxConcurrentRequests())
                .orElse(folderDownloadConfig.getMaxConcurrentRequests()));
        folderDownloadConfig.setMaxDownloadSizeMb(Optional.ofNullable(
                generalConfig.getFolderDownloadMaxSizeMb()).orElse(folderDownloadConfig.getMaxDownloadSizeMb()));
        folderDownloadConfig.setMaxFiles(Optional.ofNullable(
                generalConfig.getMaxFolderDownloadFilesLimit()).orElse(folderDownloadConfig.getMaxFiles()));
        folderDownloadConfig.setEnabledEmptyDirectories(
                Optional.ofNullable(generalConfig.isFolderDownloadEnabledEmptyDirectories())
                        .orElse(folderDownloadConfig.isEnabledEmptyDirectories()));
        descriptor.setFolderDownloadConfig(folderDownloadConfig);
        return folderDownloadConfig;
    }

    //Does not override defaults if UI sent empty model.
    private TrashcanConfigDescriptor updateTrashcanConfig(GeneralConfig generalConfig,
            MutableCentralConfigDescriptor descriptor) {
        TrashcanConfigDescriptor trashcanConfig = descriptor.getTrashcanConfig();
        trashcanConfig.setEnabled(Optional.ofNullable(
                generalConfig.getTrashcanEnabled()).orElse(trashcanConfig.isEnabled()));
        trashcanConfig.setAllowPermDeletes(Optional.ofNullable(
                generalConfig.getAllowPermDeletes()).orElse(trashcanConfig.isAllowPermDeletes()));
        trashcanConfig.setRetentionPeriodDays(Optional.ofNullable(
                generalConfig.getTrashcanRetentionPeriodDays()).orElse(trashcanConfig.getRetentionPeriodDays()));
        descriptor.setTrashcanConfig(trashcanConfig);
        return trashcanConfig;
    }

    //Does not override defaults if UI sent empty model.
    private GlobalReplicationsConfigDescriptor updateGlobalReplicationConfig(GeneralConfig generalConfig,
            MutableCentralConfigDescriptor descriptor) {
        GlobalReplicationsConfigDescriptor replicationsConfig = descriptor.getReplicationsConfig();
        replicationsConfig.setBlockPullReplications(Optional.ofNullable(
                generalConfig.getBlockPullReplications()).orElse(replicationsConfig.isBlockPullReplications()));
        replicationsConfig.setBlockPushReplications(Optional.ofNullable(
                generalConfig.getBlockPushReplications()).orElse(replicationsConfig.isBlockPushReplications()));
        return replicationsConfig;

    }

    //Does not override defaults if UI sent empty model.
    private SumoLogicConfigDescriptor updateSumoLogicConfig(GeneralConfig generalConfig,
            MutableCentralConfigDescriptor descriptor) {
        SumoLogicConfigDescriptor sumoLogicConfig = descriptor.getSumoLogicConfig();
        sumoLogicConfig.setEnabled(
                Optional.ofNullable(generalConfig.getSumoLogicEnabled()).orElse(sumoLogicConfig.isEnabled()));
        sumoLogicConfig.setCollectorUrl(Optional.ofNullable(
                generalConfig.getSumoLogicCollectorUrl()).orElse(sumoLogicConfig.getCollectorUrl()));
        return sumoLogicConfig;
    }

    //Does not override defaults if UI sent empty model.
    private ReleaseBundlesConfigModel updateReleaseBundlesConfig(GeneralConfig generalConfig,
            MutableCentralConfigDescriptor descriptor) {
        ReleaseBundlesConfigModel model = new ReleaseBundlesConfigModel(
                Optional.ofNullable(generalConfig.getReleaseBundlesCleanup())
                        .orElse(descriptor.getReleaseBundlesConfig().getIncompleteCleanupPeriodHours()));
        addonsManager.addonByType(ReleaseBundleAddon.class).setReleaseBundlesConfig(model);
        return model;
    }

    private RlmConfig updateRlmConfig(GeneralConfig generalConfig, MutableCentralConfigDescriptor descriptor) {
        RlmConfig rlmConfig = descriptor.getRlmConfig();
        rlmConfig.setRlmBuildAutoPromotionEnabled(Optional.ofNullable(generalConfig.getRlmBuildAutoPromotionEnabled())
                .orElse(rlmConfig.getRlmBuildAutoPromotionEnabled()));
        return rlmConfig;
    }

    //Does not override defaults if UI sent empty model.
    private SignedUrlConfig updateSignedUrlConfig(GeneralConfig generalConfig,
            MutableCentralConfigDescriptor descriptor) {
        SignedUrlConfig signedUrlConfig = descriptor.getSignedUrlConfig();
        signedUrlConfig.setMaxValidForSeconds(Optional.ofNullable(generalConfig.getSignedUrlMaxValidForSecs())
                .orElse(signedUrlConfig.getMaxValidForSeconds()));
        return signedUrlConfig;
    }

    //Does not override defaults if UI sent empty model.
    private DownloadRedirectConfigDescriptor updateDownloadRedirectConfig(GeneralConfig generalConfig,
            MutableCentralConfigDescriptor descriptor) {
        DownloadRedirectConfigDescriptor downloadRedirectConfig = descriptor.getDownloadRedirectConfig();
        downloadRedirectConfig.setFileMinimumSize(Optional.ofNullable(
                        generalConfig.getDownloadRedirectFileMinimumSize())
                .orElse(downloadRedirectConfig.getFileMinimumSize()));
        return downloadRedirectConfig;
    }

}
