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

package org.artifactory.security;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.crypto.CryptoService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.layout.EncryptConfigurationInterceptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.storage.db.mbean.NewDbInstallation;
import org.jfrog.config.watch.FileEventType;
import org.jfrog.storage.binstore.ifc.BinaryProviderConfigEncrypter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * Implementation of the master encryption service based on symmetric key and Base58 encoding.
 *
 * We perform encrypt on first Artifactory startup.
 *
 * Certain binary provider properties are encrypted on this entry point.
 *
 * @author Yossi Shaul
 */
@Service
public class ArtifactoryEncryptionServiceImpl
        implements ArtifactoryEncryptionService, ApplicationListener<NewDbInstallation> {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryEncryptionServiceImpl.class);

    private boolean shouldEncryptOnStartup = false;

    private CentralConfigService centralConfigService;

    private AddonsManager addonsManager;

    private RepositoryService repositoryService;

    private BinaryProviderConfigEncrypter binaryProviderConfigEncrypter;

    private CryptoService cryptoService;

    @Autowired
    public ArtifactoryEncryptionServiceImpl(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setCryptoService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Autowired
    public void setCentralConfigService(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
    }

    @Autowired
    public void setBinaryProviderConfigEncrypter(BinaryProviderConfigEncrypter binaryProviderConfigEncrypter) {
        this.binaryProviderConfigEncrypter = binaryProviderConfigEncrypter;
    }

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /**
     * The reason for the encryption to be in contextCreated and not on ReloadableBean.init() is HA.
     * The HA state and propagation service is not yet ready on bean init.
     * (They HA state is ready after onContextReady() method of ArtifactoryStateManagerImpl)
     */
    @Override
    public void onContextCreated() {
        encryptArtifactoryOnFirstStartup();
        encryptBinaryStoreConfig();
    }

    @Override
    public void onApplicationEvent(NewDbInstallation event) {
        shouldEncryptOnStartup = true;
    }

    /**
     * Will encrypt Artifactory in case its first login AND (on HA-primary node or non-HA).
     * Encryption is creating a master key (and propagating to other HA nodes if configured).
     * At this point we don't need to encrypt the config since there are no credentials in it YET.
     * Once we create the master key - any change in the descriptor will trigger EncryptConfigurationInterceptor
     *
     * isFirstLogin is based on USERS tables in DB - in case there is no "last_login_time" record - returns true.
     * We also check that the config descriptor has only one repository, and that is the example repo, and its empty.
     */
    private void encryptArtifactoryOnFirstStartup() {
        if (shouldEncryptOnStartup) {
            log.info("Starting encryption for first Artifactory startup");
            createArtifactoryKeyIfNeeded();
            shouldEncryptOnStartup = false;
        }
    }

    private void encryptBinaryStoreConfig() {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        binaryProviderConfigEncrypter.encryptFile(artifactoryHome.getBinaryStoreXmlFile());
    }

    /**
     * Order is <b>VERY</b> important here, don't mess it up!
     */
    @Override
    public void encrypt() {
        AccessLogger.approved("Encrypting with master encryption key");
        // Create the master key if needed
        // artifactory key created as marker file (not in use for enc/dec)
        createArtifactoryKeyIfNeeded();
        // config interceptor will encrypt the config before it is saved to the database
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor);

        // repo config interceptor will encrypt the repos before they are saved to the database
        repositoryService.bulkUpsertRepoConfigs(
                repositoryService.getRepoConfigsByType(RepoDetailsType.REMOTE), true);
    }

    private void createArtifactoryKeyIfNeeded() {
        ArtifactoryHome home = ArtifactoryHome.get();
        if (!CryptoHelper.hasArtifactoryKey(home)) {
            CryptoHelper.createArtifactoryKeyFile(home);
            notifyArtifactoryKeyCreated();
        }
    }

    /**
     * Order is <b>VERY</b> important here, don't mess it up!
     */
    @Override
    public void decrypt() {
        if (!CryptoHelper.hasArtifactoryKey(ArtifactoryHome.get())) {
            throw new IllegalStateException("Cannot decrypt without Artifactory key file");
        }
        AccessLogger.approved("Decrypting with master encryption key");
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        EncryptConfigurationInterceptor.decrypt(mutableDescriptor);
        File oldKeyFile = CryptoHelper.getArtifactoryKey(ArtifactoryHome.get());
        File renamedKeyFile = CryptoHelper.removeArtifactoryKeyFile(ArtifactoryHome.get());
        centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor);
        decryptRemoteRepoConfigs();
        notifyArtifactoryKeyDeleted(oldKeyFile, renamedKeyFile);
    }

    /**
     * Decrypts the sensitive fields of all remote repo configs on master-key removal and persists them in clear text.
     * Replaces the retired {@code EncryptRepoConfigInterceptor.decrypt} (DC7): the {@code @Sensitive}/
     * {@code @DynamicSensitive} fields ({@code password}, {@code customHttpHeaders}) are now decrypted uniformly via
     * {@link CryptoService}, using the same {@code CryptoHelper} key format, so already-stored values still decrypt.
     * Uses {@code updateRepoConfigSilently} to skip both BEFORE and AFTER interceptors: this is an internal
     * maintenance operation and must not fan out BEFORE/AFTER REPO_UPDATE events for every remote repository.
     */
    private void decryptRemoteRepoConfigs() {
        List<RepoConfig> remoteRepoConfigs = repositoryService.getRepoConfigsByType(RepoDetailsType.REMOTE);
        cryptoService.decrypt(remoteRepoConfigs)
                .forEach(repositoryService::updateRepoConfigSilently);
    }

    @Override
    public void notifyArtifactoryKeyCreated() {
        propagateArtifactoryKeyFileChange(ArtifactoryHome.get().getArtifactoryKey(), FileEventType.CREATE);
        addonsManager.addonByType(HaCommonAddon.class).propagateArtifactoryEncryptionKeyChanged();
    }

    // Order is important, don't touch me!
    @Override
    public void notifyArtifactoryKeyDeleted(File oldKeyFile, File renamedKeyFile) {
        propagateArtifactoryKeyFileChange(oldKeyFile, FileEventType.DELETE);
        propagateArtifactoryKeyFileChange(renamedKeyFile, FileEventType.CREATE);
        addonsManager.addonByType(HaCommonAddon.class).propagateArtifactoryEncryptionKeyChanged();
    }

    private void propagateArtifactoryKeyFileChange(File artifactoryKey, FileEventType eventType) {
        try {
            ContextHelper.get().getConfigurationManager()
                    .forceFileChanged(artifactoryKey, eventType);
        } catch (Exception e) {
            log.error("Failed to propagate artifactory key file change", e);
        }
    }
}