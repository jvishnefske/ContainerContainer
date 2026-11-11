package org.artifactory.util;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link NarArchiveEntry}.
 *
 * @author Natalie Berni
 */
@Test
public class NarArchiveEntryTest {

    @Test
    public void testRegularFileEntry() {
        NarArchiveEntry entry = new NarArchiveEntry("path/to/file.txt", 1024, false, false, null);

        assertEquals(entry.getName(), "path/to/file.txt");
        assertEquals(entry.getSize(), 1024);
        assertFalse(entry.isDirectory());
        assertFalse(entry.isExecutable());
        assertFalse(entry.isSymbolicLink());
        assertNull(entry.getSymlinkTarget());
        assertNotNull(entry.getLastModifiedDate());
    }

    @Test
    public void testExecutableFileEntry() {
        NarArchiveEntry entry = new NarArchiveEntry("bin/script.sh", 512, false, true, null);

        assertEquals(entry.getName(), "bin/script.sh");
        assertEquals(entry.getSize(), 512);
        assertFalse(entry.isDirectory());
        assertTrue(entry.isExecutable());
        assertFalse(entry.isSymbolicLink());
        assertNull(entry.getSymlinkTarget());
    }

    @Test
    public void testDirectoryEntry() {
        NarArchiveEntry entry = new NarArchiveEntry("path/to/directory/", 0, true, false, null);

        assertEquals(entry.getName(), "path/to/directory/");
        assertEquals(entry.getSize(), 0);
        assertTrue(entry.isDirectory());
        assertFalse(entry.isExecutable());
        assertFalse(entry.isSymbolicLink());
        assertNull(entry.getSymlinkTarget());
    }

    @Test
    public void testSymlinkEntry() {
        NarArchiveEntry entry = new NarArchiveEntry("lib/libfoo.so", 0, false, false, "libfoo.so.1.2.3");

        assertEquals(entry.getName(), "lib/libfoo.so");
        assertEquals(entry.getSize(), 0);
        assertFalse(entry.isDirectory());
        assertFalse(entry.isExecutable());
        assertTrue(entry.isSymbolicLink());
        assertEquals(entry.getSymlinkTarget(), "libfoo.so.1.2.3");
    }

    @Test
    public void testEmptyFile() {
        NarArchiveEntry entry = new NarArchiveEntry("empty.txt", 0, false, false, null);

        assertEquals(entry.getName(), "empty.txt");
        assertEquals(entry.getSize(), 0);
        assertFalse(entry.isDirectory());
        assertFalse(entry.isExecutable());
        assertFalse(entry.isSymbolicLink());
    }

    @Test
    public void testToString() {
        NarArchiveEntry entry = new NarArchiveEntry("test.txt", 100, false, true, null);
        String str = entry.toString();

        assertTrue(str.contains("test.txt"));
        assertTrue(str.contains("100"));
        assertTrue(str.contains("executable=true"));
        assertTrue(str.contains("directory=false"));
    }

    @Test
    public void testToStringWithSymlink() {
        NarArchiveEntry entry = new NarArchiveEntry("link", 0, false, false, "target");
        String str = entry.toString();

        assertTrue(str.contains("link"));
        assertTrue(str.contains("symlinkTarget=target"));
    }

    @Test
    public void testLastModifiedDateIsSet() {
        long before = System.currentTimeMillis();
        NarArchiveEntry entry = new NarArchiveEntry("file.txt", 0, false, false, null);
        long after = System.currentTimeMillis();

        assertNotNull(entry.getLastModifiedDate());
        long entryTime = entry.getLastModifiedDate().getTime();
        assertTrue(entryTime >= before && entryTime <= after,
                "Last modified date should be set to current time");
    }

    @Test
    public void testNestedPath() {
        NarArchiveEntry entry = new NarArchiveEntry("a/b/c/d/e/file.txt", 256, false, false, null);

        assertEquals(entry.getName(), "a/b/c/d/e/file.txt");
        assertFalse(entry.isDirectory());
    }

    @Test
    public void testRootLevelFile() {
        NarArchiveEntry entry = new NarArchiveEntry("rootfile.txt", 128, false, false, null);

        assertEquals(entry.getName(), "rootfile.txt");
        assertFalse(entry.isDirectory());
    }

    @Test
    public void testLargeFileSize() {
        long largeSize = 10L * 1024 * 1024 * 1024; // 10 GB
        NarArchiveEntry entry = new NarArchiveEntry("large.bin", largeSize, false, false, null);

        assertEquals(entry.getSize(), largeSize);
    }

    @Test
    public void testRelativeSymlinkTarget() {
        NarArchiveEntry entry = new NarArchiveEntry("current/link", 0, false, false, "../other/target");

        assertTrue(entry.isSymbolicLink());
        assertEquals(entry.getSymlinkTarget(), "../other/target");
    }

    @Test
    public void testAbsoluteSymlinkTarget() {
        NarArchiveEntry entry = new NarArchiveEntry("link", 0, false, false, "/nix/store/abc123/bin/app");

        assertTrue(entry.isSymbolicLink());
        assertEquals(entry.getSymlinkTarget(), "/nix/store/abc123/bin/app");
    }

    /**
     * Test that Lombok @Getter generates getLastModified() and it matches getLastModifiedDate().
     */
    @Test
    public void testLombokGetLastModifiedMatchesInterface() {
        NarArchiveEntry entry = new NarArchiveEntry("file.txt", 100, false, false, null);

        // Lombok generates getLastModified(), interface requires getLastModifiedDate()
        // Both should return the same value
        assertNotNull(entry.getLastModified());
        assertNotNull(entry.getLastModifiedDate());
        assertEquals(entry.getLastModified(), entry.getLastModifiedDate());
    }

    /**
     * Test Lombok @ToString excludes lastModified field.
     */
    @Test
    public void testToStringExcludesLastModified() {
        NarArchiveEntry entry = new NarArchiveEntry("test.txt", 100, false, false, null);
        String str = entry.toString();

        // toString should not contain lastModified
        assertFalse(str.contains("lastModified"));
    }

    /**
     * Test Lombok @ToString includes all expected fields.
     */
    @Test
    public void testToStringIncludesAllFields() {
        NarArchiveEntry entry = new NarArchiveEntry("myfile.txt", 999, true, true, "symtarget");
        String str = entry.toString();

        assertTrue(str.contains("name=myfile.txt"));
        assertTrue(str.contains("size=999"));
        assertTrue(str.contains("directory=true"));
        assertTrue(str.contains("executable=true"));
        assertTrue(str.contains("symlinkTarget=symtarget"));
    }

    /**
     * Test Lombok @Getter for all fields.
     */
    @Test
    public void testLombokGettersForAllFields() {
        NarArchiveEntry entry = new NarArchiveEntry("path/file.dat", 2048, true, true, "target/link");

        // Verify all Lombok-generated getters work
        assertEquals(entry.getName(), "path/file.dat");
        assertEquals(entry.getSize(), 2048);
        assertTrue(entry.isDirectory());
        assertTrue(entry.isExecutable());
        assertEquals(entry.getSymlinkTarget(), "target/link");
        assertNotNull(entry.getLastModified());
    }

    /**
     * Test null symlink target for non-symlink entries.
     */
    @Test
    public void testNullSymlinkTargetForRegularFile() {
        NarArchiveEntry entry = new NarArchiveEntry("regular.txt", 100, false, false, null);

        assertNull(entry.getSymlinkTarget());
        assertFalse(entry.isSymbolicLink());
    }

    /**
     * Test toString with null symlink target.
     */
    @Test
    public void testToStringWithNullSymlinkTarget() {
        NarArchiveEntry entry = new NarArchiveEntry("file.txt", 50, false, false, null);
        String str = entry.toString();

        assertTrue(str.contains("symlinkTarget=null"));
    }
}
