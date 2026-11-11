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

package org.artifactory.maven.snapshot;

import org.artifactory.storage.fs.tree.ItemNode;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests for BuildNumberSnapshotComparator — covers RTDEV-90369 non-throwing behaviour.
 */
@Test
public class BuildNumberSnapshotComparatorTest {

    private final BuildNumberSnapshotComparator comparator = BuildNumberSnapshotComparator.get();

    public void compareItemNodesWithValidUniqueSnapshotFilenamesReturnsCorrectOrder() {
        ItemNode node1 = mock(ItemNode.class);
        when(node1.getName()).thenReturn("artifact-1.0-20081214.090217-3.pom");
        ItemNode node2 = mock(ItemNode.class);
        when(node2.getName()).thenReturn("artifact-1.0-20081214.090217-5.pom");

        int result = comparator.compare(node1, node2);
        assertTrue(result < 0, "Build 3 should sort before build 5");
    }

    public void compareItemNodesWithNonUniqueSnapshotFilenamesShouldNotThrow() {
        ItemNode node1 = mock(ItemNode.class);
        when(node1.getName()).thenReturn("maven-metadata.xml");
        ItemNode node2 = mock(ItemNode.class);
        when(node2.getName()).thenReturn("maven-metadata.xml.md5");

        int result = comparator.compare(node1, node2);
        assertEquals(result, 0, "Non-snapshot filenames compare as equal");
    }

    public void compareItemNodesMixedSnapshotAndNonSnapshotShouldNotThrow() {
        ItemNode snapshotNode = mock(ItemNode.class);
        when(snapshotNode.getName()).thenReturn("artifact-1.0-20081214.090217-4.pom");
        ItemNode nonSnapshotNode = mock(ItemNode.class);
        when(nonSnapshotNode.getName()).thenReturn("artifact-1.0.pom");

        int result = comparator.compare(snapshotNode, nonSnapshotNode);
        assertTrue(result > 0, "Non-snapshot node sorts before snapshot node");
    }

    public void compareItemNodesBothReleaseArtifactsShouldNotThrow() {
        ItemNode node1 = mock(ItemNode.class);
        when(node1.getName()).thenReturn("artifact-1.0-sources.jar");
        ItemNode node2 = mock(ItemNode.class);
        when(node2.getName()).thenReturn("artifact-1.0.pom");

        int result = comparator.compare(node1, node2);
        assertEquals(result, 0);
    }
}
