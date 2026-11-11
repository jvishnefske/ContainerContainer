package org.artifactory.descriptor.download;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Tom Arie
 */
public class FolderDownloadConfigDescriptorTest {

    @Test
    public void testDefaultValues()  {
        FolderDownloadConfigDescriptor folderDownloadConfigDescriptor = new FolderDownloadConfigDescriptor();

        assertFalse(folderDownloadConfigDescriptor.isEnabled());
        assertFalse(folderDownloadConfigDescriptor.isEnabledForAnonymous());
        assertEquals(folderDownloadConfigDescriptor.getMaxDownloadSizeMb(),1024);
        assertEquals(folderDownloadConfigDescriptor.getMaxFiles(), 5000);
        assertEquals(folderDownloadConfigDescriptor.getMaxConcurrentRequests(), 10);
        assertFalse(folderDownloadConfigDescriptor.isEnabledEmptyDirectories());
    }

    @Test
    public void testSetters()  {
        FolderDownloadConfigDescriptor folderDownloadConfigDescriptor = new FolderDownloadConfigDescriptor();

        folderDownloadConfigDescriptor.setEnabled(true);
        folderDownloadConfigDescriptor.setEnabledEmptyDirectories(true);
        folderDownloadConfigDescriptor.setEnabledForAnonymous(true);
        folderDownloadConfigDescriptor.setMaxFiles(1000);
        folderDownloadConfigDescriptor.setMaxDownloadSizeMb(512);
        folderDownloadConfigDescriptor.setMaxConcurrentRequests(5);

        assertTrue(folderDownloadConfigDescriptor.isEnabled());
        assertTrue(folderDownloadConfigDescriptor.isEnabledEmptyDirectories());
        assertTrue(folderDownloadConfigDescriptor.isEnabledForAnonymous());
        assertEquals(folderDownloadConfigDescriptor.getMaxFiles(), 1000);
        assertEquals(folderDownloadConfigDescriptor.getMaxDownloadSizeMb(), 512);
        assertEquals(folderDownloadConfigDescriptor.getMaxConcurrentRequests(), 5);
    }
}