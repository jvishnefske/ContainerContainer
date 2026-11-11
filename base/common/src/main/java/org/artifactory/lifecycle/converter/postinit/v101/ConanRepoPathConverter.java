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

package org.artifactory.lifecycle.converter.postinit.v101;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.util.CollectionUtils;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.type;
import static org.artifactory.aql.api.internal.AqlBase.and;

/**
 * @author Tomer Mayost
 */
@Slf4j
public class ConanRepoPathConverter extends PostInitConverter {

    private static final String PATH_DELIMITER = "/";

    private RepositoryService repositoryService;
    private PropertiesService propertiesService;
    private SecurityService securityService;

    public ConanRepoPathConverter(ArtifactoryServiceVersion from, ArtifactoryServiceVersion until) {
        super(from, until);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        Authentication authentication = null;
        try {
            repositoryService = ContextHelper.get().beanForType(RepositoryService.class);
            propertiesService = ContextHelper.get().beanForType(PropertiesService.class);
            securityService = ContextHelper.get().beanForType(SecurityService.class);
            authentication = SecurityContextHolder.getContext().getAuthentication();
            securityService.authenticateAsSystem();
            doConvert();
            log.debug("Done converting conan repo paths");
        } finally {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    private void doConvert() {
        getLocalConanRepoKeys()
                .stream()
                .map(this::getConanFilesByRepoFromDB)
                .forEach(this::correctPathsIfNeeded);
    }

    private Set<RepoPath> getConanFilesByRepoFromDB(String repoKey) {
        AqlApiItem query = AqlApiItem.create()
                .filter(
                        and(
                                repo().equal(repoKey),
                                type().equal(AqlItemTypeEnum.file.signature),
                                name().matches("conanfile.py")
                        ));
        return ContextHelper.get().beanForType(AqlService.class).
                executeQueryEager(query)
                .getResults()
                .stream()
                .map(AqlUtils::fromAql)
                .collect(Collectors.toSet());
    }

    private void correctPathsIfNeeded(Set<RepoPath> conanFilesDotPy) {
        if (CollectionUtils.isNullOrEmpty(conanFilesDotPy)) {
            return;
        }
        for (RepoPath conanFile : conanFilesDotPy) {
            String conanPkgName = getConanPkgName(conanFile);
            if (conanPkgName != null && conanFile.getPath().startsWith(conanPkgName)) {
                RepoPath parent = conanFile.getParent();
                RepoPath ancestor = parent != null ? parent.getParent() : null;
                repositoryService.getChildrenDeeply(ancestor)
                        .stream()
                        .filter(itemInfo -> !itemInfo.isFolder())
                        .map(ItemInfo::getRepoPath)
                        .map(this::correctConanRepoPath)
                        .filter(Objects::nonNull)
                        .filter(BasicStatusHolder::hasErrors)
                        .forEach(this::logError);
            }
        }

    }

    @SuppressWarnings("ConstantConditions")
    private String getConanPkgName(RepoPath repoPath) {
        Properties properties = propertiesService.getProperties(repoPath);
        String pkgName = properties.getFirst("conan.package.name");
        return Strings.isNullOrEmpty(pkgName) ? null : pkgName;
    }

    private MoveMultiStatusHolder correctConanRepoPath(RepoPath repoPathToFix) {
        String fixedPath = fixConanPath(repoPathToFix.getPath());
        if (fixedPath != null) {
            RepoPath correctPath = RepoPathFactory.create(repoPathToFix.getRepoKey(), fixedPath);
            if (log.isInfoEnabled()) {
                log.info("moving {} to {}", repoPathToFix.toPath(), correctPath.toPath());
            }
            return repositoryService.moveFlat(repoPathToFix, repoPathToFix.getRepoKey(), fixedPath, null, null, false,
                    true, ConstantValues.moveCopyDefaultTransactionSize.getInt());
        }

        return null;
    }

    String fixConanPath(String path) {
        String[] parts = path.split(PATH_DELIMITER);
        if (parts.length < 4) {
            log.error("Illegal conan path: {}", path);
            return null;
        }
        String username = parts[2];
        parts = (String[]) ArrayUtils.remove(parts, 2);
        return username + PATH_DELIMITER + String.join(PATH_DELIMITER, parts);
    }

    private List<String> getLocalConanRepoKeys() {
        return repositoryService.getLocalRepoConfigsExcludingInfoRepos().stream()
                .filter(repoConfig -> RepoType.Conan.equals(repoConfig.getPackageType()))
                .map(RepoConfig::getKey)
                .toList();
    }

    private void logError(MoveMultiStatusHolder moveMultiStatusHolder) {
        log.error("failed to move conan path with error {}", moveMultiStatusHolder.getLastError().getMessage());
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // not used
    }
}