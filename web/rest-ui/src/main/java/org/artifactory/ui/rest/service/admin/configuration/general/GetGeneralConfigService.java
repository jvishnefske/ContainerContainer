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

import com.google.common.annotations.VisibleForTesting;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.DockerConfigResolver;
import org.artifactory.descriptor.repo.DockerGeneralConfig;
import org.artifactory.descriptor.repo.HuggingFaceMLConfigResolver;
import org.artifactory.descriptor.repo.HuggingFaceMlGeneralConfig;
import org.artifactory.descriptor.repo.PypiConfigResolver;
import org.artifactory.descriptor.repo.PypiGeneralConfig;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.repo.RpmGeneralConfig;
import org.artifactory.descriptor.repo.RpmGeneralConfigFilter;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.generalconfig.BasicGeneralConfig;
import org.artifactory.ui.rest.model.admin.configuration.generalconfig.GeneralConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Chenk
 */
@Component
public class GetGeneralConfigService implements RestService<GeneralConfig> {

    private CentralConfigService centralConfigService;
    private SecurityService securityService;
    private RepositoryService repositoryService;
    private DockerConfigResolver dockerConfigResolver;

    @Autowired
    public void setCentralConfigService(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }
    
    @Autowired
    public void setDockerConfigResolver(DockerConfigResolver dockerConfigResolver) {
        this.dockerConfigResolver = dockerConfigResolver;
    }

    @Override
    public void execute(ArtifactoryRestRequest<GeneralConfig> request, RestResponse response) {
        CentralConfigDescriptor descriptor = centralConfigService.getDescriptor();
        if (securityService.isAdmin()) {
            GeneralConfig generalconfig = new GeneralConfig(descriptor, centralConfigService.getListeningPort());
            generalconfig.setForceBaseUrl(repositoryService.isForceBaseUrl());
            applyRpmConfigFiltering(generalconfig);
            applyDockerConfigFiltering(generalconfig);
            applyHuggingFaceConfigFiltering(generalconfig);
            applyPypiConfigFiltering(generalconfig);
            response.iModel(generalconfig);
        } else {
            BasicGeneralConfig basicGeneralConfig = new BasicGeneralConfig(descriptor);
            response.iModel(basicGeneralConfig);
        }
    }

    /**
     * Applies filtering to the RpmGeneralConfig based on ConstantValues.
     */
    @VisibleForTesting
    void applyRpmConfigFiltering(GeneralConfig generalConfig) {
        if (generalConfig.getRepositoriesConfig() != null) {
            RpmGeneralConfig originalRpmConfig = generalConfig.getRepositoriesConfig().getRpm();
            RpmGeneralConfig filteredRpmConfig = RpmGeneralConfigFilter.getFilteredRpmConfig(originalRpmConfig);
            RepositoriesGeneralConfigDescriptor repositoriesConfig = generalConfig.getRepositoriesConfig();
            repositoriesConfig.setRpm(filteredRpmConfig);
            generalConfig.setRepositoriesConfig(repositoriesConfig);
        }
    }

    /**
     * Applies filtering to the HuggingFaceConfig using clean business logic.
     * Shows the effective value (UI/API or system property) without affecting configuration state.
     */
    @VisibleForTesting
    void applyDockerConfigFiltering(GeneralConfig generalConfig) {
        if (generalConfig.getRepositoriesConfig() != null) {
            DockerGeneralConfig dockerConfig = generalConfig.getRepositoriesConfig().getDocker();
            if (dockerConfig == null || !dockerConfig.isExplicitConfig()) {
                // Not UI/API-configured - show system property value for display
                DockerGeneralConfig displayConfig = dockerConfigResolver.createDockerConfigBySystemProperty();
                generalConfig.getRepositoriesConfig().setDocker(displayConfig);
            }
        }
    }

    /**
     * Applies filtering to the HuggingFaceConfig using clean business logic.
     * Shows the effective value (UI/API or system property) without affecting configuration state.
     */
    @VisibleForTesting
    void applyHuggingFaceConfigFiltering(GeneralConfig generalConfig) {
        if (generalConfig.getRepositoriesConfig() != null) {
            HuggingFaceMlGeneralConfig hfConfig = generalConfig.getRepositoriesConfig().getHuggingfaceml();
            HuggingFaceMlGeneralConfig displayConfig;
            if (HuggingFaceMLConfigResolver.isExplicitlyConfigured(hfConfig)) {
                // Surface the effective configured value into the xetEnabled field for display
                displayConfig = HuggingFaceMLConfigResolver.createExplicitConfiguredInstance(
                        HuggingFaceMLConfigResolver.resolveIsXetEnabled(hfConfig));
            } else {
                // Not UI/API-configured - show system property value for display
                displayConfig = HuggingFaceMLConfigResolver.createHuggingFaceConfigBySystemProperty();
            }
            generalConfig.getRepositoriesConfig().setHuggingfaceml(displayConfig);
        }
    }

    /**
     * Applies filtering to the PypiGeneralConfig using clean business logic.
     * Shows the effective value (UI/API or system property) without affecting configuration state.
     */
    @VisibleForTesting
    void applyPypiConfigFiltering(GeneralConfig generalConfig) {
        if (generalConfig.getRepositoriesConfig() != null) {
            PypiGeneralConfig pypiConfig = generalConfig.getRepositoriesConfig().getPypi();
            if (pypiConfig == null || !pypiConfig.isSimpleJsonFormatEnabledExplicit()) {
                PypiGeneralConfig displayConfig = PypiConfigResolver.createPypiConfigBySystemProperty();
                generalConfig.getRepositoriesConfig().setPypi(displayConfig);
            }
        }
    }
}
