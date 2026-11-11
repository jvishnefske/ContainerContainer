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

package org.artifactory.lifecycle.converter.postinit.v113;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;

import java.util.List;

/**
 * @author Volodymyr Shulga
 * The purpose of this converter is to replace all FrogML repository type with MachineLearning repository type.
 * The reference to the repository type is stored in the repository configuration file as well as in the repo table.
 */
@Slf4j
public class FrogMLRepoConverter extends PostInitConverter {
    private RepositoryService repositoryService;

    public FrogMLRepoConverter(ArtifactoryServiceVersion until) {
        super(until, until);
    }

    /**
     * Any version after 7.79.0 should run this converter. FrogML repository type was introduced in 7.79.0 according to
     * RTDEV-38597
     */
    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().afterOrEqual(ArtifactoryServiceVersionProvider.v7790.get())
                && source.getVersion().before(ArtifactoryServiceVersionProvider.v71040.get());
    }

    /**
     * Do the following:
     * 1. Get all frogml local and fed repositories (without cache repos)
     * 2. Change the repository type to MachineLearning
     * 3. Bulk upsert the updated repositories
     * indexing due to props interceptors
     */
    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        log.info("Start converting FrogML repositories");
        repositoryService = ContextHelper.get().beanForType(RepositoryService.class);
        SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
        securityService.doAsSystem(this::doConvert);
        log.info("Done converting FrogML repositories");
    }

    private void doConvert() {
        final List<LocalRepoConfig> updatedFrogMLConfigs = getLocalFrogMLRepositories()
                .stream()
                .map(config -> {
                    final LocalRepoConfig localRepoConfig =
                            LocalRepoConfig.create(config.getKey(), RepoType.MachineLearning);
                    log.info("Changing repoType from FrogML to machinelearning for {}", config.getKey());
                    localRepoConfig.setCreation(config.getCreation());
                    localRepoConfig.setModification(config.getModification());
                    return localRepoConfig;
                })
                .toList();
        log.info("About to change {} FrogML repositories types to machinelearning", updatedFrogMLConfigs.size());
        repositoryService.bulkUpsertRepoConfigs(updatedFrogMLConfigs, true);
    }

    private List<LocalRepoConfig> getLocalFrogMLRepositories() {
        return repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos().stream()
                .filter(config -> "frogml".equals(config.getPackageType().getType()))
                .filter(LocalRepoConfig.class::isInstance)
                .map(LocalRepoConfig.class::cast)
                .toList();
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // not used
    }
}
