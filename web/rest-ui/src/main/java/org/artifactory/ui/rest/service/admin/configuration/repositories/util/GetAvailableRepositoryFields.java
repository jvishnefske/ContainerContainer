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

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepositoryFieldsValuesModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.listOfReposAllowedSendRedirectUrl;
import static org.artifactory.common.ConstantValues.listOfReposForcedCdnRedirectUrl;

/**
 * @author Aviad Shikloshi
 */
@Component
public class GetAvailableRepositoryFields implements RestService<RepositoryFieldsValuesModel> {

    private CentralConfigService centralConfigService;
    private ArtifactoryBinaryService binaryService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        List<String> allowedRedirects = getCdnRepositories();
        List<String> forcedCdnRedirects = getForcedCdnRepositories();
        RepositoryFieldsValuesModel fields = new RepositoryFieldsValuesModel(centralConfigService.getDescriptor(),
                binaryService,
                allowedRedirects,
                forcedCdnRedirects);
        response.iModel(fields);
    }

    @Nonnull
    List<String> getCdnRepositories() {
        return Arrays.stream(listOfReposAllowedSendRedirectUrl.getString().split(","))
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    @Nonnull
    List<String> getForcedCdnRepositories() {
        return Arrays.stream(listOfReposForcedCdnRedirectUrl.getString().split(","))
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    @Autowired
    public void setCentralConfigService(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
    }

    @Autowired
    public void setBinaryService(ArtifactoryBinaryService binaryService) {
        this.binaryService = binaryService;
    }

}
