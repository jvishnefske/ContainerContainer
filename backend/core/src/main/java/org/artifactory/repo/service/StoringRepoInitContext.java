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

package org.artifactory.repo.service;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.ph.PackageVirtualResolutionService;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.virtual.interceptor.VirtualRepoInterceptor;
import org.artifactory.storage.fs.VfsItemProviderFactory;
import org.artifactory.storage.fs.lock.FsItemsVault;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.service.StatsService;
import org.artifactory.storage.service.StatsServiceImpl;

import java.util.Collection;

/**
 * Pre-resolved dependencies for {@link org.artifactory.repo.db.DbStoringRepoMixin#init(boolean)}.
 * Built once per repository rebuild and passed to each local repo init to avoid repeated context lookups.
 */
public record StoringRepoInitContext(
        SecurityService securityService,
        StorageInterceptors interceptors,
        AddonsManager addonsManager,
        FileService fileService,
        StatsService statsService,
        InternalRepositoryService repositoryService,
        PropertiesService propertiesService,
        VfsItemProviderFactory vfsItemProviderFactory,
        FsItemsVault fileVault,
        FsItemsVault folderVault,
        Collection<VirtualRepoInterceptor> virtualRepoInterceptors,
        PackageVirtualResolutionService packageVirtualResolutionService) {

    /**
     * Resolves all dependencies from the given context once. Use the returned instance when
     * initializing multiple local repos during rebuild to avoid N×context lookups.
     */
    public static StoringRepoInitContext create(ArtifactoryContext context) {
        SecurityService securityService = context.beanForType(SecurityService.class);
        StorageInterceptors interceptors = context.beanForType(StorageInterceptors.class);
        AddonsManager addonsManager = context.beanForType(AddonsManager.class);
        FileService fileService = context.beanForType(FileService.class);
        StatsService statsService = context.beanForType(StatsServiceImpl.class);
        InternalRepositoryService repositoryService = context.beanForType(InternalRepositoryService.class);
        PropertiesService propertiesService = context.beanForType(PropertiesService.class);
        VfsItemProviderFactory vfsItemProviderFactory = context.beanForType(VfsItemProviderFactory.class);
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        FsItemsVault fileVault = haAddon.getFsFileItemVault();
        FsItemsVault folderVault = haAddon.getFsFolderItemVault();
        Collection<VirtualRepoInterceptor> virtualRepoInterceptors =
                context.beansForType(VirtualRepoInterceptor.class).values();
        PackageVirtualResolutionService packageVirtualResolutionService =
                context.beanForType(PackageVirtualResolutionService.class);
        return new StoringRepoInitContext(securityService, interceptors, addonsManager, fileService, statsService,
                repositoryService, propertiesService, vfsItemProviderFactory, fileVault, folderVault,
                virtualRepoInterceptors, packageVirtualResolutionService);
    }
}
