package org.artifactory.repo.service.trash;

import lombok.NonNull;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.trash.TrashGCHelper;
import org.artifactory.storage.TrashCleanupCandidate;
import org.artifactory.util.RepoPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * @author Oleksandr Legenkiy
 */
@Component
public class TrashGCHelperImpl implements TrashGCHelper {

    private static final Logger log = LoggerFactory.getLogger(TrashGCHelperImpl.class);

    private final Queue<TrashCleanupCandidate> gcQueue;
    private final long maxQueueLength;

    public TrashGCHelperImpl() {
        gcQueue = new PriorityQueue<>(Comparator.comparingLong(TrashCleanupCandidate::getLength));
        maxQueueLength = ConstantValues.gcTrashBinariesCleanupMaxQueueLength.getLong();
    }

    public void markTrashFileForGC(@NonNull FileInfo fileInfo) {
        if (RepoPathUtils.isTrash(fileInfo.getRepoPath()) && !fileInfo.isFolder()) {
            ChecksumsInfo csInfo = fileInfo.getChecksumsInfo();
            synchronized (this) {
                gcQueue.add(new TrashCleanupCandidate(csInfo.getSha1(), csInfo.getSha256(), fileInfo.getSize()));
                if (gcQueue.size() > maxQueueLength) {
                    // Remove a file with minimal length to support the max length limitation
                    gcQueue.poll();
                    log.info("Trash cleanup GC queue has exceeded its size limits");
                }
            }
        }
    }

    public void addCleanupCandidate(TrashCleanupCandidate candidate) {
        this.gcQueue.add(candidate);
    }

    @NonNull
    public List<TrashCleanupCandidate> getTrashFilesMarkedForGC() {
        synchronized (this) {
            if (!gcQueue.isEmpty()) {
                List<TrashCleanupCandidate> results = new ArrayList<>(gcQueue.size());
                results.addAll(gcQueue);
                gcQueue.clear();
                return results;
            }
        }
        return Collections.emptyList();
    }

}
