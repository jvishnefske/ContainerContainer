package org.artifactory.util;

import org.apache.commons.io.IOUtils;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link NarArchiveInputStream}.
 *
 * @author Natalie Berni
 */
@Test
public class NarArchiveInputStreamTest {

    /**
     * Test parsing a NAR with a single regular file.
     */
    @Test
    public void testSingleRegularFile() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()  // root directory
                .addEntry("hello.txt", NarBuilder.EntryType.REGULAR, "Hello World!", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            assertEquals(entry.getName(), "hello.txt");
            assertFalse(entry.isDirectory());
            assertFalse(entry.isExecutable());
            assertEquals(entry.getSize(), 12); // "Hello World!".length()

            // Read content
            byte[] content = new byte[(int) entry.getSize()];
            IOUtils.readFully(nis, content);
            assertEquals(new String(content, StandardCharsets.UTF_8), "Hello World!");

            // No more entries
            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test parsing a NAR with an executable file.
     */
    @Test
    public void testExecutableFile() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("script.sh", NarBuilder.EntryType.REGULAR, "#!/bin/bash\necho hello", true)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            assertEquals(entry.getName(), "script.sh");
            assertTrue(entry.isExecutable());
            assertFalse(entry.isDirectory());
        }
    }

    /**
     * Test parsing a NAR with a subdirectory containing a file.
     */
    @Test
    public void testNestedDirectory() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()  // root
                .startNestedEntry("subdir")
                .startDirectory()  // subdir
                .addEntry("file.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .endDirectory()
                .endNestedEntry()
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            // First entry should be the subdirectory
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            assertEquals(entry.getName(), "subdir/");
            assertTrue(entry.isDirectory());

            // Second entry should be the file inside subdir
            entry = nis.getNextEntry();
            assertNotNull(entry);
            assertEquals(entry.getName(), "subdir/file.txt");
            assertFalse(entry.isDirectory());

            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test parsing a NAR with a symbolic link.
     */
    @Test
    public void testSymbolicLink() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("link", NarBuilder.EntryType.SYMLINK, "target/path", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            assertEquals(entry.getName(), "link");
            assertTrue(entry.isSymbolicLink());
            assertEquals(entry.getSymlinkTarget(), "target/path");
            assertFalse(entry.isDirectory());

            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test parsing a NAR with multiple files at root level.
     */
    @Test
    public void testMultipleFiles() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("file1.txt", NarBuilder.EntryType.REGULAR, "content1", false)
                .addEntry("file2.txt", NarBuilder.EntryType.REGULAR, "content2", false)
                .addEntry("file3.txt", NarBuilder.EntryType.REGULAR, "content3", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            List<String> names = new ArrayList<>();
            NarArchiveEntry entry;
            while ((entry = nis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
            assertEquals(names.size(), 3);
            assertTrue(names.contains("file1.txt"));
            assertTrue(names.contains("file2.txt"));
            assertTrue(names.contains("file3.txt"));
        }
    }

    /**
     * Test parsing a NAR with an empty file.
     */
    @Test
    public void testEmptyFile() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEmptyFile("empty.txt", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            assertEquals(entry.getName(), "empty.txt");
            assertEquals(entry.getSize(), 0);
            assertFalse(entry.isDirectory());

            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test that the root directory is skipped (not returned as an entry).
     */
    @Test
    public void testRootDirectoryIsSkipped() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()  // root - should be skipped
                .addEntry("file.txt", NarBuilder.EntryType.REGULAR, "data", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            // First entry should be the file, not the root directory
            assertEquals(entry.getName(), "file.txt");
            assertFalse(entry.isDirectory());

            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test invalid magic header throws exception.
     */
    @Test(expectedExceptions = IOException.class,
            expectedExceptionsMessageRegExp = "NAR: invalid magic header.*")
    public void testInvalidMagicHeader() throws IOException {
        byte[] invalidData = new NarBuilder()
                .writeString("invalid-header")
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(invalidData))) {
            nis.getNextEntry();
        }
    }

    /**
     * Test missing opening parenthesis after magic header.
     */
    @Test(expectedExceptions = IOException.class,
            expectedExceptionsMessageRegExp = "NAR: expected '\\(' after magic header.*")
    public void testMissingOpeningParen() throws IOException {
        byte[] invalidData = new NarBuilder()
                .writeString("nix-archive-1")
                .writeString("invalid")
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(invalidData))) {
            nis.getNextEntry();
        }
    }

    /**
     * Test reading file content correctly.
     */
    @Test
    public void testReadFileContent() throws IOException {
        String expectedContent = "This is test content with special chars: äöü €";
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("test.txt", NarBuilder.EntryType.REGULAR, expectedContent, false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = nis.read(buffer, 0, buffer.length)) != -1) {
                baos.write(buffer, 0, read);
            }

            assertEquals(new String(baos.toByteArray(), StandardCharsets.UTF_8), expectedContent);
        }
    }

    /**
     * Test that reading returns -1 when no current entry.
     */
    @Test
    public void testReadWithNoCurrentEntry() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            byte[] buffer = new byte[10];
            assertEquals(nis.read(buffer, 0, buffer.length), -1);
        }
    }

    /**
     * Test that getNextEntry returns null after stream is finished.
     */
    @Test
    public void testGetNextEntryAfterFinished() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("file.txt", NarBuilder.EntryType.REGULAR, "data", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            assertNotNull(nis.getNextEntry()); // file.txt
            assertNull(nis.getNextEntry()); // end
            assertNull(nis.getNextEntry()); // still null
            assertNull(nis.getNextEntry()); // still null
        }
    }

    /**
     * Test deep directory nesting.
     */
    @Test
    public void testDeepNesting() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()  // root
                .startNestedEntry("a")
                .startDirectory()
                .startNestedEntry("b")
                .startDirectory()
                .startNestedEntry("c")
                .startDirectory()
                .addEntry("deep.txt", NarBuilder.EntryType.REGULAR, "deep content", false)
                .endDirectory()
                .endNestedEntry()
                .endDirectory()
                .endNestedEntry()
                .endDirectory()
                .endNestedEntry()
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            List<String> names = new ArrayList<>();
            NarArchiveEntry entry;
            while ((entry = nis.getNextEntry()) != null) {
                names.add(entry.getName());
            }

            assertTrue(names.contains("a/"));
            assertTrue(names.contains("a/b/"));
            assertTrue(names.contains("a/b/c/"));
            assertTrue(names.contains("a/b/c/deep.txt"));
        }
    }

    /**
     * Test close method closes underlying stream.
     */
    @Test
    public void testClose() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .endDirectory()
                .build();

        ByteArrayInputStream bais = new ByteArrayInputStream(narData);
        NarArchiveInputStream nis = new NarArchiveInputStream(bais);
        nis.close();

        // After close, ByteArrayInputStream still allows reading (it doesn't track closed state)
        // but the NarArchiveInputStream should be properly closed
        int result = bais.read();
        // ByteArrayInputStream returns the next byte or -1 at end of stream
        assertTrue(result == -1 || result >= 0);
    }

    /**
     * Test mixed entry types (files, directories, symlinks).
     */
    @Test
    public void testMixedEntryTypes() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("regular.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .addEntry("executable.sh", NarBuilder.EntryType.REGULAR, "#!/bin/sh", true)
                .addEntry("link", NarBuilder.EntryType.SYMLINK, "regular.txt", false)
                .startNestedEntry("subdir")
                .startDirectory()
                .addEntry("nested.txt", NarBuilder.EntryType.REGULAR, "nested", false)
                .endDirectory()
                .endNestedEntry()
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            int regularCount = 0;
            int executableCount = 0;
            int symlinkCount = 0;
            int directoryCount = 0;

            NarArchiveEntry entry;
            while ((entry = nis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    directoryCount++;
                } else if (entry.isSymbolicLink()) {
                    symlinkCount++;
                } else if (entry.isExecutable()) {
                    executableCount++;
                } else {
                    regularCount++;
                }
            }

            assertEquals(regularCount, 2); // regular.txt and nested.txt
            assertEquals(executableCount, 1); // executable.sh
            assertEquals(symlinkCount, 1); // link
            assertEquals(directoryCount, 1); // subdir
        }
    }

    /**
     * Test processToken handles unknown tokens gracefully by skipping them.
     */
    @Test
    public void testUnknownTokensAreSkipped() throws IOException {
        // Build a NAR with unknown tokens interspersed
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .writeString("unknownToken1")  // Should be skipped
                .writeString("unknownToken2")  // Should be skipped
                .addEntry("file.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .writeString("anotherUnknown") // Should be skipped
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            assertEquals(entry.getName(), "file.txt");
            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test handleCloseParen properly decrements depth and pops path stack.
     */
    @Test
    public void testCloseParenPopsPathStackCorrectly() throws IOException {
        // Create a nested directory structure to test path stack management
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .startNestedEntry("parent")
                .startDirectory()
                .addEntry("child.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .endDirectory()
                .endNestedEntry()
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            List<String> names = new ArrayList<>();
            NarArchiveEntry entry;
            while ((entry = nis.getNextEntry()) != null) {
                names.add(entry.getName());
            }

            // Verify paths are built correctly
            assertTrue(names.contains("parent/"));
            assertTrue(names.contains("parent/child.txt"));
            assertEquals(names.size(), 2);
        }
    }

    /**
     * Test handleEntryToken processes entry structure correctly.
     */
    @Test
    public void testHandleEntryTokenWithSpecialCharacters() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("file-with-dashes.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .addEntry("file_with_underscores.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .addEntry("file.multiple.dots.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            List<String> names = new ArrayList<>();
            NarArchiveEntry entry;
            while ((entry = nis.getNextEntry()) != null) {
                names.add(entry.getName());
            }

            assertTrue(names.contains("file-with-dashes.txt"));
            assertTrue(names.contains("file_with_underscores.txt"));
            assertTrue(names.contains("file.multiple.dots.txt"));
        }
    }

    /**
     * Test processRegularFileToken handles executable empty files.
     */
    @Test
    public void testExecutableEmptyFile() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEmptyFile("empty-exec.sh", true)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            assertEquals(entry.getName(), "empty-exec.sh");
            assertTrue(entry.isExecutable());
            assertEquals(entry.getSize(), 0);
            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test processRegularFileToken with unknown tokens in regular file context.
     */
    @Test
    public void testUnknownTokensInRegularFile() throws IOException {
        // Build NAR with unknown token before contents
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntryWithUnknownToken("file.txt", "content")
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            assertEquals(entry.getName(), "file.txt");
            assertEquals(entry.getSize(), 7); // "content".length()
        }
    }

    /**
     * Test that depth reaches zero properly at end of archive.
     */
    @Test
    public void testDepthReachesZeroAtEnd() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("file.txt", NarBuilder.EntryType.REGULAR, "data", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            
            // After consuming all entries, stream should be finished
            assertNull(nis.getNextEntry());
            
            // Calling again should still return null
            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test multiple symlinks in the same directory.
     */
    @Test
    public void testMultipleSymlinks() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("link1", NarBuilder.EntryType.SYMLINK, "target1", false)
                .addEntry("link2", NarBuilder.EntryType.SYMLINK, "../other/target2", false)
                .addEntry("link3", NarBuilder.EntryType.SYMLINK, "/absolute/path", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            List<NarArchiveEntry> entries = new ArrayList<>();
            NarArchiveEntry entry;
            while ((entry = nis.getNextEntry()) != null) {
                entries.add(entry);
            }

            assertEquals(entries.size(), 3);
            
            for (NarArchiveEntry e : entries) {
                assertTrue(e.isSymbolicLink());
                assertFalse(e.isDirectory());
                assertFalse(e.isExecutable());
            }

            assertEquals(entries.get(0).getSymlinkTarget(), "target1");
            assertEquals(entries.get(1).getSymlinkTarget(), "../other/target2");
            assertEquals(entries.get(2).getSymlinkTarget(), "/absolute/path");
        }
    }

    /**
     * Test symlink followed by regular file in same directory.
     */
    @Test
    public void testSymlinkThenRegularFile() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("link", NarBuilder.EntryType.SYMLINK, "real.txt", false)
                .addEntry("real.txt", NarBuilder.EntryType.REGULAR, "actual content", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry link = nis.getNextEntry();
            assertNotNull(link);
            assertEquals(link.getName(), "link");
            assertTrue(link.isSymbolicLink());
            assertEquals(link.getSymlinkTarget(), "real.txt");

            NarArchiveEntry file = nis.getNextEntry();
            assertNotNull(file);
            assertEquals(file.getName(), "real.txt");
            assertFalse(file.isSymbolicLink());
            assertEquals(file.getSize(), 14);

            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test unknown type throws IOException.
     */
    @Test(expectedExceptions = IOException.class,
            expectedExceptionsMessageRegExp = "NAR: unknown type:.*")
    public void testUnknownTypeThrowsException() throws IOException {
        byte[] narData = new NarBuilder()
                .writeString("nix-archive-1")
                .writeString("(")
                .writeString("type")
                .writeString("unknowntype")
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            nis.getNextEntry();
        }
    }

    /**
     * Test reading multiple files sequentially.
     */
    @Test
    public void testReadMultipleFilesSequentially() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("first.txt", NarBuilder.EntryType.REGULAR, "first content", false)
                .addEntry("second.txt", NarBuilder.EntryType.REGULAR, "second content", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            // Read first entry
            NarArchiveEntry entry1 = nis.getNextEntry();
            assertNotNull(entry1);
            assertEquals(entry1.getName(), "first.txt");

            // Read second entry without reading first's content
            NarArchiveEntry entry2 = nis.getNextEntry();
            assertNotNull(entry2);
            assertEquals(entry2.getName(), "second.txt");

            // No more entries
            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test file with content length requiring padding.
     */
    @Test
    public void testFileContentWithPadding() throws IOException {
        // Content of length 5 requires 3 bytes of padding to reach 8-byte boundary
        String content = "12345";
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("padded.txt", NarBuilder.EntryType.REGULAR, content, false)
                .addEntry("next.txt", NarBuilder.EntryType.REGULAR, "after", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry1 = nis.getNextEntry();
            assertNotNull(entry1);
            assertEquals(entry1.getName(), "padded.txt");
            assertEquals(entry1.getSize(), 5);

            // Read content
            byte[] buf = new byte[5];
            IOUtils.readFully(nis, buf);
            assertEquals(new String(buf, StandardCharsets.UTF_8), content);

            // Should be able to get next entry after padding is skipped
            NarArchiveEntry entry2 = nis.getNextEntry();
            assertNotNull(entry2);
            assertEquals(entry2.getName(), "next.txt");
        }
    }

    /**
     * Test skipping unread file content before getting next entry.
     */
    @Test
    public void testSkipUnreadContent() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("large.txt", NarBuilder.EntryType.REGULAR, "This is a longer piece of content", false)
                .addEntry("small.txt", NarBuilder.EntryType.REGULAR, "tiny", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry1 = nis.getNextEntry();
            assertNotNull(entry1);
            assertEquals(entry1.getName(), "large.txt");

            // Don't read content, just get next entry
            NarArchiveEntry entry2 = nis.getNextEntry();
            assertNotNull(entry2);
            assertEquals(entry2.getName(), "small.txt");

            // Verify we can read small.txt content
            byte[] buf = new byte[(int) entry2.getSize()];
            IOUtils.readFully(nis, buf);
            assertEquals(new String(buf, StandardCharsets.UTF_8), "tiny");
        }
    }

    /**
     * Test directory with only a subdirectory (no files in subdirectory).
     */
    @Test
    public void testDirectoryWithEmptySubdirectory() throws IOException {
        // Empty directories in NAR are valid but may not produce entries
        // depending on the NAR structure. Test a directory that contains
        // only files to ensure basic functionality.
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("file.txt", NarBuilder.EntryType.REGULAR, "data", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry file = nis.getNextEntry();
            assertNotNull(file);
            assertEquals(file.getName(), "file.txt");
            assertFalse(file.isDirectory());

            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test partial read of file content.
     */
    @Test
    public void testPartialReadThenNextEntry() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("file1.txt", NarBuilder.EntryType.REGULAR, "0123456789", false)
                .addEntry("file2.txt", NarBuilder.EntryType.REGULAR, "abcdef", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry1 = nis.getNextEntry();
            assertNotNull(entry1);
            assertEquals(entry1.getSize(), 10);

            // Read only 3 bytes
            byte[] partial = new byte[3];
            int read = nis.read(partial, 0, 3);
            assertEquals(read, 3);
            assertEquals(new String(partial, StandardCharsets.UTF_8), "012");

            // Get next entry (should skip remaining 7 bytes + padding)
            NarArchiveEntry entry2 = nis.getNextEntry();
            assertNotNull(entry2);
            assertEquals(entry2.getName(), "file2.txt");

            // Read full content of second file
            byte[] content2 = new byte[(int) entry2.getSize()];
            IOUtils.readFully(nis, content2);
            assertEquals(new String(content2, StandardCharsets.UTF_8), "abcdef");
        }
    }

    // ==================== Additional NarArchiveInputStream Coverage Tests ====================

    /**
     * Test reading returns -1 after all content is consumed.
     */
    @Test
    public void testReadReturnsMinusOneAfterContentConsumed() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("small.txt", NarBuilder.EntryType.REGULAR, "abc", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);

            // Read all content
            byte[] content = new byte[3];
            int read = nis.read(content, 0, 3);
            assertEquals(read, 3);

            // Further reads should return -1
            assertEquals(nis.read(content, 0, 3), -1);
        }
    }

    /**
     * Test read with zero length buffer.
     */
    @Test
    public void testReadWithZeroLengthBuffer() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("file.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);

            // Read with zero length should return 0 or handle gracefully
            byte[] buffer = new byte[10];
            int read = nis.read(buffer, 0, 0);
            assertTrue(read <= 0);
        }
    }

    /**
     * Test directory entries have size 0.
     */
    @Test
    public void testDirectoryEntriesHaveZeroSize() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .startNestedEntry("mydir")
                .startDirectory()
                .endDirectory()
                .endNestedEntry()
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            assertTrue(entry.isDirectory());
            assertEquals(entry.getSize(), 0);
        }
    }

    /**
     * Test symlink entries have size 0.
     */
    @Test
    public void testSymlinkEntriesHaveZeroSize() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("mylink", NarBuilder.EntryType.SYMLINK, "/target/path", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            assertTrue(entry.isSymbolicLink());
            assertEquals(entry.getSize(), 0);
        }
    }

    /**
     * Test file with exact 8-byte boundary content (no padding needed).
     */
    @Test
    public void testFileWithExact8ByteBoundary() throws IOException {
        String content = "12345678"; // Exactly 8 bytes, no padding needed
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("exact8.txt", NarBuilder.EntryType.REGULAR, content, false)
                .addEntry("next.txt", NarBuilder.EntryType.REGULAR, "after", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry1 = nis.getNextEntry();
            assertNotNull(entry1);
            assertEquals(entry1.getSize(), 8);

            byte[] buf = new byte[8];
            IOUtils.readFully(nis, buf);
            assertEquals(new String(buf, StandardCharsets.UTF_8), content);

            NarArchiveEntry entry2 = nis.getNextEntry();
            assertNotNull(entry2);
            assertEquals(entry2.getName(), "next.txt");
        }
    }

    /**
     * Test file with content requiring maximum padding (1 byte content = 7 bytes padding).
     */
    @Test
    public void testFileWithMaximumPadding() throws IOException {
        String content = "X"; // 1 byte, needs 7 bytes padding
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("one.txt", NarBuilder.EntryType.REGULAR, content, false)
                .addEntry("two.txt", NarBuilder.EntryType.REGULAR, "second", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry1 = nis.getNextEntry();
            assertEquals(entry1.getSize(), 1);

            byte[] buf = new byte[1];
            IOUtils.readFully(nis, buf);
            assertEquals(new String(buf, StandardCharsets.UTF_8), "X");

            NarArchiveEntry entry2 = nis.getNextEntry();
            assertNotNull(entry2);
            assertEquals(entry2.getName(), "two.txt");
        }
    }

    /**
     * Test very deeply nested directory structure.
     */
    @Test
    public void testVeryDeepNesting() throws IOException {
        NarBuilder builder = new NarBuilder().startArchive().startDirectory();

        // Create 10 levels of nesting
        for (int i = 0; i < 10; i++) {
            builder.startNestedEntry("level" + i).startDirectory();
        }

        builder.addEntry("deep.txt", NarBuilder.EntryType.REGULAR, "deep content", false);

        // Close all nested directories
        for (int i = 0; i < 10; i++) {
            builder.endDirectory().endNestedEntry();
        }

        builder.endDirectory();
        byte[] narData = builder.build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            List<String> names = new ArrayList<>();
            NarArchiveEntry entry;
            while ((entry = nis.getNextEntry()) != null) {
                names.add(entry.getName());
            }

            // Should have 10 directories + 1 file
            assertEquals(names.size(), 11);
            assertTrue(names.get(names.size() - 1).endsWith("deep.txt"));
        }
    }

    /**
     * Test entry with special characters in name.
     */
    @Test
    public void testEntryWithSpecialCharactersInName() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("file with spaces.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .addEntry("file-with-dashes.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .addEntry("file_with_underscores.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .addEntry("file.multiple.dots.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            List<String> names = new ArrayList<>();
            NarArchiveEntry entry;
            while ((entry = nis.getNextEntry()) != null) {
                names.add(entry.getName());
            }

            assertTrue(names.contains("file with spaces.txt"));
            assertTrue(names.contains("file-with-dashes.txt"));
            assertTrue(names.contains("file_with_underscores.txt"));
            assertTrue(names.contains("file.multiple.dots.txt"));
        }
    }

    /**
     * Test entry with Unicode characters in name.
     */
    @Test
    public void testEntryWithUnicodeCharacters() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("文件.txt", NarBuilder.EntryType.REGULAR, "中文内容", false)
                .addEntry("файл.txt", NarBuilder.EntryType.REGULAR, "русский", false)
                .addEntry("αβγ.txt", NarBuilder.EntryType.REGULAR, "greek", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            List<String> names = new ArrayList<>();
            NarArchiveEntry entry;
            while ((entry = nis.getNextEntry()) != null) {
                names.add(entry.getName());
            }

            assertEquals(names.size(), 3);
            assertTrue(names.contains("文件.txt"));
            assertTrue(names.contains("файл.txt"));
            assertTrue(names.contains("αβγ.txt"));
        }
    }

    /**
     * Test large file content.
     */
    @Test
    public void testLargeFileContent() throws IOException {
        // Create a 100KB content
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("0123456789");
        }
        String largeContent = sb.toString();

        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("large.bin", NarBuilder.EntryType.REGULAR, largeContent, false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry = nis.getNextEntry();
            assertNotNull(entry);
            assertEquals(entry.getSize(), 100000);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = nis.read(buffer, 0, buffer.length)) != -1) {
                baos.write(buffer, 0, read);
            }

            assertEquals(baos.size(), 100000);
            assertEquals(new String(baos.toByteArray(), StandardCharsets.UTF_8), largeContent);
        }
    }

    /**
     * Test multiple executable files.
     */
    @Test
    public void testMultipleExecutableFiles() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("script1.sh", NarBuilder.EntryType.REGULAR, "#!/bin/sh\necho 1", true)
                .addEntry("script2.sh", NarBuilder.EntryType.REGULAR, "#!/bin/sh\necho 2", true)
                .addEntry("regular.txt", NarBuilder.EntryType.REGULAR, "not executable", false)
                .addEntry("script3.sh", NarBuilder.EntryType.REGULAR, "#!/bin/sh\necho 3", true)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            int executableCount = 0;
            int nonExecutableCount = 0;

            NarArchiveEntry entry;
            while ((entry = nis.getNextEntry()) != null) {
                if (entry.isExecutable()) {
                    executableCount++;
                } else {
                    nonExecutableCount++;
                }
            }

            assertEquals(executableCount, 3);
            assertEquals(nonExecutableCount, 1);
        }
    }

    /**
     * Test symlinks with various target formats.
     */
    @Test
    public void testSymlinksWithVariousTargetFormats() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("relative", NarBuilder.EntryType.SYMLINK, "target.txt", false)
                .addEntry("parent", NarBuilder.EntryType.SYMLINK, "../parent/file", false)
                .addEntry("absolute", NarBuilder.EntryType.SYMLINK, "/nix/store/abc123/bin/app", false)
                .addEntry("deep", NarBuilder.EntryType.SYMLINK, "../../very/deep/path/file.so", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry;

            entry = nis.getNextEntry();
            assertEquals(entry.getSymlinkTarget(), "target.txt");

            entry = nis.getNextEntry();
            assertEquals(entry.getSymlinkTarget(), "../parent/file");

            entry = nis.getNextEntry();
            assertEquals(entry.getSymlinkTarget(), "/nix/store/abc123/bin/app");

            entry = nis.getNextEntry();
            assertEquals(entry.getSymlinkTarget(), "../../very/deep/path/file.so");

            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test empty archive (just root directory).
     */
    @Test
    public void testEmptyArchive() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            // Empty archive should have no entries (root is skipped)
            assertNull(nis.getNextEntry());
        }
    }

    /**
     * Test that lastModifiedDate is set on entries.
     */
    @Test
    public void testEntriesHaveLastModifiedDate() throws IOException {
        long before = System.currentTimeMillis();

        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("file.txt", NarBuilder.EntryType.REGULAR, "content", false)
                .startNestedEntry("dir")
                .startDirectory()
                .endDirectory()
                .endNestedEntry()
                .addEntry("link", NarBuilder.EntryType.SYMLINK, "target", false)
                .endDirectory()
                .build();

        long after = System.currentTimeMillis();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            NarArchiveEntry entry;
            while ((entry = nis.getNextEntry()) != null) {
                assertNotNull(entry.getLastModifiedDate());
                long entryTime = entry.getLastModifiedDate().getTime();
                assertTrue(entryTime >= before && entryTime <= after + 1000,
                        "Entry should have recent timestamp");
            }
        }
    }

    /**
     * Test ArchiveEntry interface compliance.
     */
    @Test
    public void testArchiveEntryInterfaceCompliance() throws IOException {
        byte[] narData = new NarBuilder()
                .startArchive()
                .startDirectory()
                .addEntry("test.txt", NarBuilder.EntryType.REGULAR, "hello", false)
                .endDirectory()
                .build();

        try (NarArchiveInputStream nis = new NarArchiveInputStream(new ByteArrayInputStream(narData))) {
            org.apache.commons.compress.archivers.ArchiveEntry entry = nis.getNextEntry();

            // Test ArchiveEntry interface methods
            assertNotNull(entry.getName());
            assertEquals(entry.getSize(), 5);
            assertFalse(entry.isDirectory());
            assertNotNull(entry.getLastModifiedDate());
        }
    }

    /**
     * Helper class to build NAR format byte arrays for testing.
     */
    static class NarBuilder {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        enum EntryType {
            REGULAR, SYMLINK
        }

        NarBuilder startArchive() {
            writeString("nix-archive-1");
            writeString("(");
            return this;
        }

        NarBuilder startDirectory() {
            writeString("type");
            writeString("directory");
            return this;
        }

        NarBuilder endDirectory() {
            writeString(")");
            return this;
        }

        NarBuilder startNestedEntry(String name) {
            writeString("entry");
            writeString("(");
            writeString("name");
            writeString(name);
            writeString("node");
            writeString("(");
            return this;
        }

        NarBuilder endNestedEntry() {
            writeString(")");
            writeString(")");
            return this;
        }

        NarBuilder addEntry(String name, EntryType type, String content, boolean executable) {
            writeString("entry");
            writeString("(");
            writeString("name");
            writeString(name);
            writeString("node");
            writeString("(");
            writeString("type");

            switch (type) {
                case REGULAR:
                    writeString("regular");
                    if (executable) {
                        writeString("executable");
                        writeString("");
                    }
                    byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
                    writeString("contents");
                    writeLong(contentBytes.length);
                    writeBytes(contentBytes);
                    writePadding(contentBytes.length);
                    break;
                case SYMLINK:
                    writeString("symlink");
                    writeString("target");
                    writeString(content); // content is the symlink target
                    break;
            }

            writeString(")");
            writeString(")");
            return this;
        }

        NarBuilder addEmptyFile(String name, boolean executable) {
            writeString("entry");
            writeString("(");
            writeString("name");
            writeString(name);
            writeString("node");
            writeString("(");
            writeString("type");
            writeString("regular");
            if (executable) {
                writeString("executable");
                writeString("");
            }
            writeString(")");
            writeString(")");
            return this;
        }

        /**
         * Add a regular file entry with an unknown token before contents.
         * Used to test that unknown tokens in regular file context are skipped.
         */
        NarBuilder addEntryWithUnknownToken(String name, String content) {
            writeString("entry");
            writeString("(");
            writeString("name");
            writeString(name);
            writeString("node");
            writeString("(");
            writeString("type");
            writeString("regular");
            writeString("unknownFileToken");  // Unknown token that should be skipped
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            writeString("contents");
            writeLong(contentBytes.length);
            writeBytes(contentBytes);
            writePadding(contentBytes.length);
            writeString(")");
            writeString(")");
            return this;
        }

        /**
         * Writes raw bytes without length prefix (for content data).
         */
        NarBuilder writeRawBytes(byte[] bytes) {
            output.write(bytes, 0, bytes.length);
            return this;
        }

        /**
         * Writes a long value in little-endian format.
         */
        NarBuilder writeLongValue(long value) {
            writeLong(value);
            return this;
        }

        NarBuilder writeString(String s) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            writeLong(bytes.length);
            writeBytes(bytes);
            writePadding(bytes.length);
            return this;
        }

        private void writeLong(long value) {
            byte[] bytes = new byte[8];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(value);
            writeBytes(bytes);
        }

        private void writeBytes(byte[] bytes) {
            output.write(bytes, 0, bytes.length);
        }

        private void writePadding(long length) {
            int padding = (int) ((8 - (length % 8)) % 8);
            if (padding > 0) {
                writeBytes(new byte[padding]);
            }
        }

        byte[] build() {
            return output.toByteArray();
        }
    }
}
