package org.artifactory.repo.service.trash;

import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.TrashCleanupCandidate;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Oleksandr Legenkiy
 */
@Test
public class TrashGCHelperImplTest extends ArtifactoryHomeBoundTest {
    private TrashGCHelperImpl helper;

    @BeforeMethod
    public void before() {
        this.helper = new TrashGCHelperImpl();
    }

    @Test
    public void trashGcHelperQueueWorksAsIntended() {
        long maxQueueSize = ConstantValues.gcTrashBinariesCleanupMaxQueueLength.getLong();
        // Add 10001 elements with lengths from 1 to 10001
        LongStream.range(1, maxQueueSize + 2).mapToObj(curr -> this.createFileInfoMock(curr, false))
                .forEach(this.helper::markTrashFileForGC);
        List<TrashCleanupCandidate> trashFilesMarkedForGC = this.helper.getTrashFilesMarkedForGC();
        assertNotNull(trashFilesMarkedForGC);
        assertFalse(trashFilesMarkedForGC.isEmpty());
        assertEquals(trashFilesMarkedForGC.size(), maxQueueSize);
        // The last and the largest file was added successfully
        assertTrue(trashFilesMarkedForGC.stream().anyMatch(item -> item.getLength() == maxQueueSize + 1));
        // The first and the shortest file was removed
        assertTrue(trashFilesMarkedForGC.stream().noneMatch(item -> item.getLength() == 1));
        // The second shortest item is kept in the list
        assertTrue(trashFilesMarkedForGC.stream().anyMatch(item -> item.getLength() == 2));
    }

    @Test
    public void deleteFolderNothingHappens() {
        this.helper.markTrashFileForGC(this.createFileInfoMock(1, true));
        assertTrue(this.helper.getTrashFilesMarkedForGC().isEmpty());
    }

    private FileInfo createFileInfoMock(long length, boolean isFolder) {
        FileInfo fileInfoMock = mock(FileInfo.class);
        when(fileInfoMock.getSize()).thenReturn(length);
        when(fileInfoMock.isFolder()).thenReturn(isFolder);
        RepoPath pathMock = mock(RepoPath.class);
        when(pathMock.getRepoKey()).thenReturn(TRASH_REPO_KEY);
        when(fileInfoMock.getRepoPath()).thenReturn(pathMock);
        ChecksumsInfo csInfoMock = mock(ChecksumsInfo.class);
        when(fileInfoMock.getChecksumsInfo()).thenReturn(csInfoMock);
        return fileInfoMock;
    }

}
