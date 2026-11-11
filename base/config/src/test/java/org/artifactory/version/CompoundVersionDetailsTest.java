package org.artifactory.version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.HashMap;
import java.util.Map;

/**
 * @author BarakH
 */
public class CompoundVersionDetailsTest {

    @Test
    public void testVersionToString() {
        CompoundVersionDetails compoundVersionDetails =
                new CompoundVersionDetails(ArtifactoryServiceVersion.values()[0], "100", 100L);
        Assertions.assertEquals("1.2.2-rc0", compoundVersionDetails.getVersion().toString());
    }

    @Test
    public void getMfeVersion_shouldReturnValue_whenPresent() {
        Map<String, String> mfeVersions = new HashMap<>();
        mfeVersions.put("core", "1.0.0");

        CompoundVersionDetails details = new CompoundVersionDetails(
                ArtifactoryServiceVersion.values()[0],
                "",
                "100",
                100L,
                mfeVersions
        );

        Assertions.assertEquals("1.0.0", details.getMfeVersion("core"));
    }

    @Test
    public void getMfeVersion_shouldReturnNull_whenNotPresent() {
        CompoundVersionDetails details = new CompoundVersionDetails(
                ArtifactoryServiceVersion.values()[0],
                "",
                "100",
                100L,
                new HashMap<>()
        );

        Assertions.assertNull(details.getMfeVersion("rtui"));
    }

    @Test
    public void getMfeVersion_shouldReturnNull_whenCoreNotPresent() {
        CompoundVersionDetails details = new CompoundVersionDetails(
                ArtifactoryServiceVersion.values()[0],
                "",
                "100",
                100L,
                new HashMap<>()
        );

        Assertions.assertNull(details.getMfeVersion("core"));
    }

    @Test
    public void getMfeVersions_shouldReturnUnmodifiableMap_whenAccessed() {
        Map<String, String> mfeVersions = new HashMap<>();
        mfeVersions.put("core", "1.0.0");

        CompoundVersionDetails details = new CompoundVersionDetails(
                ArtifactoryServiceVersion.values()[0],
                "",
                "100",
                100L,
                mfeVersions
        );

        Assertions.assertThrows(UnsupportedOperationException.class,
                () -> details.getMfeVersions().put("rtui", "2.0.0"));
    }

    @Test
    public void getVersionName_shouldReturnVersionString() {
        CompoundVersionDetails details = new CompoundVersionDetails(
                ArtifactoryServiceVersion.values()[0],
                "100",
                100L
        );

        Assertions.assertEquals("1.2.2-rc0", details.getVersionName());
    }

    @Test
    public void getRevision_shouldReturnRevisionFromVersion() {
        CompoundVersionDetails details = new CompoundVersionDetails(
                ArtifactoryServiceVersion.values()[0],
                "100",
                100L
        );

        Assertions.assertEquals(ArtifactoryServiceVersion.values()[0].getRevision(), details.getRevision());
    }

    @Test
    public void isCurrent_shouldDelegateToVersion() {
        CompoundVersionDetails details = new CompoundVersionDetails(
                ArtifactoryServiceVersion.getCurrent(),
                "100",
                100L
        );

        Assertions.assertTrue(details.isCurrent());
    }

    @Test
    public void before_shouldReturnTrue_whenThisVersionBeforeOther() {
        CompoundVersionDetails older = new CompoundVersionDetails(
                ArtifactoryServiceVersion.values()[0],
                "100",
                100L
        );
        CompoundVersionDetails newer = new CompoundVersionDetails(
                ArtifactoryServiceVersion.getCurrent(),
                "200",
                200L
        );

        Assertions.assertTrue(older.before(newer));
    }

    @Test
    public void getFileDump_shouldIncludePackageHandlerVersion_whenNotEmpty() {
        CompoundVersionDetails details = new CompoundVersionDetails(
                ArtifactoryServiceVersion.values()[0],
                "ph-1.0",
                "100",
                100L,
                new HashMap<>()
        );

        String dump = details.getFileDump();

        Assertions.assertTrue(dump.contains("artifactory.package.handler.version=ph-1.0"));
        Assertions.assertTrue(dump.contains("artifactory.version="));
        Assertions.assertTrue(dump.contains("artifactory.buildNumber=100"));
        Assertions.assertTrue(dump.contains("artifactory.timestamp=100"));
    }

    @Test
    public void getFileDump_shouldIncludeMfeVersions_whenPresent() {
        Map<String, String> mfeVersions = new HashMap<>();
        mfeVersions.put("core", "1.0.0");
        mfeVersions.put("rtui", "2.0.0");

        CompoundVersionDetails details = new CompoundVersionDetails(
                ArtifactoryServiceVersion.values()[0],
                "",
                "100",
                100L,
                mfeVersions
        );

        String dump = details.getFileDump();

        Assertions.assertTrue(dump.contains("artifactory.core.mfe.version=1.0.0"));
        Assertions.assertTrue(dump.contains("artifactory.rtui.mfe.version=2.0.0"));
    }

    @Test
    public void constructor_shouldAcceptNullMfeVersions() {
        CompoundVersionDetails details = new CompoundVersionDetails(
                ArtifactoryServiceVersion.values()[0],
                "",
                "100",
                100L,
                null
        );

        Assertions.assertTrue(details.getMfeVersions().isEmpty());
    }
}