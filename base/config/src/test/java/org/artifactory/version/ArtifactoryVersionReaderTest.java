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

package org.artifactory.version;

import ch.qos.logback.classic.Level;
import org.artifactory.test.TestUtils;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * Tests the ArtifactoryVersionReader.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryVersionReaderTest {

    @Test
    public void readFromFile_shouldResolveVersionWithoutTimestamp_whenPropertiesHaveNoTimestamp() {
        File file = ResourceUtils.getResourceAsFile("/version/artifactory1.2.5.properties");
        CompoundVersionDetails version = ArtifactoryVersionReader.readFromFileAndFindVersion(file);
        Assertions.assertNotNull(version, "Version should have been resolved");
        Assertions.assertEquals(ArtifactoryServiceVersionProvider.v125.get(), version.getVersion(),
                "Unexpected version");
        Assertions.assertEquals(0, version.getTimestamp(), "Unexpected timestamp");
    }

    @Test
    public void readFromStream_shouldResolveVersionWithoutTimestamp_whenPropertiesHaveNoTimestamp() {
        String resName = "/version/artifactory1.2.5.properties";
        InputStream propertiesFileStream = ResourceUtils.getResource(resName);
        CompoundVersionDetails version =
                ArtifactoryVersionReader.readAndFindServiceVersion(propertiesFileStream, resName);
        Assertions.assertNotNull(version, "Version should have been resolved");
        Assertions.assertEquals(ArtifactoryServiceVersionProvider.v125.get(), version.getVersion(),
                "Unexpected version");
    }

    @Test
    public void readFromStream_shouldParseVersionAndTimestamp_whenTimestampPresent() {
        InputStream in = createInputStream("9.5", Integer.MAX_VALUE - 1, 789456);
        CompoundVersionDetails version =
                ArtifactoryVersionReader.readAndFindServiceVersion(in,
                        "readFromStream_shouldParseVersionAndTimestamp_whenTimestampPresent");
        Assertions.assertNotNull(version, "Version should have been resolved");
        Assertions.assertEquals("9.5", version.getVersion().getVersion(), "Unexpected version");
        Assertions.assertEquals(789456, version.getTimestamp(), "Unexpected version");
    }

    @Test
    public void readAndFindProductVersion_shouldReturnProvidedVersionAndRevision_whenNoOverride() {
        InputStream in = createProductVersionInputStream("9.5.1", 200, 789456);
        var version =
                ArtifactoryVersionReader.readAndFindProductVersion(in, "ARTIFACTORY_PRODUCT_VERSION_PROPERTIES_FILE");
        Assertions.assertEquals("9.5.1", version.getVersion().getVersion(), "Unexpected version");
        Assertions.assertEquals(200, version.getVersion().getRevision(), "Unexpected version");
    }

    @Test
    public void overrideVersion_shouldReplaceVersionAndComputeRevision_whenOverrideProvided() {
        InputStream in = createProductVersionInputStream("8.5.1", Integer.MAX_VALUE - 1, 400);
        ArtifactoryVersionReader.VersionPropertiesContent
                versionProps = ArtifactoryVersionReader.readProductPropsContent(in,
                        "overrideVersion_shouldReplaceVersionAndComputeRevision_whenOverrideProvided");
        ArtifactoryVersionReader.overrideVersion("9.5.2", versionProps);
        Assertions.assertEquals("9.5.2", versionProps.versionString, "Unexpected version");
        Assertions.assertEquals("90502900", versionProps.revisionString, "Unexpected revision");
    }

    @Test
    public void overrideVersion_shouldSetSnapshotRevisionToMaxInt_whenSnapshotOverrideProvided() {
        InputStream in = createProductVersionInputStream("8.5.1", 1111, 400);
        ArtifactoryVersionReader.VersionPropertiesContent
                versionProps = ArtifactoryVersionReader.readProductPropsContent(in,
                        "overrideVersion_shouldSetSnapshotRevisionToMaxInt_whenSnapshotOverrideProvided");
        ArtifactoryVersionReader.overrideVersion("9.5.2-SNAPSHOT", versionProps);
        Assertions.assertEquals("9.5.2-SNAPSHOT", versionProps.versionString, "Unexpected version");
        Assertions.assertEquals(String.valueOf(Integer.MAX_VALUE), versionProps.revisionString,
                "Unexpected revision");
    }

    @Test
    public void overrideVersion_shouldKeepOriginalValues_whenNoOverrideProvided() {
        InputStream in = createProductVersionInputStream("9.5.1", 500, 789456);
        ArtifactoryVersionReader.VersionPropertiesContent
                versionProps = ArtifactoryVersionReader.readProductPropsContent(in,
                        "overrideVersion_shouldKeepOriginalValues_whenNoOverrideProvided");
        ArtifactoryVersionReader.overrideVersion(null, versionProps);
        Assertions.assertEquals("9.5.1", versionProps.versionString, "Unexpected version");
        Assertions.assertEquals("500", versionProps.revisionString, "Unexpected revision");
    }

    @Test
    public void readFromFileAndFindVersion_shouldThrowIllegalArgumentException_whenInputIsNull() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> ArtifactoryVersionReader.readFromFileAndFindVersion(null));
    }

    @Test
    public void readAndFindServiceVersion_shouldReturnCurrentWhenInputMatchesCurrent() {
        ArtifactoryServiceVersion current = ArtifactoryServiceVersion.getCurrent();
        InputStream in = createInputStream(current.getVersion(), current.getRevision());
        CompoundVersionDetails result = ArtifactoryVersionReader.readAndFindServiceVersion(in,
                "readAndFindServiceVersion_shouldReturnCurrentWhenInputMatchesCurrent");
        Assertions.assertEquals(current, result.getVersion());
    }

    @Test
    public void readAndFindServiceVersion_shouldTreatDevelopmentAsCurrent_whenProjectVersionPlaceholderPresent() {
        InputStream in = createInputStream("${project.version}", 123);
        CompoundVersionDetails result =
                ArtifactoryVersionReader.readAndFindServiceVersion(in,
                        "readAndFindServiceVersion_shouldTreatDevelopmentAsCurrent_"
                                + "whenProjectVersionPlaceholderPresent");
        Assertions.assertEquals(ArtifactoryServiceVersion.getCurrent(), result.getVersion(),
                "Development version should always be the current version");
    }

    @Test
    public void readServicePropsContent_shouldCollectMfeVersions_whenPropertiesContainMfeEntries() {
        InputStream in = new ByteArrayInputStream((
                "artifactory.version=7.0.0\n"
                + "artifactory.revision=1\n"
                + "artifactory.core.mfe.version=1.2.3\n"
                + "artifactory.rtui.mfe.version=2.0.0\n"
                + "artifactory.not.mfe.version=ignored\n").getBytes());

        Set<String> knownMfeNames = Set.of("core", "rtui");
        ArtifactoryVersionReader.VersionPropertiesContent content =
                ArtifactoryVersionReader.readServicePropsContent(in, "mfeProps", knownMfeNames);

        Map<String, String> mfeVersions = content.mfeVersions;
        Assertions.assertEquals(2, mfeVersions.size(), "Unexpected number of MFE versions");
        Assertions.assertEquals("1.2.3", mfeVersions.get("core"), "Unexpected core MFE version");
        Assertions.assertEquals("2.0.0", mfeVersions.get("rtui"), "Unexpected rtui MFE version");
        Assertions.assertFalse(mfeVersions.containsKey("not"), "Unexpected non-MFE entry");
    }

    @Test
    public void readServicePropsContent_shouldCollectAllMfeVersions_whenKnownMfeNamesIsNull() {
        InputStream in = new ByteArrayInputStream((
                "artifactory.version=7.0.0\n"
                + "artifactory.revision=1\n"
                + "artifactory.core.mfe.version=1.2.3\n"
                + "artifactory.rtui.mfe.version=2.0.0\n").getBytes());

        ArtifactoryVersionReader.VersionPropertiesContent content =
                ArtifactoryVersionReader.readServicePropsContent(in, "mfeProps", null);

        Map<String, String> mfeVersions = content.mfeVersions;
        Assertions.assertEquals(2, mfeVersions.size(), "Unexpected number of MFE versions");
        Assertions.assertEquals("1.2.3", mfeVersions.get("core"), "Unexpected core MFE version");
        Assertions.assertEquals("2.0.0", mfeVersions.get("rtui"), "Unexpected rtui MFE version");
    }

    private InputStream createInputStream(String version, long revision) {
        return new ByteArrayInputStream(String.format("artifactory.version=%s%n" +
                "artifactory.revision=%s%n", version, revision).getBytes());
    }

    private InputStream createInputStream(String version, int revision, long timestamp) {
        return new ByteArrayInputStream(String.format(
                "artifactory.version=%s%n" +
                        "artifactory.revision=%s%n" +
                        "artifactory.timestamp=%s%n", version, revision, timestamp).getBytes());
    }

    private InputStream createProductVersionInputStream(String version, int revision, long timestamp) {
        return new ByteArrayInputStream(String.format(
                "artifactory.product.version=%s%n" +
                        "artifactory.product.revision=%s%n" +
                        "artifactory.product.timestamp=%s%n", version, revision, timestamp).getBytes());
    }


    @BeforeAll
    public static void setup() {
        TestUtils.setLoggingLevel(ArtifactoryVersionReader.class, Level.ERROR);
    }

    @AfterAll
    public static void tearDown() {
        TestUtils.setLoggingLevel(ArtifactoryVersionReader.class, Level.INFO);
    }
}
