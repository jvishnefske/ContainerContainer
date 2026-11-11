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

package org.artifactory.repo.interceptor;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.NuGetAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.profile.PackageProfile;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.ImportInterceptor;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;

import static org.artifactory.addon.nuget.NuGetProperties.Id;
import static org.artifactory.addon.nuget.NuGetProperties.Version;

/**
 * Triggers NuGet package related storage events
 *
 * @author Noam Y. Tenne
 */
@Component
@Profile("!" + PackageProfile.NEW_NUGET_IMPL)
public class NuGetCalculationInterceptor extends StorageInterceptorAdapter implements ImportInterceptor {
    private static final Logger log = LoggerFactory.getLogger(NuGetCalculationInterceptor.class);
    private static final String NUGET_PACKAGE_EXTENSION = ".nupkg";
    private static final String SYMBOL_PACKAGE_EXTENSION = ".snupkg";
    private static final String SYMBOL_PACKAGE_EXTENSION_LEGACY = ".symbols.nupkg";

    @Inject
    AddonsManager addonsManager;
    @Inject
    RepositoryService repositoryService;
    @Inject
    MetadataServiceIndexHandler metadataServiceIndexHandler;

    @Override
    public void afterDelete(VfsItem fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        removeNuPkgFromRepositoryIfNeeded(fsItem);
        removeSymbolsFromRepoIfNeeded(fsItem);
    }

    @Override
    public void afterCreate(VfsItem fsItem, MutableStatusHolder statusHolder, InterceptorCreateContext ctx) {
        extractNuPkgInfoIfNeeded(fsItem, new BasicStatusHolder());
        indexSymbolsIfNeeded(fsItem);
    }

    @Override
    public void afterMove(VfsItem sourceItem, VfsItem targetItem, MutableStatusHolder statusHolder,
            Properties properties, InterceptorMoveCopyContext ctx) {
        handleOnAfterMoveOrCopy(sourceItem, targetItem);
        indexSymbolsIfNeeded(targetItem);
    }

    @Override
    public void afterCopy(VfsItem sourceItem, VfsItem targetItem, MutableStatusHolder statusHolder,
            Properties properties, InterceptorMoveCopyContext ctx) {
        handleOnAfterMoveOrCopy(null, targetItem);
        indexSymbolsIfNeeded(targetItem);
    }

    @Override
    public void afterImport(VfsItem fsItem, MutableStatusHolder statusHolder) {
        extractNuPkgInfoIfNeeded(fsItem, statusHolder);
        indexSymbolsIfNeeded(fsItem);
    }

    /**
     * Extracts the info of the given item if it's a nupkg and is stored within a nupkg enabled repo
     */
    private void extractNuPkgInfoIfNeeded(VfsItem createdItem, MutableStatusHolder statusHolder) {
        if (shouldTakeAction(createdItem)) {
            log.debug("Sending Nuget package '{}' for async calculation at {} ms.", createdItem.getRepoPath(),
                    System.currentTimeMillis());
            addonsManager.addonByType(NuGetAddon.class).extractNuPkgInfo((FileInfo) createdItem.getInfo(), true);
        }
    }

    /**
     * If it's a nupkg recalculate the IDs latest version state for the repository
     */
    private void handleOnAfterMoveOrCopy(VfsItem<FileInfo> sourceItem, VfsItem<FileInfo> targetItem) {
        if ((sourceItem != null) && shouldTakeAction(sourceItem)) {
            removeNuPkgFromRepositoryIfNeeded(sourceItem);
        }

        if (shouldTakeAction(targetItem)) {
            Properties nuPkgProperties = targetItem.getProperties();
            String nuPkgId = Id.extract(nuPkgProperties);
            if (StringUtils.isBlank(nuPkgId)) {
                /**
                 * Package might have come from a repo with no NuGet support, so extract the info and the request
                 * calculation
                 */
                addonsManager.addonByType(NuGetAddon.class).extractNuPkgInfo((FileInfo) targetItem.getInfo(), true);
            } else {
                addonsManager.addonByType(NuGetAddon.class).addNuPkgToRepoCacheAsync(targetItem.getRepoPath(),
                        nuPkgProperties);
                metadataServiceIndexHandler.indexPackageAsync(targetItem.getInfo(), nuPkgProperties);
            }
        }
    }

    private void removeNuPkgFromRepositoryIfNeeded(VfsItem<FileInfo> affectedItem) {
        if (shouldTakeAction(affectedItem)) {
            Properties nuPkgProperties = affectedItem.getProperties();
            String nuPkgId = Id.extract(nuPkgProperties);
            String nuPkgVersion = Version.extract(nuPkgProperties);
            if (StringUtils.isNotBlank(nuPkgId) && StringUtils.isNotBlank(nuPkgVersion)) {
                addonsManager.addonByType(NuGetAddon.class).removeNuPkgFromRepoCache(affectedItem.getRepoPath(),
                        nuPkgId, nuPkgVersion);
            }
        }
    }

    @VisibleForTesting
    boolean shouldTakeAction(VfsItem<FileInfo> item) {
        if (item.isFile()) {
            RepoPath repoPath = item.getRepoPath();
            if (repoPath.getPath().endsWith(NUGET_PACKAGE_EXTENSION) && !isSymbolPackage(repoPath.getPath())) {
                String repoKey = repoPath.getRepoKey();
                RepoDescriptor repoDescriptor = repositoryService.repoDescriptorByKey(repoKey);
                return ((repoDescriptor != null) && repoDescriptor.getType().equals(RepoType.NuGet));
            }
        }

        return false;
    }

    private void indexSymbolsIfNeeded(VfsItem<FileInfo> item) {
        if (shouldTakeActionOnSymbolPackage(item)) {
            addonsManager.addonByType(NuGetAddon.class).extractSNuPkgInfo((FileInfo) item.getInfo());
        }
    }

    private void removeSymbolsFromRepoIfNeeded(VfsItem<FileInfo> affectedItem) {
        if (shouldTakeActionOnSymbolPackage(affectedItem)) {
            log.debug("Symbol package deletion detected. Executing relevant symbols deletion process...");
            addonsManager.addonByType(NuGetAddon.class).removeSymbolsFromDirectory((FileInfo) affectedItem.getInfo());
        }
    }

    private boolean shouldTakeActionOnSymbolPackage(VfsItem<FileInfo> item) {
        if (item.isFile()) {
            RepoPath repoPath = item.getRepoPath();
            String path = repoPath.getPath();
            return isSymbolPackage(path) && ConstantValues.nuGetSymbolServerSupportEnabled.getBoolean();
        }
        return false;
    }

    @VisibleForTesting
    boolean isSymbolPackage(String filename) {
        return StringUtils.isNotEmpty(filename) &&
                (filename.endsWith(SYMBOL_PACKAGE_EXTENSION) || filename.endsWith(SYMBOL_PACKAGE_EXTENSION_LEGACY));
    }
}
