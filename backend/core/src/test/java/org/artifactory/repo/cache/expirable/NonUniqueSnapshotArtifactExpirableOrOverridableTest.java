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

import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.LocalCacheRepo;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Noam Y. Tenne
 */
public class NonUniqueSnapshotArtifactExpirableOrOverridableTest {

    private NonUniqueSnapshotArtifactExpirableOrOverridable expirable;

    private static final String ARTIFACT_PATH = "g/a/1.0-SNAPSHOT/artifact-5.4-SNAPSHOT.pom";

    @Test
    public void testIsExpirableOnNoIntegrationModuleInfo() {
        LocalCacheRepo localCacheRepo = getLocalCacheRepoMock();
        expectUnExpirable(localCacheRepo, "momo");
    }

    @Test
    public void testIsExpirable() throws Exception {
        expectExpirable();

        LocalCacheRepo localCacheRepo = getLocalCacheRepoMock();

        expectUnExpirable(localCacheRepo, "g/a/1.0/artifact-5.4-20081214.090217-4.pom");
        expectUnExpirable(localCacheRepo, "g/a/1.0-20081214.090217-4/artifact-5.4-20081214.090217-4.pom");
        expectUnExpirable(localCacheRepo, "g/a/1.0-SNAPSHOT/artifact-5.4-20081214.090217-4.pom");
    }

    private void expectExpirable() {
        LocalCacheRepo localCacheRepo = getLocalCacheRepoMock();
        assertTrue(expirable.isExpirable(RepoType.Maven, "key", ARTIFACT_PATH),
                ARTIFACT_PATH + " Should be expirable.");
        assertTrue(expirable.isExpirable(RepoType.Gradle, "key", ARTIFACT_PATH),
                ARTIFACT_PATH + " Should be expirable.");
        assertTrue(expirable.isExpirable(RepoType.Ivy, "key", ARTIFACT_PATH),
                ARTIFACT_PATH + " Should be expirable.");
        assertTrue(expirable.isExpirable(RepoType.SBT, "key", ARTIFACT_PATH),
                ARTIFACT_PATH + " Should be expirable.");
        assertTrue(expirable.isOverridable(localCacheRepo, ARTIFACT_PATH),
                ARTIFACT_PATH + " Should be overridable.");
    }

    private void expectUnExpirable(LocalCacheRepo localCacheRepo, String artifactPath) {
        assertFalse(expirable.isExpirable(RepoType.Maven, "key", artifactPath),
                artifactPath + " Shouldn't be expirable.");
        assertFalse(expirable.isOverridable(localCacheRepo, artifactPath),
                artifactPath + " Shouldn't be overridable.");
    }

    private LocalCacheRepo getLocalCacheRepoMock() {
        LocalCacheRepo localCacheRepo = Mockito.mock(LocalCacheRepo.class);
        expirable = new NonUniqueSnapshotArtifactExpirableOrOverridable();
        LocalCacheRepoDescriptor descriptor = Mockito.mock(LocalCacheRepoDescriptor.class);
        Mockito.when(descriptor.getType()).thenReturn(RepoType.Maven);
        Mockito.when(localCacheRepo.getDescriptor()).thenReturn(descriptor);
        Mockito.when(localCacheRepo.getKey()).thenReturn("key");
        return localCacheRepo;
    }
}