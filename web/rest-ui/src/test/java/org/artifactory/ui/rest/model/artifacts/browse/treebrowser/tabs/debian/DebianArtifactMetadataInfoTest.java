package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.debian;

import org.artifactory.addon.debian.DebianInfo;
import org.artifactory.addon.debian.DscInfo;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Priyanshu Gupta
 */
public class DebianArtifactMetadataInfoTest {

    @Test
    public void testDefaultConstructor() {
        DebianArtifactMetadataInfo info = new DebianArtifactMetadataInfo();

        assertNull(info.getDebianInfo());
        assertNull(info.getDebianDependencies());
        assertNull(info.getIndexFailureReason());
        assertNull(info.getDscInfo());
    }

    @Test
    public void testSettersAndGetters() {
        DebianInfo debianInfo = new DebianInfo();
        List<String> dependencies = Arrays.asList("dep1", "dep2");
        String failureReason = "Failed to parse package";
        DscInfo dscInfo = new DscInfo();

        DebianArtifactMetadataInfo info = new DebianArtifactMetadataInfo();
        info.setDebianInfo(debianInfo);
        info.setDebianDependencies(dependencies);
        info.setIndexFailureReason(failureReason);
        info.setDscInfo(dscInfo);

        assertEquals(debianInfo, info.getDebianInfo());
        assertEquals(dependencies, info.getDebianDependencies());
        assertEquals(failureReason, info.getIndexFailureReason());
        assertEquals(dscInfo, info.getDscInfo());
    }

    @Test
    public void testNullValues() {
        DebianArtifactMetadataInfo info = new DebianArtifactMetadataInfo();
        info.setDebianInfo(new DebianInfo());
        info.setDebianDependencies(Arrays.asList("dep1"));
        info.setIndexFailureReason("failure");
        info.setDscInfo(new DscInfo());

        info.setDebianInfo(null);
        assertNull(info.getDebianInfo());

        info.setDebianDependencies(null);
        assertNull(info.getDebianDependencies());

        info.setIndexFailureReason(null);
        assertNull(info.getIndexFailureReason());

        info.setDscInfo(null);
        assertNull(info.getDscInfo());
    }

    @Test
    public void testEmptyDependenciesList() {
        DebianArtifactMetadataInfo info = new DebianArtifactMetadataInfo();
        List<String> emptyDependencies = Arrays.asList();

        info.setDebianDependencies(emptyDependencies);

        assertNotNull(info.getDebianDependencies());
        assertTrue(info.getDebianDependencies().isEmpty());
    }
}
