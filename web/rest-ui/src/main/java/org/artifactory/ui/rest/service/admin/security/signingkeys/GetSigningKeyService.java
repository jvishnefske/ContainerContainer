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

import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.common.keys.KeyPairService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.security.signingkey.SignKey;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

import static org.artifactory.descriptor.repo.KeyPairDescriptor.DEFAULT_GPG_KEY;

/**
 * This Service stays as backward compatible
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetSigningKeyService implements RestService<Void> {

    @Autowired
    AddonsManager addonsManager;
    @Autowired
    CentralConfigService centralConfigService;
    @Autowired
    KeyPairService keyPairService;

    @Override
    public void execute(ArtifactoryRestRequest<Void> request, RestResponse response) {
        SignKey signKey = new SignKey();
        KeyPairDescriptor keyPairDescriptor =
                keyPairService.getKeyPairByName(DEFAULT_GPG_KEY);
        boolean publicKeyInstalled = false;
        if (keyPairDescriptor != null) {
            signKey.setPrivateKeyInstalled(StringUtils.isNotBlank(keyPairDescriptor.getPrivateKey()));
            signKey.setPassPhrase(CryptoHelper.encryptIfNeeded(ArtifactoryHome.get(),
                    keyPairDescriptor.getPassphrase()));
            publicKeyInstalled = StringUtils.isNotBlank(keyPairDescriptor.getPublicKey());
        }
        if (publicKeyInstalled) {
            String link = getKeyLink(request.getServletRequest());
            signKey.setPublicKeyInstalled(true);
            signKey.setPublicKeyLink(link);
        }
        response.iModel(signKey);
    }

    private String getKeyLink(HttpServletRequest request) {
        return Joiner.on('/').join(HttpUtils.getServletContextUrl(request),
                "api", "gpg", "key/public");
    }

}
