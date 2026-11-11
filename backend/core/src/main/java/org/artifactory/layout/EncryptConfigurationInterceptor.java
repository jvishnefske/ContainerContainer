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

package org.artifactory.layout;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.ConfigurationChangesInterceptor;
import org.artifactory.descriptor.authentication.AuthenticationToken;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.mail.MailServerDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.XrayDescriptor;
import org.artifactory.descriptor.retention.ArtifactRetentionMasterToken;
import org.artifactory.descriptor.retention.ArtifactRetentionSettings;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.signingkeys.SigningKeysSettings;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.util.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Fred Simon
 */
@Slf4j
@Component
public class EncryptConfigurationInterceptor implements ConfigurationChangesInterceptor {
    public static void decrypt(MutableCentralConfigDescriptor descriptor) {
        encryptOrDecrypt(descriptor, false);
    }

    private static void encryptOrDecrypt(MutableCentralConfigDescriptor descriptor, boolean encrypt) {
        for (RemoteRepoDescriptor remoteRepoDescriptor : descriptor.getRemoteRepositoriesMap()
                .values()) {
            if (remoteRepoDescriptor instanceof HttpRepoDescriptor) {
                HttpRepoDescriptor backwardHttpRepoDescriptor = (HttpRepoDescriptor) remoteRepoDescriptor;
                String newPassword = getNewPassword(encrypt, backwardHttpRepoDescriptor.getPassword());
                if (StringUtils.isNotBlank(newPassword)) {
                    backwardHttpRepoDescriptor.setPassword(newPassword);
                }
            }
        }
        for (LocalReplicationDescriptor localReplicationDescriptor : descriptor.getLocalReplications()) {
            String newPassword = getNewPassword(encrypt, localReplicationDescriptor.getPassword());
            if (StringUtils.isNotBlank(newPassword)) {
                localReplicationDescriptor.setPassword(newPassword);
            }
        }
        SecurityDescriptor security = descriptor.getSecurity();
        SigningKeysSettings signingKeysSettings = security.getSigningKeysSettings();
        if (signingKeysSettings != null) {
            String newPassword = getNewPassword(encrypt, signingKeysSettings.getPassphrase());
            if (StringUtils.isNotBlank(newPassword)) {
                signingKeysSettings.setPassphrase(newPassword);
            }
            newPassword = getNewPassword(encrypt, signingKeysSettings.getKeyStorePassword());
            if (StringUtils.isNotBlank(newPassword)) {
                signingKeysSettings.setKeyStorePassword(newPassword);
            }
        }
        List<ProxyDescriptor> proxies = descriptor.getProxies();
        if (proxies != null) {
            for (ProxyDescriptor proxy : proxies) {
                String newPassword = getNewPassword(encrypt, proxy.getPassword());
                if (StringUtils.isNotBlank(newPassword)) {
                    proxy.setPassword(newPassword);
                }
            }
        }
        List<KeyPairDescriptor> keyPairs = descriptor.getKeyPairs();
        if (keyPairs != null) {
            for (KeyPairDescriptor keyPairDescriptor : keyPairs) {
                String newPassword = getNewPassword(encrypt, keyPairDescriptor.getPrivateKey());
                if (StringUtils.isNotBlank(newPassword)) {
                    keyPairDescriptor.setPrivateKey(newPassword);
                }
                newPassword = getNewPassword(encrypt, keyPairDescriptor.getPassphrase());
                if (StringUtils.isNotBlank(newPassword)) {
                    keyPairDescriptor.setPassphrase(newPassword);
                }
            }
        }
        MailServerDescriptor mailServer = descriptor.getMailServer();
        if (mailServer != null) {
            String newPassword = getNewPassword(encrypt, mailServer.getPassword());
            if (StringUtils.isNotBlank(newPassword)) {
                mailServer.setPassword(newPassword);
            }
        }

        XrayDescriptor xrayDescriptor = descriptor.getXrayConfig();
        if (xrayDescriptor != null) {
            String newpassoword = getNewPassword(encrypt, xrayDescriptor.getPassword());
            if (StringUtils.isNotBlank(newpassoword)) {
                xrayDescriptor.setPassword(newpassoword);
            }
        }

        SumoLogicConfigDescriptor sumoLogicConfig = descriptor.getSumoLogicConfig();
        if (sumoLogicConfig != null) {
            String clientId = getNewPassword(encrypt, sumoLogicConfig.getClientId());
            if (StringUtils.isNotBlank(clientId)) {
                sumoLogicConfig.setClientId(clientId);
            }
            String secret = getNewPassword(encrypt, sumoLogicConfig.getSecret());
            if (StringUtils.isNotBlank(secret)) {
                sumoLogicConfig.setSecret(secret);
            }
        }

        ArtifactRetentionSettings retentionSettings = descriptor.getRetentionSettings();
        if (retentionSettings != null) {
            ArtifactRetentionMasterToken masterToken = retentionSettings.getJfrogColdStorageMasterToken();
            if (masterToken != null && masterToken.getAccessToken() != null) {
                String accessToken = getNewPassword(encrypt, masterToken.getAccessToken());
                if (StringUtils.isNotBlank(accessToken)) {
                    masterToken.setAccessToken(accessToken);
                }
            }
        }

        var authentication = descriptor.getAuthentication();
        if (!CollectionUtils.isNullOrEmpty(authentication.getTokens())) {
            for (AuthenticationToken token : authentication.getTokens()) {
                String accessToken = getNewPassword(encrypt, token.getAccessToken());
                if (StringUtils.isNotBlank(accessToken)) {
                    token.setAccessToken(accessToken);
                }
            }
        }
    }

    public static String getNewPassword(boolean encrypt, String password) {
        if (StringUtils.isNotBlank(password)) {
            if (encrypt) {
                return CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(), password);
            } else {
                return CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), password);
            }
        }
        return null;
    }

    @Override
    public void onBeforeSave(CentralConfigDescriptor newDescriptor) {
        if (newDescriptor instanceof MutableCentralConfigDescriptor &&
                //checking if artifactory key (enc/dec marker) is exist
                CryptoHelper.hasArtifactoryKey(ArtifactoryHome.get())) {
            // Find all sensitive data and encrypt them
            encrypt((MutableCentralConfigDescriptor) newDescriptor);
        }
    }

    private void encrypt(MutableCentralConfigDescriptor descriptor) {
        encryptOrDecrypt(descriptor, true);
    }

}
