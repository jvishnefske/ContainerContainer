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

package org.artifactory.mime;

import org.apache.commons.io.FilenameUtils;
import org.artifactory.util.SerializablePair;
import org.jfrog.client.util.PathUtils;
import org.jfrog.security.util.Pair;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.artifactory.mime.NamingUtils.METADATA_PREFIX;

/**
 * @author yoavl
 */
public abstract class MavenNaming {
    // Matcher for unique snapshot names of the form artifactId-version-date.time-buildNumber.type
    // for example: artifactory-1.0-20081214.090217-4.pom
    // groups: 1: artifactId-version, 2. artifactId 3. base version (without the snapshot part) 4. date.time-buildNumber
    // 5. date.time 6. buildNumber
    private static final Pattern UNIQUE_SNAPSHOT_NAME_PATTERN =
            Pattern.compile("^((.+)-(.+))-(([0-9]{8}.[0-9]{6})-([0-9]+))[\\.-].+$");

    // Structural minimum length of any string that CAN match UNIQUE_SNAPSHOT_NAME_PATTERN.
    // Derived: <id ≥1> + '-' + <ver ≥1> + '-' + [0-9]{8} + '.' + [0-9]{6} + '-' + <buildNum ≥1>
    //         + [\.-] + <suffix ≥1>  =  1 + 1 + 1 + 1 + 8 + 1 + 6 + 1 + 1 + 1 + 1  =  23.
    // Used as a cheap, provably non-lossy pre-check before invoking the regex on snapshot-folder
    // children — avoids the regex JFR hotspot for the common (non-matching) names in a folder.
    private static final int MIN_UNIQUE_SNAPSHOT_FILE_NAME_LEN = 23;

    public static final String MAVEN_METADATA_NAME = "maven-metadata.xml";
    public static final String MAVEN_METADATA_NAME_SHA512 = "maven-metadata.xml.sha512";
    public static final String SNAPSHOT = "SNAPSHOT";
    public static final String SNAPSHOT_SUFFIX = "-" + SNAPSHOT;
    public static final String NEXUS_INDEX_DIR = ".index";
    public static final String NEXUS_INDEX_PREFIX = "nexus-maven-repository-index";
    public static final String NEXUS_INDEX_ZIP = NEXUS_INDEX_PREFIX + ".zip";
    public static final String NEXUS_INDEX_GZ = NEXUS_INDEX_PREFIX + ".gz";
    public static final String NEXUS_INDEX_PROPERTIES = NEXUS_INDEX_PREFIX + ".properties";
    public static final String NEXUS_INDEX_ZIP_PATH = NEXUS_INDEX_DIR + "/" + NEXUS_INDEX_ZIP;
    public static final String NEXUS_INDEX_GZ_PATH = NEXUS_INDEX_DIR + "/" + NEXUS_INDEX_GZ;
    public static final String NEXUS_INDEX_PROPERTIES_PATH = NEXUS_INDEX_DIR + "/" + NEXUS_INDEX_PROPERTIES;

    private MavenNaming() {
        // utility class
    }

    /**
     * @param version String representing the maven version
     * @return True if the version is a non-unique snapshot version (ie, ends with -SNAPSHOT)
     */
    public static boolean isNonUniqueSnapshotVersion(String version) {
        return version.endsWith(SNAPSHOT_SUFFIX);
    }

    /**
     * @param path Path to a file
     * @return True if the path is of a non-unique snapshot version file
     */
    public static boolean isNonUniqueSnapshot(String path) {
        if (!isSnapshotFolder(path)) {
            return false;
        }
        String fileName = PathUtils.getFileName(path);
        return fileName.contains(SNAPSHOT_SUFFIX + ".") || fileName.contains(SNAPSHOT_SUFFIX + "-");
    }

    public static boolean isUniqueSnapshot(String path) {
        if (!isSnapshotFolder(path)) {
            return false;
        }
        String fileName = PathUtils.getFileName(path);
        return isUniqueSnapshotFileName(fileName);
    }

    private static boolean isSnapshotFolder(String path) {
        return PathUtils.getParent(path).endsWith(SNAPSHOT_SUFFIX);
    }

    /**
     * @param path A path to file or directory
     * @return True if the path is for a snapshot file or folder (either unique or non-unique snapshots)
     */
    public static boolean isSnapshot(String path) {
        boolean result = isNonUniqueSnapshot(path);
        if (!result) {
            result = isUniqueSnapshot(path);
        }
        //A path ending with just the version dir
        if (!result) {
            int versionIdx = path.indexOf(SNAPSHOT_SUFFIX + "/");
            result = versionIdx > 0 && path.lastIndexOf('/') == versionIdx + 8;
        }
        if (!result) {
            result = path.endsWith(SNAPSHOT_SUFFIX);
        }
        return result;
    }

    public static boolean isChecksum(String path) {
        return NamingUtils.isChecksum(path);
    }

    public static boolean isChecksum(File path) {
        return isChecksum(path.getName());
    }

    /**
     * @param path Path to test
     * @return True if this path points to nexus index file.
     */
    public static boolean isIndex(String path) {
        String name = PathUtils.getFileName(path);
        return name.startsWith(NEXUS_INDEX_PREFIX);
    }

    public static boolean isMavenMetadata(String path) {
        String fileName = PathUtils.getFileName(path);
        return isMavenMetadataFileName(fileName);
    }

    public static boolean isMavenPluginMetadata(String path) {
        //TODO Implement it
        return false;
    }

    /**
     * @param path The maven metadata full path
     * @return True if the path points to maven metadata under a snapshot folder
     */
    public static boolean isSnapshotMavenMetadata(String path) {
        Pair<String, String> nameAndParent = getMetadataNameAndParent(path);
        String name = nameAndParent.getFirst();
        String parent = nameAndParent.getSecond();
        return parent != null && parent.endsWith(SNAPSHOT_SUFFIX) && isMavenMetadataFileName(name);
    }

    /**
     * Converts a path to maven 1 path. For example org/apache/commons/commons-email/1.1/commons-email-1.1.jar will
     * result in org.apache.commons/jars/commons-email-1.1.jar
     *
     * @param path Path to convert
     * @return A Maven 1 repository path
     */
    public static String toMaven1Path(String path) {
        String[] pathElements = path.split("/");
        String name = pathElements[pathElements.length - 1];
        String fileExt;
        if (isChecksum(path)) {
            int lastPeriodIndex = name.lastIndexOf('.');
            fileExt = name.substring(name.lastIndexOf('.', lastPeriodIndex - 1) + 1,
                    lastPeriodIndex);
        } else {
            fileExt = name.substring(name.lastIndexOf('.') + 1);
        }

        // Get the group path (the path up until the artifact id)
        StringBuilder groupPath = new StringBuilder(pathElements[0]);
        for (int i = 1; i < pathElements.length - 3; i++) {
            groupPath.append(".").append(pathElements[i]);
        }
        return groupPath.toString().replace('/', '.') + "/" + fileExt + "s/" + name;
    }

    public static boolean isMavenMetadataFileName(String fileName) {
        return MAVEN_METADATA_NAME.equals(fileName) ||
                fileName.endsWith(METADATA_PREFIX + MAVEN_METADATA_NAME);
    }

    /**
     * @param path Path to a file (absolute or relative)
     * @return True if the path points to Maven pom file
     */
    public static boolean isPom(String path) {
        return NamingUtils.isPom(path);
    }

    public static boolean isClientOrServerPom(String path) {
        return isPom(path) || isClientPom(path);
    }

    public static boolean isClientPom(String path) {
        String name = FilenameUtils.getName(path);
        return "pom.xml".equalsIgnoreCase(name);
    }

    /**
     * @param fileName The file name to test if is a unique snapshot
     * @return true if the file name is of the form artifactId-version-date.time-buildNumber.type;
     * false for null or any non-matching name
     * <p/>
     * For example: artifactory-1.0-20081214.090217-4.pom
     */
    public static boolean isUniqueSnapshotFileName(String fileName) {
        // Any name shorter than the structural minimum of UNIQUE_SNAPSHOT_NAME_PATTERN cannot
        // match — this is provably non-lossy and skips the costly regex for the common cases
        // in a snapshot folder (maven-metadata.xml, *.md5, foo-1.0-SNAPSHOT.jar, …).
        if (fileName == null || fileName.length() < MIN_UNIQUE_SNAPSHOT_FILE_NAME_LEN) {
            return false;
        }
        return UNIQUE_SNAPSHOT_NAME_PATTERN.matcher(fileName).matches();
    }

    /**
     * Single-pass parse of a unique snapshot file name. Prefer this over multiple
     * {@link #getUniqueSnapshotVersionBuildNumber} / {@link #getUniqueSnapshotVersionTimestamp}
     * calls on the same name in hot loops: each of those helpers re-runs the same regex via
     * {@link #matchUniqueSnapshotVersion}, which dominated the JFR profile in hot loops.
     *
     * @param fileName candidate file name
     * @return parsed parts, or {@code null} when {@code fileName} is not a unique-snapshot name
     */
    public static UniqueSnapshotParts tryParseUniqueSnapshot(String fileName) {
        if (fileName == null || fileName.length() < MIN_UNIQUE_SNAPSHOT_FILE_NAME_LEN) {
            return null;
        }
        Matcher matcher = UNIQUE_SNAPSHOT_NAME_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new UniqueSnapshotParts(matcher.group(5), Integer.parseInt(matcher.group(6)),
                    matcher.group(4));
        } catch (NumberFormatException e) {
            // The build-number group is unbounded ([0-9]+); a numerically out-of-range value is
            // treated the same as any non-parseable name, honouring the documented null contract.
            return null;
        }
    }

    /**
     * Parsed parts of a unique-snapshot file name. Field meanings match the named groups in the
     * pattern {@code ^((.+)-(.+))-(([0-9]{8}.[0-9]{6})-([0-9]+))[\.-].+$}:
     *
     * @param timestamp               group 5 — the {@code yyyyMMdd.HHmmss} portion
     *                                (e.g. {@code "20081214.090217"})
     * @param buildNumber             group 6 as an int — the build number after the timestamp
     *                                (e.g. {@code 4})
     * @param timestampAndBuildNumber group 4 — the {@code <timestamp>-<buildNumber>} compound
     *                                (e.g. {@code "20081214.090217-4"})
     */
    public record UniqueSnapshotParts(String timestamp, int buildNumber, String timestampAndBuildNumber) {
    }

    /**
     * @param uniqueVersion A file name representing a valid unique snapshot version.
     * @return The timestamp of the unique snapshot version
     */
    public static String getUniqueSnapshotVersionTimestamp(String uniqueVersion) {
        Matcher matcher = matchUniqueSnapshotVersion(uniqueVersion);
        return matcher.group(5);
    }

    /**
     * @param uniqueVersion A file name representing a valid unique snapshot version.
     * @return The timestamp-buildNumber of the unique snapshot version
     */
    public static String getUniqueSnapshotVersionTimestampAndBuildNumber(String uniqueVersion) {
        Matcher matcher = matchUniqueSnapshotVersion(uniqueVersion);
        return matcher.group(4);
    }

    /**
     * @param uniqueVersion A file name representing a valid unique snapshot version.
     * @return The buildNumber of the unique snapshot version
     */
    public static int getUniqueSnapshotVersionBuildNumber(String uniqueVersion) {
        Matcher matcher = matchUniqueSnapshotVersion(uniqueVersion);
        return Integer.parseInt(matcher.group(6));
    }

    /**
     * Returns the base build number of the unique snapshot version.<p/> For example, the base version of
     * 'artifact-5.4-20090623.090500-2.pom' is 5.4.
     *
     * @param uniqueVersion A file name representing a valid unique snapshot version.
     * @return The base build number of the unique snapshot version
     * @deprecated The base version detection is not accurate and will not work if the version contains dashes
     */
    @Deprecated
    public static String getUniqueSnapshotVersionBaseVersion(String uniqueVersion) {
        Matcher matcher = matchUniqueSnapshotVersion(uniqueVersion);
        return matcher.group(3);
    }

    public static Matcher tryMatchUniqueSnapshotVersion(String uniqueVersion) {
        Matcher matcher = UNIQUE_SNAPSHOT_NAME_PATTERN.matcher(uniqueVersion);
        return matcher.matches() ? matcher : null;
    }

    private static Matcher matchUniqueSnapshotVersion(String uniqueVersion) {
        Matcher matcher = UNIQUE_SNAPSHOT_NAME_PATTERN.matcher(uniqueVersion);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a valid maven unique snapshot version: " + uniqueVersion);
        }
        return matcher;
    }

    public static boolean isMavenMetadataChecksum(String path) {
        if (NamingUtils.isChecksum(path)) {
            String checksumTargetFile = PathUtils.stripExtension(path);
            return isMavenMetadata(checksumTargetFile);
        } else {
            return false;
        }
    }

    public static SerializablePair<String, String> getMetadataNameAndParent(String path) {
        int mdPrefixIdx = path.lastIndexOf(METADATA_PREFIX);
        String name = null;
        String parent = null;
        if (mdPrefixIdx >= 0) {
            name = path.substring(mdPrefixIdx + METADATA_PREFIX.length());
            parent = path.substring(0, mdPrefixIdx);
        } else {
            //Fallback to checking maven metadata
            final File file = new File(path);
            if (MavenNaming.MAVEN_METADATA_NAME.equals(file.getName())) {
                name = MavenNaming.MAVEN_METADATA_NAME;
                parent = file.getParent();
            }
        }
        return new SerializablePair<>(name, parent);
    }

}
