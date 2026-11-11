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

package org.artifactory.ui.rest.service.admin.configuration.repositories;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.metrics.FeatureAdoptionAction;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.NegotiationException;
import org.artifactory.config.ConfigurationException;
import org.artifactory.concurrent.LockingException;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.federation.exceptions.FederatedRemoteMembersUpdateException;
import org.artifactory.metrics.framework.adoption.CommonAdoptionLabels;
import org.artifactory.metrics.framework.adoption.FeatureAdoptionService;
import org.artifactory.metrics.framework.adoption.packages.FeatureAdoptionPackageRepository;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.service.validation.RepoConfigValidationException;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.AdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.BasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.GeneralRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.replication.RepositoryReplicationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.federated.NegotiationConflictsModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.CreateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.artifactory.util.ExceptionUtils;
import org.artifactory.util.RepoPathUtils;
import org.jfrog.security.crypto.exception.CryptoRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;

/**
 * UI REST service that creates a repository from its UI model. The built configuration is checked with
 * {@link RepoConfigValidator}, including {@link RepoConfigValidator#validatePluginPackageType}, before it is saved;
 * a {@link ConfigurationException} raised by a backend validator while saving is returned with its status code.
 *
 * @author Aviad Shikloshi
 * @author Dan Feldman
 */
@Component
public class CreateRepositoryConfigService implements
        RestService<RepositoryConfigModel<? extends BasicRepositoryConfigModel, ?
                extends AdvancedRepositoryConfigModel, ? extends RepositoryReplicationConfigModel>> {
    private static final Logger log = LoggerFactory.getLogger(CreateRepositoryConfigService.class);
    private static final String FED_ATTR_OPT_IN_PARAM = "isFedAttrSupported";

    private final CentralConfigService configService;
    private final RepoConfigValidator repoValidator;
    private final CreateRepoConfigHelper creator;
    private final ProjectsService projectsService;
    private final RepositoryConfigService repositoryConfigService;
    private FeatureAdoptionService featureAdoptionService;
    private final RepositoryService repositoryService;

    @Autowired
    public CreateRepositoryConfigService(CentralConfigService configService, RepoConfigValidator repoValidator,
            CreateRepoConfigHelper creator, ProjectsService projectsService,
            RepositoryService repositoryService, RepositoryConfigService repositoryConfigService) {
        this.configService = configService;
        this.repoValidator = repoValidator;
        this.creator = creator;
        this.projectsService = projectsService;
        this.repositoryConfigService = repositoryConfigService;
        this.repositoryService = repositoryService;
    }

    @Override
    public void execute(ArtifactoryRestRequest<RepositoryConfigModel<?, ?, ?>> request, RestResponse response) {
        RepositoryConfigModel<?, ?, ?> model = request.getImodel();
        MutableCentralConfigDescriptor updatedConfigDescriptor = configService.getMutableDescriptor();
        String repoKey =
                Optional.ofNullable(model.getGeneral()).orElse(new GeneralRepositoryConfigModel()).getRepoKey();
        try {
            repoValidator.validateEnvironmentsForRepoType(model);
            addRepositoryToProject(model, repoKey);
            boolean fedAttrOptIn = ConstantValues.repositoriesFederationsAttributeEnabled.getBoolean() &&
                    "true".equalsIgnoreCase(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM));
            RepoConfig repoConfig = createRepoConfig(response, model, updatedConfigDescriptor, repositoryService,
                    fedAttrOptIn);
            if (repoConfig != null) {
                repoValidator.validatePluginPackageType(repoConfig, null);
                projectsService.validateRepoConfigIsMatchingToProjectSpec(model.getGeneral().getProjectKey(),
                        model.getGeneral().getRepoKey(), model.getGeneral().getEnvironments());
                int numberOfDiffs = configService.findDiff(configService.getDescriptor(),
                        updatedConfigDescriptor).getNumberOfDiffs();
                if (numberOfDiffs > 0) {
                    configService.saveEditedDescriptorAndReload(updatedConfigDescriptor);
                }
                repositoryConfigService.upsertRepoConfig(repoConfig);
                addMetricRepoCreated(model.getTypeSpecific().getRepoType());
                String pkgType = fetchRepoPackageType(request);
                log.info("Successfully created repository {} with package type {}", repoKey, pkgType);
                response.info("Successfully added repository '" + repoKey + "'");
            }
        } catch (LockingException e) {
            removeRepositoryFromCurrentProject(repoKey);
            String errorMessage = "Failed to create repositories. Details: " + e.getMessage();
            throw new LockingException(errorMessage, e);
        } catch (NegotiationException e) {
            log.warn(e.getMessage(), e);
            response.responseCode(HttpStatus.SC_BAD_REQUEST)
                    .entity(new NegotiationConflictsModel(e.getMessage(), e.getConflicts()));
            removeRepositoryFromCurrentProject(repoKey);
        } catch (FederatedRemoteMembersUpdateException e) {
            log.warn(e.getMessage(), e);
            response.error(e.getMessage());
            removeRepositoryFromCurrentProject(repoKey);
        } catch (CryptoRuntimeException e) {
            log.warn("Failed to decrypt repository {} password {}", repoKey, e.getMessage());
            response.responseCode(HttpStatus.SC_BAD_REQUEST).error("The password could not be decrypted");
            removeRepositoryFromCurrentProject(repoKey);
        } catch (RepoConfigValidationException e) {
            log.warn("Failed to create repository '{}'.", repoKey, e);
            response.responseCode(SC_BAD_REQUEST).error("Failed to create repository: " + e.getMessage());
            removeRepositoryFromCurrentProject(repoKey);
        } catch (RepoConfigException e) {
            log.warn("Failed to create repository '{}'.", repoKey, e);
            response.responseCode(e.getStatusCode())
                    .error("Failed to create repository " + repoKey + ": " + e.getMessage());
            removeRepositoryFromCurrentProject(repoKey);
        } catch (ConfigurationException e) {
            log.warn("Failed to create repository '{}': {}", repoKey, e.getMessage());
            response.responseCode(e.getStatusCode())
                    .error("Failed to create repository " + repoKey + ": " + e.getMessage());
            removeRepositoryFromCurrentProject(repoKey);
        } catch (Exception e) {
            log.error("Failed to create repository '{}'.", repoKey, e);
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            response.error("Failed to create repository " + repoKey + ": " +
                    (rootCause != null ? rootCause.getMessage() : e.getMessage()));
            removeRepositoryFromCurrentProject(repoKey);
        }
    }

    private void removeRepositoryFromCurrentProject(String repoKey) {
        try {
            projectsService.removeRepositoryFromCurrentProject(repoKey);
        } catch (Exception e) {
            log.error("Failed to remove repository: '{}' from project", repoKey, e);
        }
    }

    private String fetchRepoPackageType(ArtifactoryRestRequest<RepositoryConfigModel<?, ?, ?>> request) {
        return Optional.of(request)
                .map(ArtifactoryRestRequest::getImodel)
                .map(RepositoryConfigModel::getTypeSpecific)
                .map(TypeSpecificConfigModel::getRepoType)
                .map(RepoType::getEffectiveType)
                .orElse("unknown");
    }

    public RepoConfig createRepoConfig(RestResponse<?> response, RepositoryConfigModel<?, ?, ?> model,
            MutableCentralConfigDescriptor configDescriptor, RepositoryService repositoryService,
            boolean fedAttrOptIn) throws RepoConfigException {
        String repoKey = model.getGeneral().getRepoKey();
        FederationConfigModel federation = model.getFederation();
        boolean hasMirrors = fedAttrOptIn && federation != null
                && federation.getMirrors() != null && !federation.getMirrors().isEmpty();
        if (model instanceof FederatedRepositoryConfigModel || hasMirrors) {
            ContextHelper.get().beanForType(FederatedRepoService.class).validateFederationLicenseInstalled();
        }

        if (repositoryConfigService.existsByRepoKey(repoKey)) {
            response.error("Repository " + repoKey + " already exists").responseCode(HttpStatus.SC_BAD_REQUEST);
            return null;
        }
        log.info("Creating repository {}", repoKey);
        //Run repo name validation only on create
        String repoType = model.getRepoType();
        repoValidator.validateRepoNameForCreate(model.getGeneral().getRepoKey(), repoType);
        if ((model instanceof LocalRepositoryConfigModel || model instanceof FederatedRepositoryConfigModel) &&
                RepoPathUtils.isCache(repoKey)) {
            throw new RepoConfigException(StringUtils.capitalize(repoType) + " repository '" + repoKey + "' with " +
                    "'-cache' suffix is not allowed", SC_BAD_REQUEST);
        }
        return model.createRepoConfig(creator, configDescriptor, repositoryService, fedAttrOptIn);
    }

    private void addRepositoryToProject(@NonNull RepositoryConfigModel<?, ?, ?> model, @NonNull String repoKey) {
        final String projectKey = model.getGeneral().getProjectKey();
        final Set<String> environments = model.getGeneral().getEnvironments();
        if (StringUtils.isEmpty(projectKey) || environments == null) {
            return;
        }
        projectsService.upsertProjectRepository(repoKey, projectKey, environments);
    }

    public void addMetricRepoCreated(RepoType repoType) {
        if (repoType != null) {
            FeatureAdoptionPackageRepository featureAdoptionRepo =
                    FeatureAdoptionPackageRepository.fromType(repoType.getType());
            if (featureAdoptionRepo != null) {
                featureAdoptionService.trackAdoption(featureAdoptionRepo.feature(),
                        FeatureAdoptionAction.UPDATED, true, CommonAdoptionLabels.EMPTY);
            }
        }
    }

    @Autowired
    public void setFeatureAdoptionService(FeatureAdoptionService featureAdoptionService) {
        this.featureAdoptionService = featureAdoptionService;
    }
}
