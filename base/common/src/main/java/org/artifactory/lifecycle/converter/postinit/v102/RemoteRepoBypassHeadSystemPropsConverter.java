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

package org.artifactory.lifecycle.converter.postinit.v102;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This converter is created as part of RTFACT-17371 and is meant to ease the upgrade of users coming from 5.4.12
 * If version 6.4.0 and above is detected and {@link this#PROP_KEY} is present in system props
 *
 * @author Dan Feldman
 */
public class RemoteRepoBypassHeadSystemPropsConverter extends PostInitConverter {
    private static final Logger log = LoggerFactory.getLogger(RemoteRepoBypassHeadSystemPropsConverter.class);

    //This key was included in 5.4.12 but there's no reason to keep it around out of that version so its not in the
    //consts.
    static final String PROP_KEY = "artifactory.downloads.shouldSkipHeadRepositoriesList";


    public RemoteRepoBypassHeadSystemPropsConverter(ArtifactoryServiceVersion from, ArtifactoryServiceVersion until) {
        super(from, until);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        if (!isInterested(source, target)) {
            log.debug("RemoteRepoBypassHeadSystemPropsConverter deemed unnecessary to run");
        }
        SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            securityService.authenticateAsSystem();
            convert();
        } finally {
            // Restore previous permissions
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    private void convert() {
        RepositoryConfigService repositoryConfigService =
                ContextHelper.get().beanForType(RepositoryConfigService.class);
        List<RemoteRepoConfig> remoteRepoConfigs =
                repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE).stream()
                        .map(RemoteRepoConfig.class::cast)
                        .toList();
        if (!addRepoConfigWhereNeeded(remoteRepoConfigs).isEmpty()) {
            repositoryConfigService.bulkUpsertRepoConfigs(remoteRepoConfigs, true);
        }
    }

    /**
     * Mainly separated for the test.
     * Goes over the {@param repoList}, acquires the matching repo from {@param remoteRepoConfigs} and adds
     * the {@link RemoteRepositoryRepoTypeConfig#bypassHeadRequests} config.
     */
    List<RemoteRepoConfig> addRepoConfigWhereNeeded(List<RemoteRepoConfig> remoteRepoConfigs) {
        List<RemoteRepoConfig> remoteReposToUpdate = new LinkedList<>();
        //Already passed isInterested so the list has content
        String[] bypassingRepoList = getHeadBypassingRemoteRepoList().split(",");
        Map<String, RemoteRepoConfig> remoteRepos = remoteRepoConfigs.stream()
                .collect(Collectors.toMap(RepoConfig::getKey, Function.identity()));
        Arrays.stream(bypassingRepoList)
                .filter(remoteRepos::containsKey)
                .map(remoteRepos::get)
                .filter(repo -> !repo.getRepoTypeConfig().isBypassHeadRequests())
                .peek(repo -> log.info("Adding 'bypass head request' config to repo '{}'", repo.getKey()))
                .forEach(repo -> {
                    repo.getRepoTypeConfig().setBypassHeadRequests(true);
                    remoteReposToUpdate.add(repo);
                });
        return remoteReposToUpdate;
    }


    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(ArtifactoryServiceVersionProvider.v640m007.get())
                && target.getVersion().afterOrEqual(ArtifactoryServiceVersionProvider.v640m007.get())
                && !StringUtils.isBlank(getHeadBypassingRemoteRepoList());
    }

    /**
     * Since {@link this#PROP_KEY} is omitted from the  {@link org.artifactory.common.ConstantValues},
     * this method will return props that were put on the file but are not present in
     * {@link org.artifactory.common.ConstantValues}
     */
    private String getHeadBypassingRemoteRepoList() {
        return ArtifactoryHome.get().getArtifactoryProperties().getProperty(PROP_KEY, null);
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        //noop
    }
}
