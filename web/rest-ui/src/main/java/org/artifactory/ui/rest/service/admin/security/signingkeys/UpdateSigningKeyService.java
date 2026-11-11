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

package org.artifactory.ui.rest.service.admin.security.signingkeys;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.common.keys.KeyPairService;
import org.artifactory.api.rest.keys.KeyPairModel;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.keys.KeyPairsUtil;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.security.signingkey.SignKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.artifactory.descriptor.repo.KeyPairDescriptor.DEFAULT_GPG_KEY;

/**
 * This Service stays as backward compatible
 * @author Shay Yaakov
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class UpdateSigningKeyService implements RestService<SignKey> {
    public static final String SUCCESS_UPDATE_PASS_PHRASE_MESSAGE = "Successfully updated signing pass-phrase";

    @Autowired
    AddonsManager addonsManager;
    @Autowired
    KeyPairService keyPairService;

    @Override
    public void execute(ArtifactoryRestRequest<SignKey> request, RestResponse response) {
        SignKey signKey = request.getImodel();
        updateKeyPair(response, signKey);
    }

    private void updateKeyPair(RestResponse<String> artifactoryResponse, SignKey signKey) {
        KeyPairModel keyPairModel;
        KeyPairDescriptor keyPairDescriptor = keyPairService.getKeyPairByName(DEFAULT_GPG_KEY);

        if (keyPairDescriptor == null) {
            artifactoryResponse.error("Failed to update a non existed key");
            return;
        }

        if (signKey.getVaultKey() != null) {
            keyPairModel = SigningKeyUtil.constructKeyPairModelForExternalVault(signKey);
            keyPairModel.setPairName(keyPairDescriptor.getKey());
            keyPairModel.setPairType(keyPairDescriptor.getKeyType());
            keyPairModel.setAlias(keyPairDescriptor.getAlias());
        } else {
            String passPhrase = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), signKey.getPassPhrase());

            keyPairModel = KeyPairsUtil.toKeyPairModel(keyPairDescriptor);
            keyPairModel.setPassphrase(passPhrase);
        }

        keyPairService.updateKeyPair(keyPairModel);

        artifactoryResponse.info(SUCCESS_UPDATE_PASS_PHRASE_MESSAGE);
    }
}
