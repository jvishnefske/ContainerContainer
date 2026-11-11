package org.artifactory.ph;

import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.testng.annotations.Test;

import java.util.EnumSet;

import static org.artifactory.ph.PackageRepoTypeMapper.EXCLUDED_REPO_TYPES;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

@Test
public class PackageRepoTypeMapperTest {

    @Test
    public void testPackageType() {
        for (RepoType rt : EnumSet.complementOf(EXCLUDED_REPO_TYPES)) {
            PackageType pt = PackageRepoTypeMapper.packageType(rt);
            assertNotNull(pt,
                    "No " + PackageType.class.getSimpleName() + " defined to " + RepoType.class.getSimpleName() + ": " +
                            rt);
            assertEquals(pt, PackageType.valueOf(rt.name().toUpperCase()));
        }
    }

    @Test
    public void testExcludedPackages() {
        for (RepoType rt : EXCLUDED_REPO_TYPES) {
            PackageType pt = PackageRepoTypeMapper.packageType(rt);
            assertNull(pt, rt + " should return null");
        }
    }

    @Test
    public void testTestPackageType() {
        RepoConfig config = mock(RepoConfig.class);
        when(config.getPackageType()).thenReturn(RepoType.CRAN);
        assertEquals(PackageRepoTypeMapper.packageType(config), PackageType.CRAN);
    }

    @Test
    public void testSystemRepoToPackageType() {
        RepoConfig config = mock(RepoConfig.class);
        when(config.getPackageType()).thenReturn(RepoType.ReleaseBundles);
        assertEquals(PackageRepoTypeMapper.packageType(config), PackageType.RELEASEBUNDLES);
    }

    @Test
    public void testUnmappedRepoTypeReturnsNull() {
        RepoType repoType = mock(RepoType.class);
        when(repoType.getType()).thenReturn("unknown-package-type");
        assertNull(PackageRepoTypeMapper.packageType(repoType));
    }
}