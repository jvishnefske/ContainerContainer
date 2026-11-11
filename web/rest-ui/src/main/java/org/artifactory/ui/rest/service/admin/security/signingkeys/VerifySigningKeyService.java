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

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.common.keys.KeyPairService;
import org.artifactory.api.rest.keys.KeyPairModel;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.security.signingkey.SignKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.artifactory.descriptor.repo.KeyPairDescriptor.DEFAULT_GPG_KEY;

/**
 * This Service stays as backward compatible
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class VerifySigningKeyService implements RestService<SignKey> {
    public static final String SUCCESS_VERIFY_MESSAGE = "Successfully verified keys and passphrase";

    AddonsManager addonsManager;
    KeyPairService keyPairService;

    @Autowired
    public VerifySigningKeyService(AddonsManager addonsManager, KeyPairService keyPairService) {
        this.addonsManager = addonsManager;
        this.keyPairService = keyPairService;
    }

    @Override
    public void execute(ArtifactoryRestRequest<SignKey> request, RestResponse response) {
        SignKey signKey = request.getImodel();
        execute(signKey, response);
    }

    protected void execute(SignKey signKey, RestResponse response) {
        boolean keyPairVerified = false;
        if (signKey.getVaultKey() != null) {
            KeyPairModel keyPairModel = SigningKeyUtil.constructKeyPairModelForExternalVault(signKey);
            keyPairVerified = keyPairService.verifyKeyPairModel(keyPairModel);
            if (!keyPairVerified) {
                response.error(
                        "Failed to sign and verify using the installed keys and supplied pass-phrase");
            }
        } else {
            String passPhrase = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), signKey.getPassPhrase());
            // verify signing key phrase
            keyPairVerified = verifySignKeyPhrase(response, passPhrase);
        }

        if (keyPairVerified) response.info(SUCCESS_VERIFY_MESSAGE);
    }

    /**
     * verify signing key phrase
     *
     * @param artifactoryResponse - encapsulate data related to response
     * @param passPhrase          = sign key pass phrase
     */
    private boolean verifySignKeyPhrase(RestResponse artifactoryResponse, String passPhrase) {
        boolean hasRequisites = true;
        KeyPairDescriptor keyPairDescriptor = keyPairService.getKeyPairByName(DEFAULT_GPG_KEY);
        if (isEmpty(passPhrase)) {
            artifactoryResponse.warn("No pass-phrase supplied");
            hasRequisites = false;
        }
        if (StringUtils.isBlank(keyPairDescriptor.getPrivateKey())) {
            artifactoryResponse.error("No private key installed");
            hasRequisites = false;
        }
        if (StringUtils.isBlank(keyPairDescriptor.getPublicKey())) {
            artifactoryResponse.error("No public key installed");
            hasRequisites = false;
        }

        if (hasRequisites) {
            boolean keyPairVerified = keyPairService.verifyKeyPairByName(DEFAULT_GPG_KEY, passPhrase);
            if (!keyPairVerified) {
                artifactoryResponse.error(
                        "Failed to sign and verify using the installed keys and supplied pass-phrase");
            }

            return keyPairVerified;
        }

        return false;
    }
}
