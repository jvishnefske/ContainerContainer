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

package org.artifactory.ui.rest.service.admin.security.keys;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.keys.KeysAddon;
import org.artifactory.api.jackson.JacksonWriter;
import org.artifactory.keys.TrustedKey;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.security.keys.TrustedKeyIModel;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Slf4j
@Component
@AllArgsConstructor
@SuppressWarnings({"unchecked", "rawtypes"})
public class CreateTrustedKeyService implements RestService {

    private AddonsManager addonsManager;

    @Override
    public void execute(@NonNull ArtifactoryRestRequest request, @NonNull RestResponse response) {
        TrustedKeyIModel inputModel = (TrustedKeyIModel) request.getImodel();
        TrustedKey trustedKey = createTrustedKey(inputModel);
        log.debug("Trusted key with id: {} was created successfully", trustedKey.getKid());
        TrustedKeyIModel keyModel = createKeyModel(trustedKey);
        try {
            String serializedModel = JacksonWriter.serialize(keyModel);
            response.iModel(serializedModel);
            response.responseCode(CREATED.value());
        } catch (IOException e) {
            log.error("Error occurred while creating trusted key", e);
            response.error("Error occurred while creating trusted key");
            response.responseCode(INTERNAL_SERVER_ERROR.value());
        }
    }

    @NonNull
    private TrustedKey createTrustedKey(@NonNull TrustedKeyIModel keyIModel) {
        KeysAddon addon = addonsManager.addonByType(KeysAddon.class);
        return addon.createTrustedKey(
                keyIModel.getTrustedKey(),
                keyIModel.getType(),
                keyIModel.getAlias());
    }

    @NonNull
    private TrustedKeyIModel createKeyModel(@NonNull TrustedKey key) {
        return TrustedKeyIModel.builder()
                .kid(key.getKid())
                .type(key.getType())
                .alias(key.getAlias())
                .trustedKey(key.getTrustedKey())
                .issuedBy(key.getIssuedBy())
                .issued(key.getIssued())
                .fingerprint(key.getFingerprint())
                .expiry(key.getExpiry())
                .build();
    }
}
