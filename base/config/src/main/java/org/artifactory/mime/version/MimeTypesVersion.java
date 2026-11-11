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

package org.artifactory.mime.version;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.mime.version.converter.LatestVersionConverter;
import org.artifactory.mime.version.converter.v1.XmlIndexedConverter;
import org.artifactory.mime.version.converter.v11.BzipMimeTypeConverter;
import org.artifactory.mime.version.converter.v12.GoMimeTypeConverter;
import org.artifactory.mime.version.converter.v13.CondaMimeTypeConverter;
import org.artifactory.mime.version.converter.v14.DebianDebugMimeTypeConverter;
import org.artifactory.mime.version.converter.v15.TerraformMimeTypeConverter;
import org.artifactory.mime.version.converter.v16.SwiftMimeTypeConverter;
import org.artifactory.mime.version.converter.v17.XzMimeTypeConverter;
import org.artifactory.mime.version.converter.v17.MarkdownMimeTypeConverter;
import org.artifactory.mime.version.converter.v2.AscMimeTypeConverter;
import org.artifactory.mime.version.converter.v3.ArchivesIndexConverter;
import org.artifactory.mime.version.converter.v3.NuPkgMimeTypeConverter;
import org.artifactory.mime.version.converter.v4.GemMimeTypeConverter;
import org.artifactory.mime.version.converter.v5.JsonMimeTypeConverter;
import org.artifactory.mime.version.converter.v6.DebianMimeTypeConverter;
import org.artifactory.mime.version.converter.v7.ArchiveMimeTypeConverter;
import org.artifactory.mime.version.converter.v8.PythonMimeTypeConverter;
import org.artifactory.mime.version.converter.v9.Sha2ChecksumMimeTypeConverter;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.VersionWrapper;
import org.artifactory.version.XmlConverterUtils;
import org.artifactory.version.converter.XmlConverter;

import java.util.Arrays;
import java.util.List;

/**
 * A mimetypes.xml version.
 *
 * Versioning scheme as follows:
 * v#(first version the *last* change (meaning the converter above you) was introduced,
 * last version before next change was introduced, converter to run for next version [v#]),
 *
 * @author Yossi Shaul
 */
@Slf4j
public enum MimeTypesVersion implements VersionWrapper {
    v1(ArtifactoryServiceVersionProvider.v223.get(), new XmlIndexedConverter()),
    v2(ArtifactoryServiceVersionProvider.v230.get(), new AscMimeTypeConverter()),
    v3(ArtifactoryServiceVersionProvider.v250.get(), new ArchivesIndexConverter(), new NuPkgMimeTypeConverter()),
    v4(ArtifactoryServiceVersionProvider.v251.get(), new GemMimeTypeConverter()),
    v5(ArtifactoryServiceVersionProvider.v303.get(), new JsonMimeTypeConverter()),
    v6(ArtifactoryServiceVersionProvider.v320.get(), new DebianMimeTypeConverter()),
    v7(ArtifactoryServiceVersionProvider.v330.get(), new ArchiveMimeTypeConverter()),
    v8(ArtifactoryServiceVersionProvider.v410.get(), new PythonMimeTypeConverter()),
    v9(ArtifactoryServiceVersionProvider.v5011.get(), new Sha2ChecksumMimeTypeConverter()),
    v10(ArtifactoryServiceVersionProvider.v550.get()),
    v11(ArtifactoryServiceVersionProvider.v680.get(), new BzipMimeTypeConverter()),
    v12(ArtifactoryServiceVersionProvider.v690m001.get(), new GoMimeTypeConverter()),
    v13(ArtifactoryServiceVersionProvider.v6190.get(), new CondaMimeTypeConverter()),
    v14(ArtifactoryServiceVersionProvider.v7210m007.get(), new DebianDebugMimeTypeConverter()),
    v15(ArtifactoryServiceVersionProvider.v729.get(), new TerraformMimeTypeConverter()),
    v16(ArtifactoryServiceVersionProvider.v730.get(), new SwiftMimeTypeConverter()),
    v17(ArtifactoryServiceVersionProvider.v71570.get(), new MarkdownMimeTypeConverter(), new XzMimeTypeConverter());

    private final ArtifactoryServiceVersion version;
    private final XmlConverter[] converters;

    /**
     * @param version    First Artifactory version that this version was supported.
     * @param converters List of converters to apply for conversion to the next config version.
     */
    MimeTypesVersion(ArtifactoryServiceVersion version, XmlConverter... converters) {
        this.version = version;
        this.converters = converters;
    }

    /**
     * Convert an xml string to this instance mime type version.
     *
     * @param mimeTypesXmlAsString The mime types xml string to convert
     * @return XML string converted to this version
     */
    public String convert(String mimeTypesXmlAsString) {
        // First create the list of converters to apply
        List<XmlConverter> toRun = Lists.newArrayList();

        // All converters of versions above me needs to be executed in sequence
        MimeTypesVersion[] versions = MimeTypesVersion.values();
        for (MimeTypesVersion typesVersion : versions) {
            if (typesVersion.ordinal() >= ordinal() && typesVersion.converters != null) {
                toRun.addAll(Arrays.asList(typesVersion.converters));
            }
        }
        // Always add the converter that changes the version string
        toRun.add(new LatestVersionConverter());

        return XmlConverterUtils.convertPretty(toRun, mimeTypesXmlAsString);
    }

    @Override
    public ArtifactoryServiceVersion getVersion() {
        return version;
    }

    /**
     * @param mimeTypesXmlAsString The string representation of the mimetypes.xml file
     * @return The {@link MimeTypesVersion} matching the xml content.
     */
    public static MimeTypesVersion findVersion(String mimeTypesXmlAsString) {
        final String VERSION_ATT = "<mimetypes version=\"";
        int versionIdx = mimeTypesXmlAsString.indexOf(VERSION_ATT);
        if (versionIdx < 0) {
            throw new IllegalArgumentException("Unidentified mimetypes configuration");
        }

        int versionStartIndex = versionIdx + VERSION_ATT.length();
        int versionEndIndex = getVersionEndIndex(mimeTypesXmlAsString, versionStartIndex);
        int version = Integer.parseInt(mimeTypesXmlAsString.substring(versionStartIndex, versionEndIndex));
        if (MimeTypesVersion.values().length < version) {
            log.warn("mimetypes.xml version {} is newer than the latest known version {}.",
                    version, MimeTypesVersion.values().length);
            return getCurrent();
        }
        return MimeTypesVersion.values()[version - 1];
    }

    /**
     * Having version start index, locates version end index
     * in mimeTypesXmlAsString
     *
     * @param pointer version start index
     * @return version end index
     * @throws IllegalArgumentException if pointer reaches size of mimeTypesXmlAsString
     *                                  without finding version closing '>' character
     */
    private static int getVersionEndIndex(String mimeTypesXmlAsString, int pointer) {
        while (mimeTypesXmlAsString.charAt(pointer) != '>') {
            pointer++;
            if (pointer >= mimeTypesXmlAsString.length()) {
                throw new IllegalArgumentException("MimeTypes version in malformed");
            }
        }
        return --pointer;
    }

    public static MimeTypesVersion getCurrent() {
        MimeTypesVersion[] versions = MimeTypesVersion.values();
        return versions[versions.length - 1];
    }

    public boolean isCurrent() {
        return this == getCurrent();
    }

    /**
     * @return The version string associated to this version (the one written in the xml file)
     */
    public String versionString() {
        return name().substring(1);
    }
}
