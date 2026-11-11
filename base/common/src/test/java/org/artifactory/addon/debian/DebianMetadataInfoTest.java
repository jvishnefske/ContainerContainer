package org.artifactory.addon.debian;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;

/**
 * @author Priyanshu Gupta
 */
public class DebianMetadataInfoTest {

    @Test
    public void testDefaultConstructor() {
        DebianMetadataInfo info = new DebianMetadataInfo();

        assertNull(info.getDebianInfo());
        assertNull(info.getDebianDependencies());
        assertNull(info.getFailReason());
        assertNull(info.getDscInfo());
    }

    @Test
    public void testDscInfoConstructor() {
        DscInfo dscInfo = new DscInfo();

        DebianMetadataInfo info = new DebianMetadataInfo(dscInfo);

        assertNull(info.getDebianInfo());
        assertNull(info.getDebianDependencies());
        assertNull(info.getFailReason());
        assertEquals(dscInfo, info.getDscInfo());
    }

    @Test
    public void testFullConstructor() {
        DebianInfo debianInfo = new DebianInfo();
        List<String> dependencies = Arrays.asList("dep1", "dep2", "dep3");
        String failReason = "Test failure reason";

        DebianMetadataInfo info = new DebianMetadataInfo(debianInfo, dependencies, failReason);

        assertEquals(debianInfo, info.getDebianInfo());
        assertEquals(dependencies, info.getDebianDependencies());
        assertEquals(failReason, info.getFailReason());
        assertNull(info.getDscInfo());
    }

    @Test
    public void testSettersAndGetters() {
        DebianInfo debianInfo = new DebianInfo();
        List<String> dependencies = Arrays.asList("dep1", "dep2", "dep3");
        String failReason = "Test failure reason";
        DscInfo dscInfo = new DscInfo();

        DebianMetadataInfo info = new DebianMetadataInfo();
        info.setDebianInfo(debianInfo);
        info.setDebianDependencies(dependencies);
        info.setFailReason(failReason);
        info.setDscInfo(dscInfo);

        assertEquals(debianInfo, info.getDebianInfo());
        assertEquals(dependencies, info.getDebianDependencies());
        assertEquals(failReason, info.getFailReason());
        assertEquals(dscInfo, info.getDscInfo());
    }
}
