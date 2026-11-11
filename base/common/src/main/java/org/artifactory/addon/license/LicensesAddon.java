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

package org.artifactory.addon.license;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.artifactory.common.addon.Addon;
import org.artifactory.api.license.LicenseInfo;
import org.artifactory.api.license.LicensesInfo;
import org.artifactory.api.license.ModuleLicenseModel;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.artifactory.descriptor.property.PropertySet.ARTIFACTORY_RESERVED_PROP_SET;

/**
 * @author Tomer Cohen
 */
public interface LicensesAddon extends Addon {

    String LICENSES_PROP_NAME = "licenses"; //Represents the license's id
    String LICENSES_PROP_FULL_NAME = ARTIFACTORY_RESERVED_PROP_SET + "." + LICENSES_PROP_NAME;
    String LICENSES_UNKNOWN_PREFIX = "unknown";
    //Represents the license's name (for unknown licenses)
    String LICENSES_UNKNOWN_PROP_NAME = LICENSES_PROP_NAME + "." + LICENSES_UNKNOWN_PREFIX + ".name";
    String LICENSES_UNKNOWN_PROP_FULL_NAME = ARTIFACTORY_RESERVED_PROP_SET + "." + LICENSES_UNKNOWN_PROP_NAME;

    /**
     * Import licenses as part of the full system import.
     */
    default void importLicenses(ImportSettings settings) {
    }

    /**
     * Export licenses as part of the full system export.
     */
    default void exportLicenses(ExportSettings exportSettings) {
    }

    default List<ModuleLicenseModel> findLicensesInRepos(Set<String> repoKeys, LicenseStatus status) {
        return Lists.newArrayList();
    }

    default LicensesInfo getArtifactsLicensesInfo() {
        return null;
    }

    default String writeLicenseXML(LicensesInfo licensesInfo) {
        return null;
    }

    default void addLicenseInfo(LicenseInfo licensesInfo) {
    }

    default void updateLicenseInfo(LicenseInfo licensesInfo) {
    }

    default void deleteLicenseInfo(LicenseInfo licensesInfo) {
    }

    default LicenseInfo getLicenseByName(String licenseName) {
        return new LicenseInfo();
    }

    default void reloadLicensesCache() {
    }

    default String generateLicenseCsv(Collection<ModuleLicenseModel> models) {
        return null;
    }

    default boolean setLicensePropsOnPath(RepoPath path, Set<LicenseInfo> licenses) {
        return false;
    }

    default void setLicensePropsOnPath(RepoPath path, String... licenseNames) {
    }

    /**
     * Used by the UI to scan path for licenses - returns all licenses for path, property-based and
     * descriptor-retrieved (extracted). Returns a distinct list of licenses (i.e. no duplicates that were found both
     * in properties and extracted).
     */
    default Set<LicenseInfo> scanPathForLicenses(RepoPath path) {
        return Sets.newHashSet();
    }

    /**
     * Used by the UI to retrieve property-based licenses to show
     */
    default Set<LicenseInfo> getPathLicensesByProps(RepoPath path) {
        return null;
    }
}
