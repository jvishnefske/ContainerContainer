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

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link MimeTypes}.
 *
 * @author Yossi Shaul
 */
public class MimeTypesTest {
    private final MimeType TEXT_PLAIN = new MimeType("text/plain", ImmutableSet.of("txt"), true, false, false, "text",
            null);
    private MimeTypes holder;

    @BeforeEach
    public void setup() {
        ImmutableSet<MimeType> types = ImmutableSet.of(
                TEXT_PLAIN,
                new MimeType("text/xml", ImmutableSet.of("xml"), true, false, false, "xml", "xml"),
                new MimeType("text/dup1", ImmutableSet.of("duplicate"), true, false, false, null, null),
                new MimeType("text/dup2", ImmutableSet.of("duplicate"), true, false, false, null, null)
        );

        holder = new MimeTypes(types);
        assertEquals(4, holder.getMimeTypes().size(), "Unexpected count of mime types");
    }

    @Test
    public void getByMimeEntryName() {
        assertNull(holder.getByMime("text/nothere"), "Unexpected mime entry was found");
        assertNotNull(holder.getByMime("text/plain"), "Expected mime entry text/plain not found");
    }

    @Test
    public void getForUnknownExtension() {
        assertNull(holder.getByExtension("text"), "Unexpected mime entry was found");
    }

    @Test
    public void getByExtension() {
        assertEquals(TEXT_PLAIN, holder.getByExtension("txt"), "Unexpected mime type was found");
        assertEquals("text/dup2", holder.getByExtension("duplicate").getType(),
                "Should have taken the second duplicate");
    }

    @Test
    public void getByExtensionUpperCase() {
        assertNotNull(holder.getByExtension("TXT"), "Couldn't find mime type when using different caps");
        assertEquals(TEXT_PLAIN, holder.getByExtension("TXT"), "Unexpected mime type was found");
    }

    @Test
    public void getByExtensionNullInput() {
        assertNull(holder.getByExtension(null), "Expected null for null extension input");
    }

    @Test
    public void getArchiveExtensionsOnlyIncludesArchiveTypes() {
        ImmutableSet<MimeType> typesWithArchive = ImmutableSet.of(
                TEXT_PLAIN,
                new MimeType("application/zip", ImmutableSet.of("zip", "z"), false, false, true, null, null),
                new MimeType("text/xml", ImmutableSet.of("xml"), true, false, false, "xml", "xml")
        );
        MimeTypes withArchive = new MimeTypes(typesWithArchive);
        Set<String> archiveExtensions = withArchive.getArchiveExtensions();
        assertEquals(2, archiveExtensions.size(), "Only archive-type extensions should be included");
        assertTrue(archiveExtensions.contains("zip"), "zip extension should be present");
        assertTrue(archiveExtensions.contains("z"), "z extension should be present");
        assertFalse(archiveExtensions.contains("txt"), "txt (non-archive) should not be present");
        assertFalse(archiveExtensions.contains("xml"), "xml (non-archive) should not be present");
    }

    @Test
    public void getArchiveExtensionsIsImmutable() {
        Set<String> archiveExtensions = holder.getArchiveExtensions();
        assertThrows(UnsupportedOperationException.class,
                () -> archiveExtensions.add("should-fail"),
                "Returned archive extensions set must be immutable");
    }

    @Test
    public void derivedCachesBuiltEagerlyByProtectedConstructor() throws Exception {
        Field typeByExtensionField = MimeTypes.class.getDeclaredField("typeByExtension");
        typeByExtensionField.setAccessible(true);
        Field archivesExtensionsField = MimeTypes.class.getDeclaredField("archivesExtensions");
        archivesExtensionsField.setAccessible(true);
        assertNotNull(typeByExtensionField.get(holder),
                "typeByExtension must be built eagerly by the protected constructor before any getter is called");
        assertNotNull(archivesExtensionsField.get(holder),
                "archivesExtensions must be built eagerly by the protected constructor before any getter is called");
    }
}
