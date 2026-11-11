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

package org.artifactory.util;

import com.google.common.collect.Lists;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.common.ResourceUtils;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.reporters.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link ZipUtils} class.
 *
 * @author Yossi Shaul
 */
@Test
public class ZipUtilsTest extends ArtifactoryHomeBoundTest {

    private static final String MALICIOUS_SYM_LINK_FILE_NAME = "maliciousLink";

    private File zipFile;
    private File archiveWithSymLinks;
    private ZipInputStream zis;

    // the zip test contains: file.txt, folder/another.txt

    @BeforeClass
    public void setup() {
        zipFile = ResourceUtils.getResourceAsFile("/ziptest.zip");
        archiveWithSymLinks = ResourceUtils.getResourceAsFile("/zip-with-symlinks.tar.gz");
    }

    @BeforeMethod
    public void openStream() throws FileNotFoundException {
        zis = new ZipInputStream(new FileInputStream(zipFile));
    }

    @AfterMethod
    public void closeStream() {
        IOUtils.closeQuietly(zis);
    }

    public void locateExistingEntry() throws Exception {
        ZipEntry zipEntry = ZipUtils.locateEntry(zis, "file.txt", null);
        assertNotNull(zipEntry, "Couldn't find zip entry");
    }

    public void locateMissingEntry() throws Exception {
        ZipEntry zipEntry = ZipUtils.locateEntry(zis, "nosuchfile.txt", null);
        assertNull(zipEntry, "Shouldn't have found zip entry");
    }

    public void testExtractZipFile() throws Exception {
        File tempExtractedDir = extract(zipFile);
        assertExtractedFiles(tempExtractedDir);
    }

    @Test // RTFACT-7596
    public void testExtractingSymLinks() throws Exception {
        File tempExtractedDir = extract(archiveWithSymLinks);
        assertExtractedSymlinks(new File(tempExtractedDir.getPath() + File.separator + "zip-with-symlinks"));
    }

    @Test
    void testLocateArchiveEntry() throws IOException {
        File tarArchiveFile = ResourceUtils.getResourceAsFile("/test_archive.tar");
        try (TarArchiveInputStream inputStream = new TarArchiveInputStream(new FileInputStream(tarArchiveFile))) {
            ArchiveEntry entry = ZipUtils.locateArchiveEntry(inputStream, "target_file.txt");
            assertNotNull(entry, "Should have found entry");
        }
    }

    @Test
    void testLocateArchiveEntryWithAlternativeExt() throws IOException {
        File tarArchiveFile = ResourceUtils.getResourceAsFile("/test_archive.tar");
        try (TarArchiveInputStream inputStream = new TarArchiveInputStream(new FileInputStream(tarArchiveFile))) {
            ArchiveEntry entry = ZipUtils.locateArchiveEntry(inputStream, "target_file.bin", List.of("csv", "txt"));
            assertNotNull(entry, "Should have found entry");
        }
    }

    @Test
    void testLocateNonExistingArchiveEntry() throws IOException {
        File tarArchiveFile = ResourceUtils.getResourceAsFile("/test_archive.tar");
        try (TarArchiveInputStream inputStream = new TarArchiveInputStream(new FileInputStream(tarArchiveFile))) {
            ArchiveEntry entry = ZipUtils.locateArchiveEntry(inputStream, "non_existent_file.txt");
            assertNull(entry);
        }
    }

    @Test
    void testLocateZipEntry() throws IOException {
        File zipArchiveFile = ResourceUtils.getResourceAsFile("/test_archive.zip");
        try (ZipInputStream inputStream = new ZipInputStream(new FileInputStream(zipArchiveFile))) {
            ZipEntry entry = ZipUtils.locateEntry(inputStream, "target_file.txt");
            assertNotNull(entry, "Should have found entry");
        }
    }

    @Test
    void testLocateZipEntryWithAlternativeExt() throws IOException {
        File zipArchiveFile = ResourceUtils.getResourceAsFile("/test_archive.zip");
        try (ZipInputStream inputStream = new ZipInputStream(new FileInputStream(zipArchiveFile))) {
            ZipEntry entry = ZipUtils.locateEntry(inputStream, "target_file.bin", List.of("csv", "txt"));
            assertNotNull(entry, "Should have found entry");
        }
    }

    @Test
    void testLocateNonExistingZipEntry() throws IOException {
        File zipArchiveFile = ResourceUtils.getResourceAsFile("/test_archive.zip");
        try (ZipInputStream inputStream = new ZipInputStream(new FileInputStream(zipArchiveFile))) {
            ZipEntry entry = ZipUtils.locateEntry(inputStream, "non_existent_file.txt");
            assertNull(entry);
        }
    }

    @Test // RTFACT-22303
    public void testExtractTarSymlinkWithOuterPath() throws Exception {
        // Create a folder hierarchy.
        File outerDir = new File("outer");
        FileUtils.deleteDirectory(outerDir);
        FileUtils.forceMkdir(outerDir);
        try {
            File extractDir = new File(outerDir, "extract");
            FileUtils.forceMkdir(extractDir);
            // Construct a path to link file. The path points outside the extract dir. So it is NOT valid.
            String pathToLinkFile = outerDir.getCanonicalPath() + File.separator + MALICIOUS_SYM_LINK_FILE_NAME;
            // Construct a path to a fictional target file. The path should point inside the extract dir. So it's valid.
            String pathToTargetFile = extractDir.getCanonicalPath() + File.separator + "someFile.test";
            TarArchiveEntry entry = new TarArchiveEntry(pathToLinkFile, true);
            entry.setLinkName(pathToTargetFile);

            ZipUtils.extractTarSymLink(extractDir.getCanonicalFile(), entry);

            // Assert that the link file was not created.
            List<String> outerDirFiles = Lists.newArrayList(Objects.requireNonNull(outerDir.list()));
            assertFalse(outerDirFiles.contains(MALICIOUS_SYM_LINK_FILE_NAME));
        } finally {
            FileUtils.forceDelete(outerDir); // Clean up all the test data.
        }
    }

    public void testExtractTarFile() throws Exception {
        File tarFile = ResourceUtils.getResourceAsFile("/tartest.tar");
        File tempExtractedDir = extract(tarFile);
        assertExtractedFiles(tempExtractedDir);
    }

    public void testExtractTarBz2File() throws Exception {
        File tarFile = ResourceUtils.getResourceAsFile("/tarbzip2test.tar.bz2");
        File tempExtractedDir = extract(tarFile);
        List<String> files = Lists.newArrayList(tempExtractedDir.list());
        assertTrue(files.contains("info"), "Unexpected info directory to exist");
    }

    public void testExtract7ZipFile() throws Exception {
        File sevenZFile = ResourceUtils.getResourceAsFile("/sevenZtest.7z");
        File tempExtractedDir = extract(sevenZFile);
        assertExtractedFiles(tempExtractedDir);
    }

    public void testExtractTarGzFile() throws Exception {
        File gzipFile = ResourceUtils.getResourceAsFile("/gziptest.tar.gz");
        File tempExtractedDir = extract(gzipFile);
        assertExtractedFiles(tempExtractedDir);
    }

    public void testExtractTgzFile() throws Exception {
        File tgzFile = ResourceUtils.getResourceAsFile("/tgztest.tgz");
        File tempExtractedDir = extract(tgzFile);
        assertExtractedFiles(tempExtractedDir);
    }

    public void testExtractTarXzFile() throws Exception {
        final File tarxzFile = ResourceUtils.getResourceAsFile("/tarxztest.tar.xz");
        final File tempExtractedDir = extract(tarxzFile);
        assertExtractedFiles(tempExtractedDir);
    }

    public void testExtractXzFile() throws Exception {
        final File xzFile = ResourceUtils.getResourceAsFile("/xztest.xz");
        final File tempExtractedDir = extract(xzFile);
        assertExtractedFiles(tempExtractedDir);
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "(.*)Unsupported(.*)archive(.*)extension(.*)")
    public void testUnsupportedArchive() throws Exception {
        File unsupportedFile = ResourceUtils.getResourceAsFile("/unsupported.ar");
        extract(unsupportedFile);
    }

    public void testGetArchiveInputStreamArray() throws IOException {
        assertArchiveInputStreamArray("file.TGZ", TarArchiveInputStream[].class);
        assertArchiveInputStreamArray("file.tgz", TarArchiveInputStream[].class);
        assertArchiveInputStreamArray("file.7z", SevenZInputStreamWrapper[].class);
        assertArchiveInputStreamArray("file.7Z", SevenZInputStreamWrapper[].class);
        assertArchiveInputStreamArray("file.zip", ZipArchiveInputStream[].class);
        assertArchiveInputStreamArray("file.ar", ZipArchiveInputStream[].class);
        assertArchiveInputStreamArray("file.tar.xz", TarArchiveInputStream[].class);
        assertArchiveInputStreamArray("file.xz", TarArchiveInputStream[].class);
        assertArchiveInputStreamArray("file.nar.xz", NarArchiveInputStream[].class);
    }

    @Test
    public void testIsTgzFamilyArchiveExcludesNarXz() {
        assertTrue(ZipUtils.isTgzFamilyArchive("file.tar.gz"));
        assertTrue(ZipUtils.isTgzFamilyArchive("file.tgz"));
        assertFalse(ZipUtils.isTgzFamilyArchive("file.nar.xz"));
    }

    private void assertArchiveInputStreamArray(String file, Class<? extends ArchiveInputStream[]> aClass) {
        final int length = 2;
        ArchiveInputStream[] archiveInputStreamArray = ZipUtils.getArchiveInputStreamArray(file, length);
        assertEquals(archiveInputStreamArray.getClass(), aClass);
        assertEquals(length, archiveInputStreamArray.length);
    }

    @Test
    public void testGetArchiveInputStreamWithValidArchive() throws IOException {
        VfsFile vfsFileMock = mock(VfsFile.class);
        when(vfsFileMock.getPath()).thenReturn("generic-local:bla.zip");
        InputStream inputStreamMock = mock(InputStream.class);
        when(vfsFileMock.getStream()).thenReturn(inputStreamMock);

        ArchiveInputStream archiveInputStream = ZipUtils.getArchiveInputStream(vfsFileMock);

        assertTrue(archiveInputStream instanceof ZipArchiveInputStream);
    }

    @Test(expectedExceptions = IOException.class,
            expectedExceptionsMessageRegExp = "Could not process the archive format")
    public void testGetArchiveInputStreamWithBadArchiveFormat() throws IOException {
        VfsFile vfsFileMock = mock(VfsFile.class);
        when(vfsFileMock.getPath()).thenReturn("generic-local:bla.tar.bz2");
        // the mocked VfsFile serves as a corrupted tar.bz2 file
        InputStream inputStreamMock = mock(InputStream.class);
        when(vfsFileMock.getStream()).thenReturn(inputStreamMock);

        try {
            ZipUtils.getArchiveInputStream(vfsFileMock);
        } finally {
            verify(inputStreamMock, times(2)).close();
        }
    }

    private File extract(File fileToExtract) throws Exception {
        File tempExtractedDir = new File(fileToExtract.getParentFile(), "temp");
        FileUtils.deleteDirectory(tempExtractedDir);
        FileUtils.forceMkdir(tempExtractedDir);
        ZipUtils.extract(fileToExtract, tempExtractedDir);
        return tempExtractedDir;
    }

    private void assertExtractedFiles(File tempExtractedDir) {
        List<String> files = Lists.newArrayList(tempExtractedDir.list());
        assertEquals(files.size(), 2, "Unexpected files size");
        assertTrue(files.contains("file.txt"), "Unexpected file name");
        assertTrue(files.contains("folder"), "Unexpected folder name");
    }

    private void assertExtractedSymlinks(File extractedDir) throws IOException {
        List<String> files = Lists.newArrayList(extractedDir.list());
        if (SystemUtils.IS_OS_WINDOWS) {
            // in windows, symlink support depends on the os version and process privileges
            assertTrue(files.size() == 4 || files.size() == 5, "Unexpected file size: " + files);
        } else {
            assertEquals(files.size(), 5, "Unexpected file size: " + files);
        }
        assertTrue(files.contains("chopper_ico.bmp"), "Missing file");
        assertTrue(files.contains("chopper_ico.bmp-hlink"), "Missing file");
        assertTrue(files.contains("chopper_ico.spr"), "Missing file");
        assertTrue(files.contains("chopper_top.bmp"), "Missing file");
        if (files.size() == 5) {    // check links
            assertEquals(files.size(), 5, "Unexpected files size");
            assertTrue(files.contains("chopper_ico.spr-slnk"), "Missing file");

            String file1 = Files.readFile(new FileInputStream(new File(extractedDir, "chopper_ico.spr")));
            String linkToFile1 = Files.readFile(new FileInputStream(new File(extractedDir, "chopper_ico.spr-slnk")));

            String file2 = Files.readFile(new FileInputStream(new File(extractedDir, "chopper_ico.bmp")));
            String linkToFile2 = Files.readFile(new FileInputStream(new File(extractedDir, "chopper_ico.bmp-hlink")));

            assertEquals(file1, linkToFile1, "Soft links reconstruction has failed");
            assertEquals(file2, linkToFile2, "Hard links reconstruction has failed");
        }
    }

    @Test
    void testConvertToArchiveInputStreamWithAnError() throws IOException {
        InputStream is = Mockito.mock(InputStream.class);
        try {
            ZipUtils.convertToArchiveInputStream(is, ".tgz");
        } catch (IOException e) {
            // Expected
        }
        Mockito.verify(is).markSupported();
        Mockito.verify(is).read(any(), anyInt(), anyInt());
        Mockito.verify(is).close();
        Mockito.verifyNoMoreInteractions(is);
    }

    /**
     * Test that .nar.xz files are correctly identified and return NarArchiveInputStream.
     */
    @Test
    public void testConvertToArchiveInputStreamForNarXz() throws IOException {
        // Create a valid NAR archive wrapped in XZ compression
        byte[] narData = createSimpleNarArchive();
        byte[] xzCompressedNar = compressWithXz(narData);

        try (ArchiveInputStream ais = ZipUtils.convertToArchiveInputStream(
                new java.io.ByteArrayInputStream(xzCompressedNar), "package.nar.xz")) {
            assertTrue(ais instanceof NarArchiveInputStream,
                    "Expected NarArchiveInputStream for .nar.xz files, got: " + ais.getClass().getName());

            // Verify we can read entries from the NAR
            ArchiveEntry entry = ais.getNextEntry();
            assertNotNull(entry, "Should be able to read entry from NAR archive");
            assertEquals(entry.getName(), "test.txt");
        }
    }

    /**
     * Test that .nar.xz suffix is correctly detected.
     */
    @Test
    public void testNarXzSuffixDetection() throws IOException {
        byte[] narData = createSimpleNarArchive();
        byte[] xzCompressedNar = compressWithXz(narData);

        // Test various path formats
        String[] narXzPaths = {
                "file.nar.xz",
                "path/to/file.nar.xz",
                "UPPERCASE.NAR.XZ",
                "/nix/store/abc123-package.nar.xz"
        };

        for (String path : narXzPaths) {
            try (ArchiveInputStream ais = ZipUtils.convertToArchiveInputStream(
                    new java.io.ByteArrayInputStream(xzCompressedNar), path.toLowerCase())) {
                assertTrue(ais instanceof NarArchiveInputStream,
                        "Expected NarArchiveInputStream for path: " + path);
            }
        }
    }

    /**
     * Test that .tar.xz files still return TarArchiveInputStream (not NarArchiveInputStream).
     */
    @Test
    public void testTarXzDoesNotReturnNarInputStream() throws IOException {
        File tarxzFile = ResourceUtils.getResourceAsFile("/tarxztest.tar.xz");
        try (FileInputStream fis = new FileInputStream(tarxzFile);
                ArchiveInputStream ais = ZipUtils.convertToArchiveInputStream(fis, "file.tar.xz")) {
            assertTrue(ais instanceof TarArchiveInputStream,
                    "Expected TarArchiveInputStream for .tar.xz files, got: " + ais.getClass().getName());
        }
    }

    /**
     * Test that regular .xz files return TarArchiveInputStream.
     */
    @Test
    public void testPlainXzReturnsTarInputStream() throws IOException {
        File xzFile = ResourceUtils.getResourceAsFile("/xztest.xz");
        try (FileInputStream fis = new FileInputStream(xzFile);
                ArchiveInputStream ais = ZipUtils.convertToArchiveInputStream(fis, "file.xz")) {
            assertTrue(ais instanceof TarArchiveInputStream, "Expected TarArchiveInputStream for .xz files");
        }
    }

    /**
     * Test reading actual content from a NarXz archive.
     */
    @Test
    public void testReadNarXzContent() throws IOException {
        String expectedContent = "Hello from NAR!";
        byte[] narData = createNarArchiveWithContent("hello.txt", expectedContent);
        byte[] xzCompressedNar = compressWithXz(narData);

        try (ArchiveInputStream ais = ZipUtils.convertToArchiveInputStream(
                new java.io.ByteArrayInputStream(xzCompressedNar), "test.nar.xz")) {

            ArchiveEntry entry = ais.getNextEntry();
            assertNotNull(entry);
            assertEquals(entry.getName(), "hello.txt");
            assertEquals(entry.getSize(), expectedContent.length());

            // Read content
            byte[] content = new byte[(int) entry.getSize()];
            IOUtils.readFully(ais, content);
            assertEquals(new String(content, java.nio.charset.StandardCharsets.UTF_8), expectedContent);
        }
    }

    /**
     * Test NarXz with multiple entries.
     */
    @Test
    public void testNarXzWithMultipleEntries() throws IOException {
        byte[] narData = createNarArchiveWithMultipleFiles();
        byte[] xzCompressedNar = compressWithXz(narData);

        try (ArchiveInputStream ais = ZipUtils.convertToArchiveInputStream(
                new java.io.ByteArrayInputStream(xzCompressedNar), "multi.nar.xz")) {

            int entryCount = 0;
            ArchiveEntry entry;
            while ((entry = ais.getNextEntry()) != null) {
                entryCount++;
                assertNotNull(entry.getName());
            }
            assertEquals(entryCount, 3, "Expected 3 entries in the NAR archive");
        }
    }

    /**
     * Test NarXz with nested directory structure.
     */
    @Test
    public void testNarXzWithNestedDirectories() throws IOException {
        byte[] narData = createNarArchiveWithNestedDir();
        byte[] xzCompressedNar = compressWithXz(narData);

        try (ArchiveInputStream ais = ZipUtils.convertToArchiveInputStream(
                new java.io.ByteArrayInputStream(xzCompressedNar), "nested.nar.xz")) {

            java.util.List<String> entryNames = new java.util.ArrayList<>();
            ArchiveEntry entry;
            while ((entry = ais.getNextEntry()) != null) {
                entryNames.add(entry.getName());
            }

            assertTrue(entryNames.contains("subdir/"));
            assertTrue(entryNames.contains("subdir/nested.txt"));
        }
    }

    /**
     * Test locateArchiveEntry works with NarArchiveInputStream.
     */
    @Test
    public void testLocateArchiveEntryInNarXz() throws IOException {
        byte[] narData = createNarArchiveWithMultipleFiles();
        byte[] xzCompressedNar = compressWithXz(narData);

        try (NarArchiveInputStream nis = new NarArchiveInputStream(
                new org.apache.commons.compress.compressors.xz.XZCompressorInputStream(
                        new java.io.ByteArrayInputStream(xzCompressedNar)))) {

            ArchiveEntry entry = ZipUtils.locateArchiveEntry(nis, "file2.txt");
            assertNotNull(entry, "Should find file2.txt in the archive");
            assertEquals(entry.getName(), "file2.txt");
        }
    }

    /**
     * Test locateArchiveEntry returns null for non-existent entry in NAR.
     */
    @Test
    public void testLocateNonExistentEntryInNarXz() throws IOException {
        byte[] narData = createNarArchiveWithMultipleFiles();
        byte[] xzCompressedNar = compressWithXz(narData);

        try (NarArchiveInputStream nis = new NarArchiveInputStream(
                new org.apache.commons.compress.compressors.xz.XZCompressorInputStream(
                        new java.io.ByteArrayInputStream(xzCompressedNar)))) {

            ArchiveEntry entry = ZipUtils.locateArchiveEntry(nis, "nonexistent.txt");
            assertNull(entry, "Should not find nonexistent.txt");
        }
    }

    /**
     * Test getArchiveInputStream with VfsFile for .nar.xz path.
     */
    @Test
    public void testGetArchiveInputStreamForNarXzVfsFile() throws IOException {
        byte[] narData = createSimpleNarArchive();
        byte[] xzCompressedNar = compressWithXz(narData);

        VfsFile vfsFileMock = mock(VfsFile.class);
        when(vfsFileMock.getPath()).thenReturn("nix-local:package.nar.xz");
        when(vfsFileMock.getStream()).thenReturn(new java.io.ByteArrayInputStream(xzCompressedNar));

        try (ArchiveInputStream archiveInputStream = ZipUtils.getArchiveInputStream(vfsFileMock)) {
            assertTrue(archiveInputStream instanceof NarArchiveInputStream,
                    "Expected NarArchiveInputStream for .nar.xz VfsFile");
        }
    }

    /**
     * Creates a simple NAR archive with one file.
     */
    private byte[] createSimpleNarArchive() {
        return new NarArchiveInputStreamTest.NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("test.txt", NarArchiveInputStreamTest.NarBuilder.EntryType.REGULAR, "test content", false)
                .endDirectory()
                .build();
    }

    /**
     * Creates a NAR archive with specific content.
     */
    private byte[] createNarArchiveWithContent(String fileName, String content) {
        return new NarArchiveInputStreamTest.NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry(fileName, NarArchiveInputStreamTest.NarBuilder.EntryType.REGULAR, content, false)
                .endDirectory()
                .build();
    }

    /**
     * Creates a NAR archive with multiple files.
     */
    private byte[] createNarArchiveWithMultipleFiles() {
        return new NarArchiveInputStreamTest.NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("file1.txt", NarArchiveInputStreamTest.NarBuilder.EntryType.REGULAR, "content1", false)
                .addEntry("file2.txt", NarArchiveInputStreamTest.NarBuilder.EntryType.REGULAR, "content2", false)
                .addEntry("file3.txt", NarArchiveInputStreamTest.NarBuilder.EntryType.REGULAR, "content3", false)
                .endDirectory()
                .build();
    }

    /**
     * Creates a NAR archive with a nested directory.
     */
    private byte[] createNarArchiveWithNestedDir() {
        return new NarArchiveInputStreamTest.NarBuilder()
                .startArchive()
                .startDirectory()
                .startNestedEntry("subdir")
                .startDirectory()
                .addEntry("nested.txt", NarArchiveInputStreamTest.NarBuilder.EntryType.REGULAR, "nested content", false)
                .endDirectory()
                .endNestedEntry()
                .endDirectory()
                .build();
    }

    /**
     * Compresses data using XZ compression.
     */
    private byte[] compressWithXz(byte[] data) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (org.apache.commons.compress.compressors.xz.XZCompressorOutputStream xzOut =
                     new org.apache.commons.compress.compressors.xz.XZCompressorOutputStream(baos)) {
            xzOut.write(data);
        }
        return baos.toByteArray();
    }
}
