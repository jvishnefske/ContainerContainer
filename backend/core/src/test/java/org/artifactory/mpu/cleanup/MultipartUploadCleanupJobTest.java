package org.artifactory.mpu.cleanup;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.cron.CronUtils;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MultipartUploadCleanupJobTest extends ArtifactoryHomeBoundJUnitTest {
    private static InternalArtifactoryContext context;
    private static final String SERVICE_ID = "jfrt@01bwz23eaw7ht80xvthqkk1ync";

    @BeforeEach
    void before() {
        context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void after() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testOnExecute() throws JobExecutionException {
        when(context.isReady()).thenReturn(true);
        ArtifactoryBinaryService binaryService = mock(ArtifactoryBinaryService.class);
        when(binaryService.isMultipartUploadSupported()).thenReturn(true);
        when(context.beanForType(ArtifactoryBinaryService.class)).thenReturn(binaryService);
        MultipartUploadCleanupJob multipartUploadCleanupJob = new MultipartUploadCleanupJob();
        multipartUploadCleanupJob.onExecute(createJobExecutionContext());
        verify(binaryService, times(1)).cleanupMultipartUploads();
    }

    @Test
    void testOnExecuteNotReady() throws JobExecutionException {
        ArtifactoryBinaryService binaryService = mock(ArtifactoryBinaryService.class);
        when(context.beanForType(ArtifactoryBinaryService.class)).thenReturn(binaryService);
        MultipartUploadCleanupJob multipartUploadCleanupJob = new MultipartUploadCleanupJob();
        multipartUploadCleanupJob.onExecute(createJobExecutionContext());
        verify(binaryService, times(0)).cleanupMultipartUploads();
    }

    @Test
    void testBuildRandomQuartzExpOverrideExp() {
        String expression = MultipartUploadCleanupJob.buildRandomQuartzExp("");
        assertTrue(CronUtils.isValid(expression));
    }

    @Test
    void testBuildRandomQuartzExpOverrideWrong() {
        setStringSystemProperty(ConstantValues.mpuCleanupCron, "wrong exp");
        String expression = MultipartUploadCleanupJob.buildRandomQuartzExp("");
        assertTrue(CronUtils.isValid(expression));
    }

    @Test
    void testBuildRandomQuartzExpWithServiceId() {
        String expression = MultipartUploadCleanupJob.buildRandomQuartzExp(SERVICE_ID);
        assertTrue(CronUtils.isValid(expression));
        assertEquals("0 33 9 ? * *", expression);
    }

    @Test
    void testBuildRandomQuartzExpWithNullServiceId() {
        String expression = MultipartUploadCleanupJob.buildRandomQuartzExp(null);
        assertTrue(CronUtils.isValid(expression));
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
