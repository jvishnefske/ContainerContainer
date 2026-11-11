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

package org.artifactory.search.build;

import com.google.common.collect.Sets;
import com.jfrog.build.acl.bridge.BuildFacade;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildSearchCriteria;
import jakarta.annotation.Nonnull;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.search.ItemSearchResults;
import org.artifactory.api.search.SearchControls;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.search.SearcherBase;

import java.util.Set;

import static com.jfrog.build.acl.model.BuildChecksumType.MD5;
import static com.jfrog.build.acl.model.BuildChecksumType.SHA1;
import static com.jfrog.build.acl.model.BuildChecksumType.SHA256;

/**
 * Holds the build search logic
 *
 * @author Noam Y. Tenne
 */
public class BuildSearcher extends SearcherBase {

    /**
     * Locates builds with deployed artifacts that have the given checksum
     *
     * @param sha1 SHA1 checksum to search for. Can be blank.
     * @param md5  MD5 checksum to search for. Can be blank.
     * @return List of basic build infos that deployed at least one artifact with the given checksum
     */
    public Set<BuildRun> findBuildsByArtifactChecksum(
            String sha1,
            String sha2,
            String md5,
            @Nonnull String buildRepo) {
        return findBuildsByItemChecksum(BuildSearchCriteria.IN_ARTIFACTS, sha1, sha2, md5, buildRepo);
    }

    /**
     * Locates builds with dependencies that have the given checksum
     *
     * @param sha1 SHA1 checksum to search for. Can be blank.
     * @param md5  MD5 checksum to search for. Can be blank.
     * @return List of basic build infos that depend on the artifact with the given checksum
     */
    public Set<BuildRun> findBuildsByDependencyChecksum(
            String sha1,
            String sha2,
            String md5,
            @Nonnull String buildRepo) {
        return findBuildsByItemChecksum(BuildSearchCriteria.IN_DEPENDENCIES, sha1, sha2, md5, buildRepo);
    }

    /**
     * DO NOT USE - NOT IMPLEMENTED
     */
    @Override
    public ItemSearchResults doSearch(SearchControls controls) {
        return null;
    }

    /**
     * Locates builds that produced or depended on an item with the given checksum and adds them to the given list
     *
     * @param criteria Where the checksum should be search for (dependencies or artifacts)
     * @param sha1     SHA1 checksum value
     * @param md5      MD5 checksum value
     */
    private Set<BuildRun> findBuildsByItemChecksum(
            BuildSearchCriteria criteria,
            String sha1,
            String sha2,
            String md5,
            @Nonnull String buildRepo) {
        Set<BuildRun> results = Sets.newHashSet();
        boolean validSha1 = ChecksumType.sha1.isValid(sha1);
        boolean validSha2 = ChecksumType.sha256.isValid(sha2);
        boolean validMd5 = ChecksumType.md5.isValid(md5);
        if (!validSha1 && !validMd5 && !validSha2) {
            return results;
        }
        BuildFacade buildFacade = ContextHelper.get().beanForType(BuildFacade.class);
        if (validSha1) {
            results.addAll(buildFacade.findBuildsForChecksum(criteria, SHA1, sha1, buildRepo));
        }
        if (validSha2) {
            results.addAll(buildFacade.findBuildsForChecksum(criteria, SHA256, sha2, buildRepo));
        }
        if (validMd5) {
            results.addAll(buildFacade.findBuildsForChecksum(criteria, MD5, md5, buildRepo));
        }
        return results;
    }
}