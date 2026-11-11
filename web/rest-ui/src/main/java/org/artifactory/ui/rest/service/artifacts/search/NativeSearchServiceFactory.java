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

package org.artifactory.ui.rest.service.artifacts.search;

import org.artifactory.ui.rest.service.artifacts.search.packagesearch.CountNativePackagesService;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.PackageNativeDockerSearchService;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.PackageNativeExtraInfoService;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.PackageNativeSearchService;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.PackageNativeSummaryExtraInfoService;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.PackageNativeSummaryService;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.VersionNativeExtraInfoService;
import org.artifactory.ui.rest.service.artifacts.search.reposearch.RepoNativeSearchService;
import org.artifactory.ui.rest.service.artifacts.search.versionsearch.VersionNativeDockerListSearchService;
import org.artifactory.ui.rest.service.artifacts.search.versionsearch.VersionNativeGetBuildsService;
import org.artifactory.ui.rest.service.artifacts.search.versionsearch.VersionNativeGetPropsService;
import org.artifactory.ui.rest.service.artifacts.search.versionsearch.VersionNativeListSearchService;
import org.artifactory.ui.rest.service.artifacts.search.versionsearch.VersionNativeSummaryExtraInfoService;
import org.artifactory.ui.rest.service.artifacts.search.versionsearch.VersionNativeSummaryService;
import org.springframework.beans.factory.annotation.Lookup;

/**
 * @author Lior Gur
 */
public abstract class NativeSearchServiceFactory {

    @Lookup
    public abstract RepoNativeSearchService repoNativeSearchService();

    @Lookup
    public abstract PackageNativeSearchService packageNativeSearchService();

    @Lookup
    public abstract PackageNativeSummaryService packageNativeSummaryService();

    @Lookup
    public abstract PackageNativeSummaryExtraInfoService packageNativeSummaryExtraInfoService();

    @Lookup
    public abstract PackageNativeExtraInfoService packageNativeExtraInfoService();

    @Lookup
    public abstract ShowExtraInfoService getDbTypeService();

    @Lookup
    public abstract CountNativePackagesService countNativePackagesService();

    @Lookup
    public abstract VersionNativeListSearchService versionNativeListSearchService();

    @Lookup
    public abstract VersionNativeSummaryService versionNativeSummaryService();

    @Lookup
    public abstract VersionNativeSummaryExtraInfoService versionNativeSummaryExtraInfoService();

    @Lookup
    public abstract VersionNativeGetBuildsService versionNativeGetBuildsService();

    @Lookup
    public abstract VersionNativeGetPropsService versionNativeGetPropsService();

    @Lookup
    public abstract VersionNativeExtraInfoService versionNativeExtraInfoService();

    //Docker
    @Lookup
    public abstract PackageNativeDockerSearchService packageNativeDockerSearchService();

    @Lookup
    public abstract NativeDockerTotalDownloadSearchService nativeDockerTotalDownloadSearchService();

    @Lookup
    public abstract PackageNativeDockerExtraInfoService packageNativeDockerExtraInfoService();

    @Lookup
    public abstract VersionNativeDockerListSearchService versionNativeDockerListSearchService();
}
