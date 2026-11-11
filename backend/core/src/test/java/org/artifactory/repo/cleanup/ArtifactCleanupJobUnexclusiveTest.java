package org.artifactory.repo.cleanup;

import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * @author Dvir Twina
 */
class ArtifactCleanupJobUnexclusiveTest {

    @Test
    void testOnExecute() throws JobExecutionException {
        ArtifactCleanupJobUnexclusive job = spy(new ArtifactCleanupJobUnexclusive());
        doNothing().when(job).callParent(any());

        job.onExecute(null);

        verify(job).callParent(any());
    }
}