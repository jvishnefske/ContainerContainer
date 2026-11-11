package org.artifactory.metadata.service.indexer;


import jakarta.annotation.Nullable;
import org.artifactory.async.Async;
import org.artifactory.fs.ItemInfo;
import org.artifactory.metadata.service.MetadataEventService;

import java.util.Collection;
import java.util.Map;

/**
 * An internal interface for exposing MDS "real time" indexer abilities to the event base indexer.
 *
 * @author BarakH
 */
public interface InternalMetadataServiceIndexHandler {

    /**
     * @return True if the real time Metadata index is enabled and the package type of the given repository is
     * supported by the real time indexer.
     */
    boolean shouldUseRealTimeIndex(String repoPath);

    /**
     * Inject manually to remain the current functionality in the {@link MetadataEventService#init}.
     */
    void setMetadataEventService(MetadataEventService metadataEventService);


    /**
     * Submit internal path deletion with a delay after commit.
     * This is necessary as long as the MDS is validating the artifact was deleted before deleting it.
     */
    @Async(delayUntilAfterCommit = true)
    void submitPackageDeletionAfterCommit(ItemInfo repoPath,  @Nullable Map<String, Collection<String>> props);
}
