package org.artifactory.repo.replication;

import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * @author Dvir Twina
 */
class RemoteReplicationJobUnexclusiveTest {

    @Test
    void testOnExecute() throws JobExecutionException {
        RemoteReplicationJobUnexclusive job = spy(new RemoteReplicationJobUnexclusive());
        doNothing().when(job).callParent(any());

        job.onExecute(null);

        verify(job).callParent(any());
    }
}