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

package org.artifactory.converter.postinit.v103;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.packagetype.ExternalDependenciesConfig;
import org.artifactory.util.ExternalDependenciesPackageTypeConfigMapper;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;

import java.util.List;

/**
 * Add /golang.org/** as a whitelist pattern to all of the Go virtual repositories with default patterns
 *
 * @author Nadavy
 */
@Slf4j
public class GoVirtualRepositoryWhitelistConverter extends PostInitConverter {

    private static final String GO_LANG_PATTERN = "**/golang.org/**";
    private static final String K8S_PATTERN = "**/k8s.io/**";
    private static final List<String> OLD_VIRTUAL_PATTERN = List.of(
            "**/github.com/**",
            "**/go.googlesource.com/**",
            "**/gopkg.in/**");

    public GoVirtualRepositoryWhitelistConverter(ArtifactoryServiceVersion from) {
        super(from, from);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        ContextHelper.get().beanForType(SecurityService.class).doAsSystem(this::convert);
    }

    private void convert() {
        log.info("Whitelisting golang.org and k8s.io to all Go virtual repositories with default patterns");
        RepositoryConfigService repositoryConfigService =
                ContextHelper.get().beanForType(RepositoryConfigService.class);
        List<VirtualRepoConfig> virtualRepoConfigs =
                repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL).stream()
                        .map(VirtualRepoConfig.class::cast).toList();
        virtualRepoConfigs.stream()
                .filter(this::isGoRepository)
                .forEach(this::addReposToWhitelist);
        repositoryConfigService.bulkUpsertRepoConfigs(virtualRepoConfigs, true);
    }

    private void addReposToWhitelist(VirtualRepoConfig virtualRepoConfig) {
        ExternalDependenciesConfig externalDependenciesConfig =
                ExternalDependenciesPackageTypeConfigMapper.INSTANCE.map(virtualRepoConfig.getPackageTypeConfig());
        List<String> patterns = externalDependenciesConfig.getPatterns();
        if (OLD_VIRTUAL_PATTERN.equals(patterns)) {
            log.info("Adding golang.org/** and k8s.io/** patterns to Go repository {}", virtualRepoConfig.getKey());
            // add only if the patterns are default
            patterns.add(GO_LANG_PATTERN);
            patterns.add(K8S_PATTERN);
        }
    }

    private boolean isGoRepository(RepoConfig repoConfig) {
        return RepoType.Go.equals(repoConfig.getPackageType());
    }

    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(ArtifactoryServiceVersionProvider.v6100.get()) &&
                ArtifactoryServiceVersionProvider.v6100.get().beforeOrEqual(target.getVersion());
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        //noop
    }
}
