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

import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link MimeTypesReader}
 *
 * @author Yossi Shaul
 */
@SuppressWarnings("ConstantConditions")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MimeTypesReaderTest {
    private MimeTypes mimeTypes;

    @BeforeAll
    public void setup() {
        MimeTypesReader reader = new MimeTypesReader();
        mimeTypes = reader.read(ResourceUtils.getResourceAsFile("/org/artifactory/mime/mimetypes-test.xml"));
        assertNotNull(mimeTypes, "Should not return null");
        assertEquals(15, mimeTypes.getMimeTypes().size(), "Unexpected count of mime types");
    }

    @Test
    public void checkArchiveMime() {
        MimeType archive = mimeTypes.getByMime(MimeType.javaArchive);
        assertNotNull(archive, "Couldn't find application/java-archive mime");
        assertEquals(5, archive.getExtensions().size(), "Unexpected file extensions count");
        assertFalse(archive.isViewable(), "Should not be viewable");
        assertFalse(archive.isIndex(), "Should be marked as indexed");
        assertFalse(archive.isArchive(), "Should be marked as archive");
        assertNull(archive.getSyntax(), "No syntax configured for this type");
        assertNull(archive.getCss(), "No css class configured for this type");
    }

    @Test
    public void trimmedFileExtensions() {
        // the file extensions list is usually with spaces that should be trimmed
        MimeType archive = mimeTypes.getByMime(MimeType.javaArchive);
        Set<String> extensions = archive.getExtensions();
        assertTrue(extensions.contains("war"), "war extension not found in: " + extensions);
        assertTrue(extensions.contains("jar"), "jar extension not found in: " + extensions);
    }

    @Test
    public void readConvertVersion1() {
        File versionsDirectory = ResourceUtils.getResourceAsFile("/org/artifactory/mime/version/mimetypes-v1.xml");
        MimeTypes result = new MimeTypesReader().read(versionsDirectory);
        assertFalse(result.getByExtension("xml").isIndex());
        assertTrue(result.getByExtension("pom").isIndex());
        assertTrue(result.getByExtension("ivy").isIndex());
    }

    @Test
    public void readConvertVersion5() {
        File versionsDirectory = ResourceUtils.getResourceAsFile("/org/artifactory/mime/version/mimetypes-v5.xml");
        MimeTypes result = new MimeTypesReader().read(versionsDirectory);
        MimeType json = result.getByExtension("json");
        assertNotNull(json, "Expected new json mime type not found");
        assertTrue(json.isViewable());
    }

    @Test
    public void versionsRead() {
        File versionsDirectory = ResourceUtils.getResourceAsFile(
                "/org/artifactory/mime/version/mimetypes-v1.xml").getParentFile();
        File[] mimeTypeFiles = versionsDirectory.listFiles((FileFilter) new SuffixFileFilter("xml"));
        assertTrue(mimeTypeFiles.length > 0, "Couldn't find mime types files under "
                + versionsDirectory.getAbsolutePath());
        for (File mimeTypeFile : mimeTypeFiles) {
            MimeTypesReader reader = new MimeTypesReader();
            reader.read(mimeTypeFile);  // will throw an exception on error
        }
    }

    @Test
    public void allDeclaredExtensionsResolveToTheirDeclaredType() {
        for (MimeType type : mimeTypes.getMimeTypes()) {
            for (String ext : type.getExtensions()) {
                MimeType resolved = mimeTypes.getByExtension(ext);
                assertNotNull(resolved, "Extension '" + ext + "' should resolve to a non-null MimeType");
                assertTrue(resolved.getExtensions().contains(ext),
                        "Resolved MimeType for '" + ext + "' must declare that extension");
            }
        }
    }

    @Test
    public void archiveExtensionsMatchDeclaredArchiveTypes() {
        Set<String> expected = mimeTypes.getMimeTypes().stream()
                .filter(MimeType::isArchive)
                .flatMap(t -> t.getExtensions().stream())
                .collect(Collectors.toSet());
        assertEquals(expected, mimeTypes.getArchiveExtensions(),
                "getArchiveExtensions() must equal the union of all isArchive() type extensions");
    }

    @Test
    public void derivedCachesBuiltEagerlyAfterReaderRead() throws Exception {
        MimeTypes fresh = new MimeTypesReader()
                .read(ResourceUtils.getResourceAsFile("/org/artifactory/mime/mimetypes-test.xml"));
        Field typeByExtensionField = MimeTypes.class.getDeclaredField("typeByExtension");
        typeByExtensionField.setAccessible(true);
        Field archivesExtensionsField = MimeTypes.class.getDeclaredField("archivesExtensions");
        archivesExtensionsField.setAccessible(true);
        assertNotNull(typeByExtensionField.get(fresh),
                "typeByExtension must be built eagerly by the reader before any getter is called");
        assertNotNull(archivesExtensionsField.get(fresh),
                "archivesExtensions must be built eagerly by the reader before any getter is called");
    }
}
