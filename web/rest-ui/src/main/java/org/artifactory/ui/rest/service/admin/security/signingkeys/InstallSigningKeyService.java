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
import org.apache.commons.io.FileUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.common.keys.KeyPairService;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.keys.KeyPairModel;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.file.ArtifactoryValidatedFile;
import org.artifactory.keys.KeyPairsUtil;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.security.signingkey.SignKey;
import org.artifactory.ui.rest.model.utils.FileUpload;
import org.artifactory.ui.utils.MultiPartUtils;
import org.artifactory.util.Files;
import org.artifactory.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static org.artifactory.descriptor.repo.KeyPairDescriptor.DEFAULT_GPG_KEY;
import static org.artifactory.descriptor.repo.KeyPairDescriptor.GPG_TYPE;

/**
 * This Service stays as backward compatible
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class InstallSigningKeyService implements RestService<FileUpload> {
    public static final String SUCCESS_INSTALL_PUBLIC_KEY_MESSAGE = "Public key is installed";
    public static final String SUCCESS_INSTALL_PRIVATE_KEY_MESSAGE = "Private key is installed";

    private static final Logger log = LoggerFactory.getLogger(InstallSigningKeyService.class);

    @Autowired
    AddonsManager addonsManager;
    @Autowired
    CentralConfigService centralConfigService;
    @Autowired
    KeyPairService keyPairService;

    @Override
    public void execute(ArtifactoryRestRequest<FileUpload> request, RestResponse response) {
        boolean isPublic = Boolean.parseBoolean(request.getQueryParamByKey("public"));
        List<String> fileNames = new ArrayList<>();
        // save key file to temp folder
        saveKeyFileToTempFolder(request, fileNames, response);
        // install signing key
        installSigningKey(response, isPublic, fileNames, request.getServletRequest());
    }

    /**
     * install signing key
     *
     * @param artifactoryResponse - encapsulate data related to response
     * @param isPublic            - if true public key
     * @param fileNames           - files name
     */
    private void installSigningKey(RestResponse<SignKey> artifactoryResponse, boolean isPublic, List<String> fileNames,
            HttpServletRequest request) {
        String uploadDir = ContextHelper.get().getArtifactoryHome().getTempUploadDir().getAbsolutePath();
        ArtifactoryValidatedFile file = new ArtifactoryValidatedFile(uploadDir, fileNames.get(0));
        try {
            String key = FileUtils.readFileToString(file, Charset.defaultCharset());
            onInstallKey(key, isPublic);
            String publicKeyDownloadTarget = getKeyLink(request);
            SignKey signKey = new SignKey();
            signKey.setPublicKeyLink(publicKeyDownloadTarget);
            artifactoryResponse.iModel(signKey);
            if (isPublic) {
                artifactoryResponse.info(SUCCESS_INSTALL_PUBLIC_KEY_MESSAGE);
            } else {
                artifactoryResponse.info(SUCCESS_INSTALL_PRIVATE_KEY_MESSAGE);
            }
            // delete temp key
            Files.removeFile(file);
        } catch (Exception e) {
            log.error(e.toString());
            artifactoryResponse.error(e.toString());
        }
    }

    /**
     * save key file to temp folder
     *
     * @param artifactoryRequest - encapsulate data related to request
     * @param fileNames          - file names
     */
    private void saveKeyFileToTempFolder(ArtifactoryRestRequest<FileUpload> artifactoryRequest, List<String> fileNames,
            RestResponse response) {
        FileUpload uploadFile = artifactoryRequest.getImodel();
        String uploadDir = ContextHelper.get().getArtifactoryHome().getTempUploadDir().getAbsolutePath();
        MultiPartUtils.createTempFolderIfNotExist(uploadDir);
        // save file to temp folder
        try {
            MultiPartUtils.saveFileDataToTemp(centralConfigService, uploadFile.getFormDataMultiPart(), uploadDir,
                    fileNames, false);
        } catch (Exception e) {
            response.error(e.getMessage());
        }
    }

    private String getKeyLink(HttpServletRequest request) {
        return Joiner.on('/').join(HttpUtils.getServletContextUrl(request),
                "api", "gpg", "key/public");
    }

    private void onInstallKey(String key, boolean isPublic) {
        KeyPairDescriptor keyPairDescriptor =
                keyPairService.getKeyPairByName(DEFAULT_GPG_KEY);
        KeyPairModel keyPairModel;
        if (keyPairDescriptor == null) {
            keyPairModel = KeyPairModel.builder().alias(DEFAULT_GPG_KEY)
                    .pairType(GPG_TYPE).pairName(DEFAULT_GPG_KEY)
                    .build();
            if (isPublic) {
                keyPairModel.setPublicKey(key);
            } else {
                keyPairModel.setPrivateKey(key);
            }
            keyPairService.createKeyPair(keyPairModel);
        } else {
            keyPairModel = KeyPairsUtil.toKeyPairModel(keyPairDescriptor);
            if (isPublic) {
                keyPairModel.setPublicKey(key);
            } else {
                keyPairModel.setPrivateKey(key);
            }
            keyPairService.updateKeyPair(keyPairModel);
        }
    }
}
