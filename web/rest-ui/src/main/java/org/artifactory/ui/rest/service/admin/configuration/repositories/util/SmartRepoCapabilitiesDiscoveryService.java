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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.api.jackson.JacksonFactory;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.request.UrlVerifier;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ResearchServiceImpl;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.util.SmartRepoUrlUtils;
import org.artifactory.util.StreamUtils;
import org.artifactory.util.UrlUtils;
import org.jfrog.client.util.PathUtils;
import org.jfrog.security.crypto.exception.CryptoRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;

/**
 * Checks whether remoteRepo is an Artifactory instance and if
 * so, retrieves its capabilities for the given version
 *
 * @author Michael Pasternak
 */
@Slf4j
@Component
public class SmartRepoCapabilitiesDiscoveryService<T extends RemoteRepositoryConfigModel> implements RestService<T> {

    private final ResearchServiceImpl researchService;
    private final UrlVerifier urlVerifier;
    private final PackageSpecification packageSpecification;

    @Autowired
    public SmartRepoCapabilitiesDiscoveryService(ResearchServiceImpl researchService, UrlVerifier urlVerifier,
            PackageSpecification packageSpecification) {
        this.researchService = researchService;
        this.urlVerifier = urlVerifier;
        this.packageSpecification = packageSpecification;
    }

    @Override
    public void execute(ArtifactoryRestRequest<T> artifactoryRequest, RestResponse artifactoryResponse) {
        RemoteRepositoryConfigModel repositoryModel = artifactoryRequest.getImodel();
        RemoteAdvancedRepositoryConfigModel remoteRepoAdvancedModel = repositoryModel.getAdvanced();
        RemoteBasicRepositoryConfigModel basicModel = repositoryModel.getBasic();

        if (validationFailed(artifactoryResponse, remoteRepoAdvancedModel, basicModel)) {
            return;
        }

        RepoType repoType = repositoryModel.getTypeSpecific().getRepoType();
        String remoteRepoUrl = PathUtils.addTrailingSlash(basicModel.getUrl());
        CloseableHttpClient client = null;
        try {
            client = getRemoteRepoHttpClient(remoteRepoUrl, repositoryModel);
            ResearchServiceImpl.ResearchResponse researchResponse =
                    handleResearchRequest(remoteRepoUrl, repoType, client);
            artifactoryResponse
                    .iModel(JacksonFactory.createObjectMapper().writeValueAsString(researchResponse))
                    .responseCode(HttpStatus.SC_OK);
        } catch (IOException e) {
            log.error("Cannot serialize SmartRepoCapabilities", e);
            artifactoryResponse.responseCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } catch (CryptoRuntimeException e) {
            log.warn("Failed to decrypt the remote repository password {}", e.getMessage());
            artifactoryResponse.responseCode(SC_BAD_REQUEST).error("The password could not be decrypted");
        } finally {
            StreamUtils.close(client);
        }
    }

    @VisibleForTesting
    ResearchServiceImpl.ResearchResponse handleResearchRequest(String remoteRepoUrl, RepoType repoType,
            CloseableHttpClient client)
            throws IOException {
        ResearchServiceImpl.ResearchResponse researchResponse =
                researchService.getArtifactoryCapabilities(remoteRepoUrl, client).getResearchResponse();
        String fixedUrl = getFixedRemoteRepoUrlForSmart(remoteRepoUrl, repoType);
        researchResponse.setFixedUrl(fixedUrl);
        return researchResponse;
    }

    @VisibleForTesting
    CloseableHttpClient getRemoteRepoHttpClient(String remoteRepoUrl,
            RemoteRepositoryConfigModel repositoryModel) {
        return RemoteRepositoryProvider.getRemoteRepoHttpClient(remoteRepoUrl, repositoryModel, true);
    }

    private boolean validationFailed(RestResponse<String> artifactoryResponse,
            RemoteAdvancedRepositoryConfigModel remoteRepoAdvancedModel, RemoteBasicRepositoryConfigModel basicModel) {
        if (remoteRepoAdvancedModel == null) {
            artifactoryResponse.error("Network details was not sent.")
                    .responseCode(HttpStatus.SC_BAD_REQUEST);
            return true;
        }
        if (basicModel == null || StringUtils.isEmpty(basicModel.getUrl())) {
            artifactoryResponse.error("Remote Url was not sent.")
                    .responseCode(HttpStatus.SC_BAD_REQUEST);
            return true;
        }
        String remoteRepoUrl = PathUtils.addTrailingSlash(basicModel.getUrl());
        if (urlVerifier.isRemoteRepoBlocked(remoteRepoUrl, "test")) {
            artifactoryResponse.error("Connection failed: URL is blocked")
                    .responseCode(SC_BAD_REQUEST);
            return true;
        }
        return false;
    }

    /**
     * Get fixed remote repo url for smart if applicable. Otherwise, return null
     * @param remoteRepoUrl original remote repo url
     * @param repoType package type
     * @return fixed remote repo url for smart if applicable, null otherwise or if feature is disabled
     */

    @Nullable
    private String getFixedRemoteRepoUrlForSmart(String remoteRepoUrl, RepoType repoType) {
        if (!ConstantValues.autoCorrectSmartRepoUrl.getBoolean()) {
            return null;
        }
        String prefixForSmartUrl = packageSpecification.getPrefixForSmartUrl(repoType);
        String smartRepoUrl = SmartRepoUrlUtils.buildSmartRepoUrl(remoteRepoUrl, prefixForSmartUrl);
        return !UrlUtils.softEquals(smartRepoUrl, remoteRepoUrl) ? smartRepoUrl : null;
    }
}