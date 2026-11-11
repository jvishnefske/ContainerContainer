package org.artifactory.ui.rest.model.admin.configuration.repository.remote;

import org.testng.annotations.Test;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_METADATA_RETRIEVAL_TIMEOUT_SECS;
import static org.testng.Assert.assertEquals;

/**
 * @author Alexei Vainshtein
 */
public class RemoteCacheRepositoryConfigModelTest {

    @Test
    public void testSetMissedRetrievalCachePeriodSecs() {
        RemoteCacheRepositoryConfigModel model = new RemoteCacheRepositoryConfigModel();
        long metadataRetrievalTimeoutSecs = model.getMetadataRetrievalTimeoutSecs();
        assertEquals(metadataRetrievalTimeoutSecs, DEFAULT_METADATA_RETRIEVAL_TIMEOUT_SECS);
        model.setMetadataRetrievalTimeoutSecs(10L);
        metadataRetrievalTimeoutSecs = model.getMetadataRetrievalTimeoutSecs();
        assertEquals(metadataRetrievalTimeoutSecs, 10L);
    }
}