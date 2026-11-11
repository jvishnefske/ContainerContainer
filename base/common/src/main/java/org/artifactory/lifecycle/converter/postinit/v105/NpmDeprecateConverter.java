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

package org.artifactory.lifecycle.converter.postinit.v105;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.concurrent.ThreadPoolConfig;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.ph.selector.IndexingSelector;
import org.artifactory.repo.RepoPath;
import org.artifactory.util.CollectionUtils;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.path;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.property;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.type;
import static org.artifactory.aql.api.internal.AqlBase.and;

/**
 * @author asafz
 * The purpose of this converter is to update package.json metadata files of npm packages that marked as deprecated.
 * From RT 7.49.0 - we are handling "npm.deprecated" property changes in afterPropertyChange and updating the
 * metadata files
 */
@Slf4j
public class NpmDeprecateConverter extends PostInitConverter {
    private static final String NPM_DEPRECATED = "npm.deprecated";
    private RepositoryService repositoryService;

    public NpmDeprecateConverter(ArtifactoryServiceVersion until) {
        super(until, until);
    }

    /**
     * Any version until 7.49.0 should run this converter
     */
    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(ArtifactoryServiceVersionProvider.v7490m001.get());
    }

    /**
     * Do the following async:
     * 1. Get all npm local and fed repositories (without cache repos)
     * 2. Search after all tgz artifacts with "npm.deprecated" prop
     * 3. Remove the value of this prop without triggering interceptors, and add the same value - will trigger
     * indexing due to props interceptors
     */
    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        ExecutorService asyncExecutor = ArtifactoryExecutorFactory.newClassicThreadPool(ThreadPoolConfig.builder()
                .corePoolSize(1)
                .maxThreads(1)
                .keepAliveTimeout(0L)
                .keepAliveTimeoutTimeUnit(TimeUnit.MILLISECONDS)
                .queue(new LinkedBlockingQueue<>())
                .daemon(true)
                .threadNameFormat("npm-deprecated-converter-%s")
                .build(), ContextHelper.get(), true);
        asyncExecutor.submit(this::convertAsync);
        log.info("Triggered converting package.json files of npm deprecated packages async");
        asyncExecutor.shutdown();
    }

    @VisibleForTesting
    void convertAsync() {
        Authentication authentication = null;
        try {
            log.info("Start converting package.json files of npm deprecated packages");
            repositoryService = ContextHelper.get().beanForType(RepositoryService.class);
            SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
            authentication = SecurityContextHolder.getContext().getAuthentication();
            securityService.authenticateAsSystem();
            doConvert();
            log.info("Done converting package.json files of npm deprecated packages");
        } finally {
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
    }

    private void doConvert() {
        getLocalNpmRepoKeys()
                .stream()
                .map(this::getNpmPackagesWithDeprecateProp)
                .forEach(this::handleDeprecate);
    }

    private Set<RepoPath> getNpmPackagesWithDeprecateProp(String repoKey) {
        AqlApiItem query = AqlApiItem.create()
                .filter(
                        and(
                                repo().equal(repoKey),
                                type().equal(AqlItemTypeEnum.file.signature),
                                property().key().equal(NPM_DEPRECATED)
                        ))
                .include(name(), repo(), path());
        return ContextHelper.get().beanForType(AqlService.class).
                executeQueryEager(query)
                .getResults()
                .stream()
                .map(AqlUtils::fromAql)
                .filter(repoPath -> repoPath.getPath().endsWith(".tgz"))
                .collect(Collectors.toSet());
    }

    private void handleDeprecate(Set<RepoPath> npmDeprecatedPackages) {
        if (CollectionUtils.isNullOrEmpty(npmDeprecatedPackages)) {
            return;
        }
        for (RepoPath repoPath : npmDeprecatedPackages) {
            try {
                ContextHelper.get().beanForType(IndexingSelector.class)
                        .reindex(repoPath.getRepoKey(), repoPath.getPath());
            } catch (Exception e) {
                log.warn("Failed to trigger reindex on repo {}, path {},{}", repoPath.getRepoKey(), repoPath.getPath(),
                        e.getMessage());
            }
        }
    }

    private List<String> getLocalNpmRepoKeys() {
        return repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos().stream()
                .filter(config -> RepoType.Npm.equals(config.getPackageType()))
                .map(RepoConfig::getKey)
                .toList();
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // not used
    }
}