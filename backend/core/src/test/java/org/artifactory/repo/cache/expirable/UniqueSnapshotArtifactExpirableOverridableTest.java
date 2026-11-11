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

package org.artifactory.repo.cache.expirable;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.common.RepoPathImpl;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.artifactory.util.RepoLayoutUtils.MAVEN_2_DEFAULT;
import static org.artifactory.util.RepoLayoutUtils.SIMPLE_DEFAULT;

/**
 * @author dudim
 */
public class UniqueSnapshotArtifactExpirableOverridableTest {

    @Test
    public void testIsExpirableValid() {
        UniqueSnapshotArtifactExpirableOverridable uniqueSnapshotArtifactExpirableOverridable =
                new UniqueSnapshotArtifactExpirableOverridable();
        Assert.assertTrue(uniqueSnapshotArtifactExpirableOverridable
                .isExpirable(RepoType.Maven, MAVEN_2_DEFAULT,
                        new RepoPathImpl("maven", "parent-SNAPSHOT/package-SNAPSHOT.jar")));
    }

    @Test
    public void testIsExpirableFalseWhenNullRepoType() {
        UniqueSnapshotArtifactExpirableOverridable uniqueSnapshotArtifactExpirableOverridable =
                new UniqueSnapshotArtifactExpirableOverridable();
        Assert.assertFalse(uniqueSnapshotArtifactExpirableOverridable
                .isExpirable(null, MAVEN_2_DEFAULT,
                        new RepoPathImpl("maven", "package-SNAPSHOT.jar")));
    }

    @Test
    public void testIsExpirableFalseWhenNotMavenRepoLayout() {
        UniqueSnapshotArtifactExpirableOverridable uniqueSnapshotArtifactExpirableOverridable =
                new UniqueSnapshotArtifactExpirableOverridable();
        Assert.assertFalse(uniqueSnapshotArtifactExpirableOverridable
                .isExpirable(RepoType.Bower, SIMPLE_DEFAULT,
                        new RepoPathImpl("maven", "package-SNAPSHOT.jar")));
    }

    @Test
    public void testIsExpirableFalseWhenNonUniqueSnapshot() {
        UniqueSnapshotArtifactExpirableOverridable uniqueSnapshotArtifactExpirableOverridable =
                new UniqueSnapshotArtifactExpirableOverridable();
        Assert.assertFalse(uniqueSnapshotArtifactExpirableOverridable
                .isExpirable(RepoType.Maven, MAVEN_2_DEFAULT, new RepoPathImpl("maven", "package.jar")));
    }
}