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

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.api.maven.MavenArtifactInfo;
import org.artifactory.api.maven.MavenMetadataService;
import org.artifactory.api.maven.MavenMetadataWorkItem;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.SnapshotVersionBehaviorDescriptor;
import org.artifactory.event.EventHintType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.mime.MavenNaming;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.util.RepoLayoutUtils;
import org.artifactory.util.RepoPathUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.artifactory.common.ConstantValues.calculateMavenMetadataAsyncForFedMirror;
import static org.artifactory.common.ConstantValues.calculateMavenMetadataSyncForPom;

/**
 * Interceptor which handles maven metadata calculation upon creation and removal
 *
 * @author Noam Tenne
 */
@Slf4j
public class MavenMetadataCalculationInterceptor extends StorageInterceptorAdapter {
    private MavenMetadataService mavenMetadataService;
    private InternalRepositoryService repoService;

    @Autowired
    public void setMavenMetadataService(@NonNull MavenMetadataService mavenMetadataService) {
        this.mavenMetadataService = mavenMetadataService;
    }

    @NonNull
    public InternalRepositoryService getRepoService() {
        return repoService;
    }

    @Autowired
    public void setRepoService(@NonNull InternalRepositoryService repoService) {
        this.repoService = repoService;
    }

    /**
     * If the newly created item is a pom file, this method will calculate the maven metadata of it's parent folder
     *
     * @param fsItem       Newly created item
     * @param statusHolder StatusHolder
     */
    @Override
    public void afterCreate(VfsItem<?> fsItem, MutableStatusHolder statusHolder, InterceptorCreateContext ctx) {
        if (shouldRecalculateOnCreate(fsItem)) {
            RepoPath folderForMetadataCalculation = fsItem.getRepoPath().getParent();
            if (folderForMetadataCalculation == null) {
                log.debug("Couldn't find repo for maven metadata calculation");
                return;
            }
            if (shouldCalculateMavenMetadataAsyncForFedMirrorForFsItem(fsItem)) {
                mavenMetadataService.calculateMavenMetadataAsync(
                        buildMavenMetadataWorkItem(fsItem, folderForMetadataCalculation));
            } else if (shouldCalculateMavenMetadata(fsItem)) {
                // Unique snapshots require instant metadata calculation since it is used to calculate future snapshots.
                // This is also done for any kind of file to support classifier snapshot version introduced in Maven 3.
                // We also instantly calculate if non-unique pom is deployed for simplicity
                mavenMetadataService.calculateMavenMetadata(
                        buildMavenMetadataWorkItem(fsItem, folderForMetadataCalculation));
            }
            if (isPomFile(fsItem)) {
                // For pom files we need to trigger metadata calculation on the grandparent non-recursively -
                // potential new version and snapshot.
                if (!shouldCalculateMavenMetadataAsyncForFedMirror() && shouldCalculateMavenMetadataSyncForPom()) {
                    // It's a rare race condition, but it can happen if only the pom file is deployed, without the
                    // artifact and the metadata is not calculated synchronously. Next GET request for the pom file
                    // will return wrong data, since the metadata is not calculated yet.
                    mavenMetadataService.calculateMavenMetadata(
                            buildMavenMetadataWorkItem(fsItem, RepoPathUtils.getAncestor(fsItem.getRepoPath(), 2)));
                } else {
                    // This can be done asynchronously since it doesn't require instant update
                    mavenMetadataService.calculateMavenMetadataAsync(
                            buildMavenMetadataWorkItem(fsItem, RepoPathUtils.getAncestor(fsItem.getRepoPath(), 2)));
                }
            }
        }
    }

    private boolean shouldCalculateMavenMetadataSyncForPom() {
        return calculateMavenMetadataSyncForPom.getBoolean();
    }

    private boolean shouldCalculateMavenMetadataAsyncForFedMirror() {
        return calculateMavenMetadataAsyncForFedMirror.getBoolean() &&
                EventLogHintManager.contains(EventHintType.FederatedReplication);
    }

    private boolean shouldCalculateMavenMetadataAsyncForFedMirrorForFsItem(VfsItem<?> fsItem) {
        return shouldCalculateMavenMetadataAsyncForFedMirror() && shouldCalculateMavenMetadata(fsItem);
    }

    private boolean shouldCalculateMavenMetadata(VfsItem<?> fsItem) {
        return MavenNaming.isUniqueSnapshot(fsItem.getPath()) ||
                (isPomFile(fsItem) && MavenNaming.isSnapshot(fsItem.getPath()));
    }


    @Override
    public void afterMove(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        calculateMavenMetadata(sourceItem, targetItem, false);
    }


    @Override
    public void afterCopy(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        calculateMavenMetadata(sourceItem, targetItem, true);
    }

    private void calculateMavenMetadata(VfsItem<? extends ItemInfo> source, VfsItem<? extends ItemInfo> target,
            boolean copy) {
        calculateMavenMetadataOnTarget(target);
        if (!copy) {
            calculateMavenMetadataOnSource(source);
        }
    }

    private void calculateMavenMetadataOnTarget(@NonNull VfsItem<? extends ItemInfo> targetItem) {
        // handle target if it is not trash
        if (RepoPathUtils.isTrash(targetItem.getRepoPath())) {
            return;
        }

        // start calculation from the parent folder of the target path (unless it's the root)
        RepoPath folderForMetadataCalculation =
                targetItem.getRepoPath().isRoot() ? targetItem.getRepoPath() : targetItem.getRepoPath().getParent();

        mavenMetadataService.calculateMavenMetadataAsync(new MavenMetadataWorkItem(folderForMetadataCalculation, true));

        if (MavenNaming.isPom(targetItem.getRepoPath().getPath())) {
            // for pom files we need to trigger metadata calculation on the grandparent non-recursively -
            // potential new version and snapshot.
            RepoPath grandparentFolder = RepoPathUtils.getAncestor(targetItem.getRepoPath(), 2);
            mavenMetadataService.calculateMavenMetadataAsync(new MavenMetadataWorkItem(grandparentFolder, false));
        }
    }

    private void calculateMavenMetadataOnSource(VfsItem<? extends ItemInfo> sourceItem) {
        // handle source if it is not trash
        if (RepoPathUtils.isTrash(sourceItem.getRepoPath())) {
            return;
        }

        // recalculate the source repository only if it's not a cache repo and not copy
        RepoPath sourceForMetadataCalculation = sourceItem.getRepoPath().getParent();
        if (sourceForMetadataCalculation == null) {
            return;
        }
        RepoConfig repoConfig = repoService.getRepositoryConfigByKey(sourceItem.getRepoKey());

        if (repoConfig != null && !(repoConfig instanceof LocalCacheRepoConfig)) {
            mavenMetadataService.calculateMavenMetadataAsync(
                    new MavenMetadataWorkItem(sourceForMetadataCalculation, true));
        }
    }


    @Override
    public void afterDelete(VfsItem<?> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (shouldRecalculateOnCreate(fsItem) && isMavenSupportingRepo(getLocalRepo(fsItem))) {
            // calculate maven metadata on the parent path
            RepoPath parent = ctx.getRepoPath().getParent();
            if (parent != null && !parent.isRoot()) {
                if (!ConstantValues.mvnMetadataCalculationSkipDeleteEvent.getBoolean()) {
                    mavenMetadataService.calculateMavenMetadataAsync(buildMavenMetadataWorkItem(fsItem, parent));
                }
                if (isPomFile(fsItem) &&
                        !ConstantValues.mvnMetadataCalculationSkipDeleteEvent
                                .getBoolean()) { // ISSUE RTFACT - 10257
                    // also calculate maven metadata on the artifactId node (grandparent of the file)
                    mavenMetadataService
                            .calculateMavenMetadataAsync(buildMavenMetadataWorkItem(fsItem, parent.getParent()));
                }
            }
        }
    }

    @NonNull
    private MavenMetadataWorkItem buildMavenMetadataWorkItem(@NonNull VfsItem<?> fsItem, @NonNull RepoPath repoPath) {
        return new MavenMetadataWorkItem(repoPath, false, isMavenPlugin(fsItem));
    }

    private boolean shouldRecalculateOnCreate(VfsItem<?> fsItem) {
        if (!fsItem.isFile()) {
            return false;
        }

        LocalRepo<?, ?> localRepo = getLocalRepo(fsItem);
        if (!isMavenSupportingRepo(localRepo)) {
            return false;
        }

        // it's a local non-cache repository, check the snapshot behavior
        MavenArtifactInfo moduleInfo = MavenArtifactInfo.fromRepoPath(fsItem.getRepoPath());
        if (!moduleInfo.isValid()) {
            return false;
        }

        return !MavenNaming.isSnapshot(fsItem.getPath()) ||
                !SnapshotVersionBehaviorDescriptor.DEPLOYER.equals(localRepo.getMavenSnapshotVersionBehavior());
    }

    private LocalRepo<?, ?> getLocalRepo(VfsItem<?> fsItem) {
        return repoService.localOrFederatedRepositoryByKey(fsItem.getRepoKey());
    }

    private boolean isMavenSupportingRepo(LocalRepo<?, ?> localRepo) {
        if (localRepo == null) {
            return false;
        }
        RepoLayout repoLayout = localRepo.getDescriptor().getRepoLayout();
        RepoType type = localRepo.getDescriptor().getType();
        // Do not calculate maven metadata if type == null or type doesn't belong to the maven group (Maven, Ivy,
        //Gradle) or repoLayout not equals MAVEN_2_DEFAULT
        if (type != null && !(type.isMavenGroup() || RepoLayoutUtils.MAVEN_2_DEFAULT.equals(repoLayout))) {
            log.debug(
                    "Skipping maven metadata calculation since repoType '{}' doesn't belong to neither Maven, Ivy, " +
                            "Gradle" +
                            " repositories types.", localRepo.getKey());
            return false;
        }
        return true;
    }

    private boolean isPomFile(VfsItem<?> fsItem) {
        return MavenNaming.isPom(fsItem.getRepoPath().getPath());
    }

    private boolean isMavenPlugin(VfsItem<?> fsItem) {
        Set<String> propertyValues = fsItem.getProperties().get(PropertiesService.MAVEN_PLUGIN_PROPERTY_NAME);
        if (propertyValues == null) {
            return false;
        } else {
            return Boolean.parseBoolean(propertyValues.stream().findFirst().orElse("false"));
        }
    }
}
