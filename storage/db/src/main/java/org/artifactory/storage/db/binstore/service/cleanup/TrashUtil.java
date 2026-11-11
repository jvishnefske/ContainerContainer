package org.artifactory.storage.db.binstore.service.cleanup;

import lombok.experimental.UtilityClass;
import org.artifactory.storage.GCCandidate;

import static org.artifactory.repo.trash.TrashService.TRASH_KEY;

@UtilityClass
public class TrashUtil {
    public static boolean isTrashItem(GCCandidate gcCandidate) {
        return gcCandidate.repoPath() != null && TRASH_KEY.equals(gcCandidate.repoPath().getRepoKey());
    }
}
