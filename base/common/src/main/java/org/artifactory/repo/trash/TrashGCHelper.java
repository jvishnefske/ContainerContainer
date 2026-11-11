package org.artifactory.repo.trash;

import org.artifactory.fs.FileInfo;
import org.artifactory.storage.TrashCleanupCandidate;

import java.util.List;

/**
 * @author Oleksandr Legenkiy
 */
public interface TrashGCHelper {

    /**
     * Marks a file to be deleted by a trash binaries cleanup GC job.<br/>
     * The file is put into an in-memory queue that is later on accessed by a cleanup job.
     *
     * @param fileInfo a file to be marked for GC
     */
    void markTrashFileForGC(FileInfo fileInfo);

    /**
     * Gets all the files that were marked for a GC.
     *
     * @return a list of files that should be GC-ed. Never <code>null</code>
     */
    List<TrashCleanupCandidate> getTrashFilesMarkedForGC();

}
