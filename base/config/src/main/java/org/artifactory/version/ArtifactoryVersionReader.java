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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.artifactory.version.ArtifactoryServiceVersion.getCurrent;
import static org.artifactory.version.ArtifactoryServiceVersion.isCurrentVersion;


/**
 * Returns ArtifactoryVersion object from a properties stream/file.
 *
 * @author Yossi Shaul
 */
public class ArtifactoryVersionReader {
    private static final Logger log = LoggerFactory.getLogger(ArtifactoryVersionReader.class);
    // First release of Artifactory
    static final long UNFILTERED_TIMESTAMP = 1167040800000L;
    
    private static final Pattern MFE_VERSION_PROPERTY_PATTERN =
            Pattern.compile("^artifactory\\.(.+)\\.mfe\\.version$");

    public static class VersionPropertiesContent {
        String versionString;
        String revisionString;
        String packageHandlerVersion;
        String buildNumberString;
        String timestampString;
        Map<String, String> mfeVersions;

        VersionPropertiesContent() {
            this.mfeVersions = new HashMap<>();
        }

        public VersionPropertiesContent(String version, String revision, String buildNumber, String timestamp,
                Map<String, String> mfeVersions) {
            this.versionString = version;
            this.revisionString = revision;
            this.packageHandlerVersion = "";
            this.buildNumberString = buildNumber;
            this.timestampString = timestamp;
            this.mfeVersions = mfeVersions != null ? new HashMap<>(mfeVersions) : new HashMap<>();
        }
    }

    public static CompoundVersionDetails readFromFileAndFindVersion(File propertiesFile) {
        if (propertiesFile == null) {
            throw new IllegalArgumentException("Null properties file is not allowed");
        }
        try {
            return readAndFindServiceVersion(new FileInputStream(propertiesFile), propertiesFile.getAbsolutePath());
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Properties file " + propertiesFile.getName() + " doesn't exist");
        }
    }

    static VersionPropertiesContent readServicePropsContent(InputStream inputStream, String sourceName) {
        return readServicePropsContent(inputStream, sourceName, null);
    }

    /**
     * Reads service version properties. Only MFE version properties whose name is in
     * {@code knownMfeNames} are collected. When null, no MFE version properties are collected.
     *
     * @param knownMfeNames set of MFE names to collect (e.g. from MfeProjectRegistry.getAllProjects()), or null
     */
    static VersionPropertiesContent readServicePropsContent(InputStream inputStream, String sourceName,
            Set<String> knownMfeNames) {
        VersionPropertiesContent res = new VersionPropertiesContent();
        if (inputStream == null) {
            throw new IllegalArgumentException("Artifactory properties input stream cannot be null");
        }
        Properties props = new Properties();
        try {
            props.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read version details from '" + sourceName + "'", e);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
        String versionPropName = ConstantValues.artifactoryServiceVersion.getPropertyName();
        res.versionString = props.getProperty(versionPropName);
        if (StringUtils.isBlank(res.versionString)) {
            throw new IllegalArgumentException("Version source '" + sourceName +
                    "' does not have the mandatory property '" + versionPropName + "'");
        }
        res.revisionString = props.getProperty(ConstantValues.artifactoryServiceRevision.getPropertyName());
        res.packageHandlerVersion =
                props.getProperty(ConstantValues.artifactoryServicePackageHandlerVersion.getPropertyName());
        res.timestampString = props.getProperty(ConstantValues.artifactoryServiceTimestamp.getPropertyName());
        res.buildNumberString = props.getProperty(ConstantValues.artifactoryServiceBuildNumber.getPropertyName());

        // When knownMfeNames is null, collect all MFE version properties (e.g. from classpath resource).
        // When non-null, collect only properties whose MFE name is in the set.
        boolean filterByMfeNames = knownMfeNames != null && !knownMfeNames.isEmpty();
        res.mfeVersions = new HashMap<>();
        Set<String> propertyNames = props.stringPropertyNames();
        for (String propertyName : propertyNames) {
            Matcher matcher = MFE_VERSION_PROPERTY_PATTERN.matcher(propertyName);
            if (matcher.matches()) {
                String mfeName = matcher.group(1);
                if (!filterByMfeNames || knownMfeNames.contains(mfeName)) {
                    String version = props.getProperty(propertyName);
                    if (StringUtils.isNotBlank(version)) {
                        res.mfeVersions.put(mfeName, version);
                    }
                }
            }
        }
        return res;
    }

    static VersionPropertiesContent readProductPropsContent(InputStream inputStream, String sourceName) {
        VersionPropertiesContent res = new VersionPropertiesContent();
        if (inputStream == null) {
            throw new IllegalArgumentException("Artifactory product properties input stream cannot be null");
        }
        Properties props = new Properties();
        try {
            props.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read product version details from '" + sourceName + "'", e);
        }
        String versionPropName = ConstantValues.artifactoryProductVersion.getPropertyName();
        res.versionString = props.getProperty(versionPropName);
        if (StringUtils.isBlank(res.versionString)) {
            throw new IllegalArgumentException("Product version source '" + sourceName +
                    "' does not have the mandatory property '" + versionPropName + "'");
        }
        res.revisionString = props.getProperty(ConstantValues.artifactoryProductRevision.getPropertyName());
        res.timestampString = props.getProperty(ConstantValues.artifactoryProductTimestamp.getPropertyName());
        res.buildNumberString = props.getProperty(ConstantValues.artifactoryProductBuildNumber.getPropertyName());
        return res;
    }

    public static CompoundVersionDetails readAndFindServiceVersion(InputStream inputStream, String sourceName) {
        return readAndFindServiceVersion(inputStream, sourceName, null);
    }

    /**
     * Same as {@link #readAndFindServiceVersion(InputStream, String)} but only collects MFE version
     * properties for the given names (e.g. derived from {@code MfeProjectRegistry.getAllProjects()}).
     */
    public static CompoundVersionDetails readAndFindServiceVersion(InputStream inputStream, String sourceName,
            Set<String> knownMfeNames) {
        return getCompoundVersionDetails(readServicePropsContent(inputStream, sourceName, knownMfeNames));
    }

    public static CompoundVersionDetails readAndFindProductVersion(InputStream inputStream, String sourceName) {
        VersionPropertiesContent versionProps = readProductPropsContent(inputStream, sourceName);
        String productVersion = System.getenv("ARTIFACTORY_PRODUCT_VERSION");
        overrideVersion(productVersion, versionProps);
        return getCompoundVersionDetails(versionProps);
    }

    public static void overrideVersion(String productVersion, VersionPropertiesContent versionProps) {
        if (StringUtils.isNotBlank(productVersion)) {
            versionProps.versionString = productVersion;
            if (productVersion.contains("SNAPSHOT")) {
                versionProps.revisionString = String.valueOf(Integer.MAX_VALUE);
            } else {
                versionProps.revisionString =
                        String.valueOf(ArtifactoryServiceVersionProvider.getRevisionFromVersion(productVersion));
            }
        }
    }

    public static CompoundVersionDetails getCompoundVersionDetails(String version, String revision, String timestamp) {
        Map<String, String> emptyMfeVersions = new HashMap<>();
        return getCompoundVersionDetails(new VersionPropertiesContent(version, revision, "UNDEFINED",
                timestamp, emptyMfeVersions));
    }

    private static CompoundVersionDetails getCompoundVersionDetails(VersionPropertiesContent versionProps) {
        long revision = Integer.MAX_VALUE;
        if (StringUtils.isNumeric(versionProps.revisionString)) {
            revision = Long.parseLong(versionProps.revisionString);
        }
        ArtifactoryServiceVersion version;
        if (isCurrentVersion(versionProps.versionString, versionProps.revisionString)) {
            version = getCurrent();
        } else {
            version = ArtifactoryServiceVersionProvider.get(versionProps.versionString, revision);
        }
        long timestamp;
        try {
            if ("${timestamp}".equals(versionProps.timestampString) || "LOCAL".equals(versionProps.timestampString)) {
                // In dev mode
                timestamp = UNFILTERED_TIMESTAMP;
            } else {
                timestamp = Long.parseLong(versionProps.timestampString);
            }
        } catch (Exception e) {
            log.warn("Could not parse timestamp value {}", versionProps.timestampString);
            timestamp = 0;
        }
        return new CompoundVersionDetails(version, versionProps.packageHandlerVersion, versionProps.buildNumberString,
                timestamp, versionProps.mfeVersions);
    }
}
