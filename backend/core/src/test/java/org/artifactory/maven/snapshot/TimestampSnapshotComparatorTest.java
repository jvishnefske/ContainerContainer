package org.artifactory.maven.snapshot;

import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.artifactory.storage.fs.tree.ItemNode;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

import static org.testng.Assert.assertEquals;

/**
 * @author uriahl
 */
@Test
public class TimestampSnapshotComparatorTest {

    private final TimestampSnapshotComparator timestampSnapshotComparator = new TimestampSnapshotComparator();

    public void testTimestampComparatorVersions() {
        SnapshotVersion newerSnapshotVersion = new SnapshotVersion();
        newerSnapshotVersion.setUpdated("20170510085558");
        newerSnapshotVersion.setVersion("1.9.2.3-XWT-20170510.085558-2");
        newerSnapshotVersion.setExtension("pom");
        SnapshotVersion olderSnapshotVersion = new SnapshotVersion();
        olderSnapshotVersion.setUpdated("20170510084024");
        olderSnapshotVersion.setVersion("1.9.2.3-XWT-20170510.084024-1");
        olderSnapshotVersion.setExtension("pom");
        assertEquals(timestampSnapshotComparator.compare(newerSnapshotVersion, olderSnapshotVersion), 1,
                "20170510085558 should be > 20170510084024");
    }

    public void testTimestampSnapshotComparatorWithDottedUpdated() {
        SnapshotVersion newerSnapshotVersion = new SnapshotVersion();
        // Doesn't happen in real life but test to make sure we don't fail with formatting errors
        newerSnapshotVersion.setUpdated("20170510.085558");
        newerSnapshotVersion.setVersion("1.9.2.3-XWT-20170510.085558-2");
        newerSnapshotVersion.setExtension("pom");
        SnapshotVersion olderSnapshotVersion = new SnapshotVersion();
        olderSnapshotVersion.setUpdated("20170510.084024");
        olderSnapshotVersion.setVersion("1.9.2.3-XWT-20170510.084024-1");
        olderSnapshotVersion.setExtension("pom");
        assertEquals(timestampSnapshotComparator.compare(newerSnapshotVersion, olderSnapshotVersion), 1,
                "20170510085558 should be > 20170510084024");
    }

    public void testFallbackToVersionWhenUpdatedIsMissing() {
        SnapshotVersion newerSnapshotVersion = new SnapshotVersion();
        newerSnapshotVersion.setUpdated("");
        newerSnapshotVersion.setVersion("1.9.2.3-20170510.085558-2");
        newerSnapshotVersion.setExtension("pom");
        SnapshotVersion olderSnapshotVersion = new SnapshotVersion();
        olderSnapshotVersion.setUpdated(null);
        olderSnapshotVersion.setVersion("1.9.2.3-20170510.084024-1");
        olderSnapshotVersion.setExtension("pom");
        assertEquals(timestampSnapshotComparator.compare(newerSnapshotVersion, olderSnapshotVersion), 1,
                "20170510085558 should be > 20170510084024");
    }

    public void compareItemNodesWithNonUniqueSnapshotFilenamesShouldNotThrow() {
        ItemNode node1 = mock(ItemNode.class);
        when(node1.getName()).thenReturn("maven-metadata.xml");
        ItemNode node2 = mock(ItemNode.class);
        when(node2.getName()).thenReturn("artifact-1.0.jar");

        int result = timestampSnapshotComparator.compare(node1, node2);
        assertEquals(result, 0, "Non-snapshot filenames compare as equal");
    }

    public void compareItemNodesMixedSnapshotAndNonSnapshotShouldNotThrow() {
        ItemNode snapshotNode = mock(ItemNode.class);
        when(snapshotNode.getName()).thenReturn("artifact-1.0-20081214.090217-4.pom");
        ItemNode nonSnapshotNode = mock(ItemNode.class);
        when(nonSnapshotNode.getName()).thenReturn("artifact-1.0.pom");

        int result = timestampSnapshotComparator.compare(snapshotNode, nonSnapshotNode);
        assertTrue(result > 0, "Non-snapshot node sorts before snapshot node");
    }

    public void compareItemNodesWithValidTimestampFilenamesReturnsCorrectOrder() {
        ItemNode node1 = mock(ItemNode.class);
        when(node1.getName()).thenReturn("artifact-1.0-20081214.090217-1.pom");
        ItemNode node2 = mock(ItemNode.class);
        when(node2.getName()).thenReturn("artifact-1.0-20081215.090217-2.pom");

        int result = timestampSnapshotComparator.compare(node1, node2);
        assertTrue(result < 0, "Earlier timestamp should sort first");
    }
}
