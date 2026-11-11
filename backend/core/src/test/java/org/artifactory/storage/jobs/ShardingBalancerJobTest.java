package org.artifactory.storage.jobs;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ShardingBalancerJobTest {
    private static InternalArtifactoryContext context;

    @BeforeEach
    void setUp() {
        context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testOnExecute() throws JobExecutionException {
        ArtifactoryBinaryService binaryService = mock(ArtifactoryBinaryService.class);
        when(context.beanForType(ArtifactoryBinaryService.class)).thenReturn(binaryService);
        ShardingBalancerJob job = new ShardingBalancerJob();
        job.onExecute(createJobExecutionContext());
        verify(binaryService, times(1)).tryToOptimize();
    }

    @Test
    void testOnExecuteException() {
        ArtifactoryBinaryService binaryService = mock(ArtifactoryBinaryService.class);
        doThrow(new RuntimeException()).when(binaryService).tryToOptimize();
        when(context.beanForType(ArtifactoryBinaryService.class)).thenReturn(binaryService);
        ShardingBalancerJob job = new ShardingBalancerJob();
        assertThrows(JobExecutionException.class, () -> job.onExecute(createJobExecutionContext()));
    }

    private JobExecutionContext createJobExecutionContext() {
        TriggerFiredBundle triggerFiredBundle = mock(TriggerFiredBundle.class);
        OperableTrigger operableTrigger = mock(OperableTrigger.class);
        when(operableTrigger.getJobDataMap()).thenReturn(mock(JobDataMap.class));
        when(triggerFiredBundle.getTrigger()).thenReturn(operableTrigger);
        JobDetail jobDetail = mock(JobDetail.class);
        when(jobDetail.getJobDataMap()).thenReturn(mock(JobDataMap.class));
        when(triggerFiredBundle.getJobDetail()).thenReturn(jobDetail);
        when(triggerFiredBundle.getScheduledFireTime()).thenReturn(new Date());
        return new JobExecutionContextImpl(mock(Scheduler.class),
                triggerFiredBundle, mock(Job.class));
    }
}
