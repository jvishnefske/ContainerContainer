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

package org.artifactory.maven;

import com.jfrog.ph.acl.metrics.PackageIndexMetrics;
import com.jfrog.ph.acl.metrics.PackageIndexMetrics.Trigger;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.api.maven.MavenMetadataPluginWorkItem;
import org.artifactory.api.maven.MavenMetadataService;
import org.artifactory.api.maven.MavenMetadataWorkItem;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.util.RepoLayoutUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * A service for calculating maven metadata.
 *
 * @author Yossi Shaul
 */
@Slf4j
@Service
public class MavenMetadataServiceImpl implements MavenMetadataService {

    private InternalRepositoryService repoService;
    private AddonsManager addonsManager;
    /**
     * Optional. Provided by the package-handler-base Spring config when running inside the full Artifactory
     * application; absent in lighter-weight unit-test contexts. When {@code null}, no indexing metrics are emitted.
     */
    @Nullable
    private PackageIndexMetrics packageIndexMetrics;

    @Autowired
    public void setRepoService(@NonNull InternalRepositoryService repoService) {
        this.repoService = repoService;
    }

    @NonNull
    public InternalRepositoryService getRepoService() {
        return repoService;
    }

    @Autowired
    public void setAddonsManager(@NonNull AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @NonNull
    public AddonsManager getAddonsManager() {
        return addonsManager;
    }

    @Autowired(required = false)
    public void setPackageIndexMetrics(@Nullable PackageIndexMetrics packageIndexMetrics) {
        this.packageIndexMetrics = packageIndexMetrics;
    }

    private static MavenMetadataService getTransactionalMe() {
        return InternalContextHelper.get().beanForType(MavenMetadataService.class);
    }

    @Override
    public void calculateMavenMetadataAsync(MavenMetadataWorkItem workItem) {
        calculateMavenMetadata(workItem);
    }

    @Override
    public void calculateMavenMetadata(MavenMetadataWorkItem workItem) {
        RepoPath baseFolderPath = workItem.getRepoPath();
        if (baseFolderPath == null) {
            log.debug("Couldn't find repo for null repo path.");
            return;
        }
        LocalRepo localRepo = repoService.localOrFederatedRepositoryByKey(baseFolderPath.getRepoKey());
        if (localRepo == null) {
            log.debug("Couldn't find local non-cache repository for path '{}'.", baseFolderPath);
            return;
        }
        log.debug("Calculate maven metadata on {}", baseFolderPath);

        RepoLayout repoLayout = localRepo.getDescriptor().getRepoLayout();
        RepoType type = localRepo.getDescriptor().getType();
        ArtifactoryRunningMode runningModel = addonsManager.getArtifactoryRunningMode();
        // Do not calculate maven metadata if type == null or type doesn't belong to the maven group (Maven, Ivy,
        //Gradle) or repoLayout not equals MAVEN_2_DEFAULT
        if (skipCalculation(type, repoLayout, runningModel)) {
            log.debug("Skipping maven metadata calculation since repoType '{}' doesn't belong to " +
                    "neither Maven, Ivy, Gradle repositories types.", baseFolderPath.getRepoKey());
            return;
        }

        getMavenMetadataCalculator(workItem, type).calculate();

        if (workItem.isMavenPlugin()) {
            // Calculate maven plugins metadata asynchronously
            getTransactionalMe()
                    .calculateMavenPluginsMetadataAsync(new MavenMetadataPluginWorkItem(localRepo.getKey()));
            log.debug("Calculated maven plugin metadata for '{}'.", baseFolderPath);
        }
    }

    @NonNull
    MavenMetadataCalculator getMavenMetadataCalculator(@NonNull MavenMetadataWorkItem workItem,
            @Nullable RepoType repoType) {
        Trigger trigger = workItem.isReindex() ? Trigger.REINDEX : Trigger.INDEX;
        return new MavenMetadataCalculator(workItem.getRepoPath(), workItem.isRecursive(),
                toPackageType(repoType), trigger, packageIndexMetrics);
    }

    /**
     * Maps a Maven-group {@link RepoType} to its {@link PackageType} for metric tagging. Returns {@code null}
     * for non-Maven-group (or {@code null}) types so that legacy MAVEN_2_DEFAULT-layout calculator runs on
     * Generic/Docker/etc. repos don't pollute the {@code packageType} metric dimension.
     */
    @Nullable
    static PackageType toPackageType(@Nullable RepoType repoType) {
        if (repoType == null || !repoType.isMavenGroup()) {
            return null;
        }
        try {
            return PackageType.valueOf(repoType.name().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.debug("No PackageType mapping for repoType {} -- maven indexing metrics will be skipped", repoType);
            return null;
        }
    }

    protected boolean skipCalculation(RepoType type, RepoLayout repoLayout, ArtifactoryRunningMode runningModel) {
        boolean licenseExcludeMaven = runningModel.isJCR() || runningModel.isConan();
        boolean isMavenLayout2Allowed = !licenseExcludeMaven && RepoLayoutUtils.MAVEN_2_DEFAULT.equals(repoLayout);
        return (type != null && !(type.isMavenGroup() || isMavenLayout2Allowed));
    }

    // get all folders marked for maven metadata calculation and execute the metadata calculation
    @Override
    public void calculateMavenPluginsMetadataAsync(MavenMetadataPluginWorkItem workItem) {
        LocalRepo localRepo = localRepositoryByKeyFailIfNull(
                InternalRepoPathFactory.repoRootPath(workItem.getLocalRepo()));
        new MavenPluginsMetadataCalculator().calculate(localRepo);
    }

    private LocalRepo localRepositoryByKeyFailIfNull(RepoPath localRepoPath) {
        LocalRepo localRepo = repoService.localOrFederatedRepositoryByKey(localRepoPath.getRepoKey());
        if (localRepo == null) {
            throw new IllegalArgumentException("Couldn't find local non-cache repository for path " + localRepoPath);
        }
        return localRepo;
    }
}
