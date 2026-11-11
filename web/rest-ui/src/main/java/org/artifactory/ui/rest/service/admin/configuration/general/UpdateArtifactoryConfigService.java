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

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.rest.release.ReleaseBundlesConfigModel;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.replication.GlobalReplicationsConfigDescriptor;
import org.artifactory.descriptor.signature.SignedUrlConfig;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.generalconfig.ArtifactoryConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Yoaz Menda
 */
@Component
public class UpdateArtifactoryConfigService implements RestService {

    private CentralConfigService centralConfigService;
    private AddonsManager addonsManager;

    @Autowired
    public UpdateArtifactoryConfigService(CentralConfigService centralConfigService,
            AddonsManager addonsManager) {
        this.centralConfigService = centralConfigService;
        this.addonsManager = addonsManager;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        ArtifactoryConfig artifactoryConfig = (ArtifactoryConfig) request.getImodel();
        // update general setting and set config descriptor
        updateDescriptorAndSave(artifactoryConfig);
        response.info("Successfully updated Artifactory settings");
    }

    /**
     * update config descriptor with general config setting and save
     *
     * @param artifactoryConfig - general setting sent from client
     */
    void updateDescriptorAndSave(ArtifactoryConfig artifactoryConfig) {
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        mutableDescriptor.setFileUploadMaxSizeMb(artifactoryConfig.getFileUploadMaxSize());
        mutableDescriptor.setItemsBrowserPagingSize(artifactoryConfig.getItemsBrowserPagingSize());
        mutableDescriptor.setOfflineMode(artifactoryConfig.isGlobalOfflineMode());
        mutableDescriptor.getAddons().setShowAddonsInfo(artifactoryConfig.isShowAddonSettings());
        List<Descriptor> descriptors = new ArrayList<>();

        //Folder download config
        descriptors.add(updateFolderDownloadConfig(artifactoryConfig, mutableDescriptor));
        //Trashcan config
        descriptors.add(updateTrashcanConfig(artifactoryConfig, mutableDescriptor));
        //Update global replication config
        descriptors.add(updateGlobalReplicationConfig(artifactoryConfig, mutableDescriptor));
        // Update Sumo Logic config
        descriptors.add(updateSumoLogicConfig(artifactoryConfig, mutableDescriptor));
        // Update Release Bundles config
        updateReleaseBundlesConfig(artifactoryConfig, mutableDescriptor);
        updateSignedUrlConfig(artifactoryConfig, mutableDescriptor);
        centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor, artifactoryConfig, descriptors);
    }

    //Does not override defaults if UI sent empty model.
    private FolderDownloadConfigDescriptor updateFolderDownloadConfig(ArtifactoryConfig artifactoryConfig,
            MutableCentralConfigDescriptor descriptor) {
        FolderDownloadConfigDescriptor folderDownloadConfig = descriptor.getFolderDownloadConfig();
        folderDownloadConfig.setEnabled(
                Optional.ofNullable(artifactoryConfig.isFolderDownloadEnabled())
                        .orElse(folderDownloadConfig.isEnabled()));
        folderDownloadConfig.setEnabledForAnonymous(
                Optional.ofNullable(artifactoryConfig.isFolderDownloadEnabledForAnonymous())
                        .orElse(folderDownloadConfig.isEnabledForAnonymous()));
        folderDownloadConfig.setMaxConcurrentRequests(Optional.ofNullable(
                artifactoryConfig.getFolderDownloadMaxConcurrentRequests())
                .orElse(folderDownloadConfig.getMaxConcurrentRequests()));
        folderDownloadConfig.setMaxDownloadSizeMb(Optional.ofNullable(
                artifactoryConfig.getFolderDownloadMaxSizeMb()).orElse(folderDownloadConfig.getMaxDownloadSizeMb()));
        folderDownloadConfig.setMaxFiles(Optional.ofNullable(
                artifactoryConfig.getMaxFolderDownloadFilesLimit()).orElse(folderDownloadConfig.getMaxFiles()));
        descriptor.setFolderDownloadConfig(folderDownloadConfig);
        return folderDownloadConfig;
    }

    //Does not override defaults if UI sent empty model.
    private TrashcanConfigDescriptor updateTrashcanConfig(ArtifactoryConfig artifactoryConfig,
            MutableCentralConfigDescriptor descriptor) {
        TrashcanConfigDescriptor trashcanConfig = descriptor.getTrashcanConfig();
        trashcanConfig.setEnabled(Optional.ofNullable(
                artifactoryConfig.getTrashcanEnabled()).orElse(trashcanConfig.isEnabled()));
        trashcanConfig.setAllowPermDeletes(Optional.ofNullable(
                artifactoryConfig.getAllowPermDeletes()).orElse(trashcanConfig.isAllowPermDeletes()));
        trashcanConfig.setRetentionPeriodDays(Optional.ofNullable(
                artifactoryConfig.getTrashcanRetentionPeriodDays()).orElse(trashcanConfig.getRetentionPeriodDays()));
        descriptor.setTrashcanConfig(trashcanConfig);
        return trashcanConfig;
    }

    //Does not override defaults if UI sent empty model.
    private GlobalReplicationsConfigDescriptor updateGlobalReplicationConfig(ArtifactoryConfig artifactoryConfig,
            MutableCentralConfigDescriptor descriptor) {
        GlobalReplicationsConfigDescriptor replicationsConfig = descriptor.getReplicationsConfig();
        replicationsConfig.setBlockPullReplications(Optional.ofNullable(
                artifactoryConfig.getBlockPullReplications()).orElse(replicationsConfig.isBlockPullReplications()));
        replicationsConfig.setBlockPushReplications(Optional.ofNullable(
                artifactoryConfig.getBlockPushReplications()).orElse(replicationsConfig.isBlockPushReplications()));
        return replicationsConfig;
    }

    //Does not override defaults if UI sent empty model.
    private SumoLogicConfigDescriptor updateSumoLogicConfig(ArtifactoryConfig artifactoryConfig,
            MutableCentralConfigDescriptor descriptor) {
        SumoLogicConfigDescriptor sumoLogicConfig = descriptor.getSumoLogicConfig();
        sumoLogicConfig.setEnabled(
                Optional.ofNullable(artifactoryConfig.getSumoLogicEnabled()).orElse(sumoLogicConfig.isEnabled()));
        sumoLogicConfig.setCollectorUrl(Optional.ofNullable(
                artifactoryConfig.getSumoLogicCollectorUrl()).orElse(sumoLogicConfig.getCollectorUrl()));
        return sumoLogicConfig;
    }

    //Does not override defaults if UI sent empty model.
    private void updateReleaseBundlesConfig(ArtifactoryConfig artifactoryConfig,
            MutableCentralConfigDescriptor descriptor) {
        ReleaseBundlesConfigModel model = new ReleaseBundlesConfigModel(
                Optional.ofNullable(artifactoryConfig.getReleaseBundlesCleanup())
                        .orElse(descriptor.getReleaseBundlesConfig().getIncompleteCleanupPeriodHours()));
        addonsManager.addonByType(ReleaseBundleAddon.class).setReleaseBundlesConfig(model);
    }

    //Does not override defaults if UI sent empty model.
    private void updateSignedUrlConfig(ArtifactoryConfig artifactoryConfig, MutableCentralConfigDescriptor descriptor) {
        SignedUrlConfig signedUrlConfig = descriptor.getSignedUrlConfig();
        signedUrlConfig.setMaxValidForSeconds(Optional.ofNullable(artifactoryConfig.getSignedUrlMaxValidForSecs())
                .orElse(signedUrlConfig.getMaxValidForSeconds()));
    }

}
