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

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_VERSION_PROPERTIES;
import static org.artifactory.version.ArtifactoryVersionReader.readServicePropsContent;
import static org.artifactory.version.CurrentVersionLoader.RELEASE_VERSION_PATTERN;

/**
 * This test is supposed to fail the build if more than one version holds the same revision - revisions must be unique
 * for all versions.
 *
 * @author Dan Feldman
 * @author Fred Simon
 */
@Test
public class UniqueRevisionsVerifierTest {
    private static final Logger log = LoggerFactory.getLogger(UniqueRevisionsVerifierTest.class);

    public void verifyRevisions() {
        Assert.assertFalse(getUniqueRevisionCount() < ArtifactoryServiceVersion.values().length,
                "Each Artifactory version must hold a unique build revision! Found duplicates: " + duplicates());
    }

    private long getUniqueRevisionCount() {
        return Stream.of(ArtifactoryServiceVersion.values())
                .map(ArtifactoryServiceVersion::getRevision)
                .distinct()
                .count();
    }

    private Set<Long> duplicates() {
        List<Long> revs = Stream.of(ArtifactoryServiceVersion.values())
                .map(ArtifactoryServiceVersion::getRevision)
                .collect(Collectors.toList());

        return revs.stream()
                .filter(rev -> Collections.frequency(revs, rev) > 1)
                .collect(Collectors.toSet());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void verifyIsCurrentVersionNullVersionAndNullRev() {
        ArtifactoryServiceVersion.isCurrentVersion(null, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void verifyIsCurrentVersionNullVersion() {
        ArtifactoryServiceVersion.isCurrentVersion(null, "whatever");
    }

    public void verifyIsNotCurrentVersion() {
        // Null revision
        Assert.assertFalse(ArtifactoryServiceVersion.isCurrentVersion("whatever", null));
    }

    public void verifyIsCurrentVersion() {
        Assert.assertTrue(ArtifactoryServiceVersion.isCurrentVersion("${forgot}", null));
        Assert.assertTrue(ArtifactoryServiceVersion.isCurrentVersion("whatever-SNAPSHOT", null));
        Assert.assertTrue(ArtifactoryServiceVersion.isCurrentVersion("whatever.x-DOWN-whatever", null));

        Assert.assertTrue(ArtifactoryServiceVersion.isCurrentVersion("whatever", "${ff}"));
        Assert.assertTrue(ArtifactoryServiceVersion.isCurrentVersion("whatever", "dev"));
        Assert.assertTrue(ArtifactoryServiceVersion.isCurrentVersion("whatever", "dev-branch-456"));
    }

    public void verifyAllReleasedVersionMatches() {
        InputStream inputStream = ArtifactoryHome.class.getResourceAsStream(ARTIFACTORY_VERSION_PROPERTIES);
        ArtifactoryVersionReader.VersionPropertiesContent versionPropertiesContent =
                readServicePropsContent(inputStream, ARTIFACTORY_VERSION_PROPERTIES);
        String runningVersion = versionPropertiesContent.versionString;

        for (ArtifactoryServiceVersion version : ArtifactoryServiceVersion.values()) {
            if (version.getVersion().equals(runningVersion)) {
                // nothing here
                continue;
            }
            String versionString = version.getVersion();
            String[] split = versionString.split("\\.");
            Assert.assertTrue(split.length >= 3);
            if (split.length >= 4) {
                Assert.assertTrue(versionString.endsWith(".1"));
                if (version == ArtifactoryServiceVersionProvider.v130beta61.get()) {
                    versionString = "1.3.0_m061";
                } else {
                    versionString = versionString.substring(0, versionString.length() - 2) + "_p001";
                }
            }
            versionString = versionString.
                    replace("u", "_p00").
                    replace("-beta-", "_m00").
                    replace("-rc-", "_m00").
                    replace("-rc", "_m00").
                    replace("-", "_");
            Matcher releaseVersionMatcher = RELEASE_VERSION_PATTERN.matcher(versionString);
            if (releaseVersionMatcher.matches()) {
                if (version.after(ArtifactoryServiceVersionProvider.v542.get())) {
                    assertVersionMatch(version, versionString, releaseVersionMatcher);
                }
            } else {
                log.warn("Version " + version + " does not respect the regexp");
            }
        }
    }

    static void assertVersionMatch(ArtifactoryServiceVersion artifactoryServiceVersion, String releaseVersion,
            Matcher releaseVersionMatcher) {
        int major = Integer.parseInt(releaseVersionMatcher.group(1));
        int minor = Integer.parseInt(releaseVersionMatcher.group(2));
        int patch = Integer.parseInt(releaseVersionMatcher.group(3));
        String preRelease = releaseVersionMatcher.group(4);
        if (StringUtils.isNotBlank(preRelease)) {
            char preChar = preRelease.substring(1, 2).charAt(0);
            Assert.assertTrue(preChar == 'm' || preChar == 'p',
                    "Pre release character '" + preChar + "' isn't a valid pre release character in " +
                            releaseVersion);
            int preReleaseNumber = Integer.parseInt(preRelease.substring(2));
            Assert.assertTrue(preReleaseNumber > 0,
                    "Version pattern pre release number is negative in " + releaseVersion);

            Assert.assertTrue(
                    artifactoryServiceVersion.getVersion()
                            .startsWith(major + "." + minor + "." + patch + "-" + preChar));
            if (preChar == 'm') {
                long rev = constructRevision(major, minor, patch, preReleaseNumber);
                Assert.assertEquals(rev + preReleaseNumber, artifactoryServiceVersion.getRevision());
            } else {
                long rev = constructRevision(major, minor, patch, preReleaseNumber + 900);
                Assert.assertEquals(rev + preReleaseNumber + 900L, artifactoryServiceVersion.getRevision());
            }
        } else {
            // Full release no milestone or customer patch
            Assert.assertEquals(artifactoryServiceVersion.getVersion(), releaseVersion);
            long rev = constructRevision(major, minor, patch, 900);
            Assert.assertEquals(artifactoryServiceVersion.getRevision(), rev);
        }
    }

    private static long constructRevision(int major, int minor, int patch, int preReleaseNumber) {
        if (minor < 100) {
            return Long.parseLong(String.format("%02d%02d%02d%03d", major, minor, patch, preReleaseNumber));
        }
        return Long.parseLong(String.format("%01d%03d%02d%03d", major, minor, patch, preReleaseNumber));
    }
}