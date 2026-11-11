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

package org.artifactory.ui.rest.service.artifacts.search.packagesearch;

import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria.FieldSearchCriteria;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria.PackageSearchCriteria;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria.PackageSearchCriteria.PackageSearchCriterion;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.search.AqlUISearchModel;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.search.AqlUISearchPackageTypeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.entitlement.EntitlementType.PACKAGES_IDE_EXTENSION;

/**
 * @author Dan Feldman
 */
@Component
public class GetPackageSearchOptionsService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(GetPackageSearchOptionsService.class);

    private static final String AVAILABLE_PACKAGES = "availablePackages";
    // Criteria the UI will now show
    private static final Set<String> filteredCriteria = Set.of(PackageSearchCriteria.terraformModuleType.name(),
            PackageSearchCriteria.terraformProviderType.name());

    private boolean isPackagesIdeEntitled() {
        return  ConstantValues.forcePluginsRepositoryEnabled.getBoolean() ||
                ContextHelper.get().beanForType(AddonsManager.class).isEntitled(PACKAGES_IDE_EXTENSION);
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String pkgType = request.getPathParamByKey("type");
        pkgType = pkgType == null ? "" : pkgType;
        //Special call to get package types that are available to search by
        if (pkgType.equalsIgnoreCase(AVAILABLE_PACKAGES)) {
            response.iModelList(getPackageTypes());
            response.responseCode(HttpStatus.SC_OK);
        } else {
            try {
                List<AqlUISearchModel> availableOptions = Lists.newArrayList();
                availableOptions.addAll(PackageSearchCriteria.getCriteriaByPackage(pkgType)
                        .stream()
                        .map(PackageSearchCriterion::getModel)
                        .filter(item -> !filteredCriteria.contains(item.getId()))
                        .collect(Collectors.toList())
                );
                availableOptions.addAll(Stream.of(FieldSearchCriteria.values())
                        .map(FieldSearchCriteria::getModel)
                        .collect(Collectors.toList())
                );
                response.iModel(availableOptions);
                response.responseCode(HttpStatus.SC_OK);
            } catch (UnsupportedOperationException uoe) {
                log.debug(uoe.getMessage(), uoe);
                response.error(uoe.getMessage());
            }
        }
    }

    @Nonnull
    private List<AqlUISearchPackageTypeModel> getPackageTypes() {
        return Stream.of(PackageSearchCriteria.PackageSearchType.values())
                .map(AqlUISearchPackageTypeModel::new)
                .filter(this::filterPackagesUnderDev)
                .collect(Collectors.toList());
    }

    private boolean filterPackagesUnderDev(AqlUISearchPackageTypeModel searchPackageTypeModel) {
        if (PackageSearchCriteria.PackageSearchType.machinelearning.getId().equals(searchPackageTypeModel.getId())) {
            return ConstantValues.machineLearningRepositoryEnabled.getBoolean();
        } else if (PackageSearchCriteria.PackageSearchType.bazelmodules.getId()
                .equals(searchPackageTypeModel.getId())) {
            return ConstantValues.bazelModulesRepositoryEnabled.getBoolean();
        } else if (PackageSearchCriteria.PackageSearchType.nix.getId().equals(searchPackageTypeModel.getId())) {
            return ConstantValues.nixRepositoryEnabled.getBoolean();
        } else if (PackageSearchCriteria.PackageSearchType.agentplugins.getId()
                           .equals(searchPackageTypeModel.getId())) {
            return ConstantValues.agentPluginsRepositoryEnabled.getBoolean();
        } else if (PackageSearchCriteria.PackageSearchType.ansible.getId().equals(searchPackageTypeModel.getId())) {
            return ConstantValues.ansibleRepositoryEnabled.getBoolean();
        } else if (PackageSearchCriteria.PackageSearchType.hex.getId().equals(searchPackageTypeModel.getId())) {
            return ConstantValues.hexRepositoryEnabled.getBoolean();
        } else if (PackageSearchCriteria.PackageSearchType.nimmodel.getId().equals(searchPackageTypeModel.getId())) {
            return ConstantValues.nimModelRepositoryEnabled.getBoolean();
        } else if (PackageSearchCriteria.PackageSearchType.jem.getId().equals(searchPackageTypeModel.getId())) {
            return ConstantValues.jemRepositoryEnabled.getBoolean();
        } else if (PackageSearchCriteria.PackageSearchType.aieditorextensions.getId()
                .equals(searchPackageTypeModel.getId()) && isPackagesIdeEntitled()) {
            return ConstantValues.aieditorExtensionsRepositoryEnabled.getBoolean();
        } else if (PackageSearchCriteria.PackageSearchType.jetbrainsplugins.getId()
                .equals(searchPackageTypeModel.getId()) && isPackagesIdeEntitled()) {
            return ConstantValues.jetbrainsPluginsRepositoryEnabled.getBoolean();
        }
        return true;
    }
}