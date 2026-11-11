package org.artifactory.storage.jobs;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.cron.CronUtils;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.service.StorageMetricCollector;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author annakv
 */
class UpdateStorageMetricJobTest extends ArtifactoryHomeBoundJUnitTest {

    private static InternalArtifactoryContext context;

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
        StorageMetricCollector collector = mock(StorageMetricCollector.class);
        when(context.beanForType(StorageMetricCollector.class)).thenReturn(collector);
        UpdateStorageMetricJob updateStorageMetricJob = new UpdateStorageMetricJob();
        updateStorageMetricJob.onExecute(createJobExecutionContext());
        verify(collector, times(1)).update();
    }

    @Test
    void testOnExecuteNotReady() throws JobExecutionException {
        StorageMetricCollector collector = mock(StorageMetricCollector.class);
        when(context.beanForType(StorageMetricCollector.class)).thenReturn(collector);
        UpdateStorageMetricJob updateStorageMetricJob = new UpdateStorageMetricJob();
        updateStorageMetricJob.onExecute(createJobExecutionContext());
        verify(collector, times(0)).update();
    }

    @Test
    void testBuildRandomQuartzExp() {
        String expression = UpdateStorageMetricJob.buildRandomQuartzExp();
        assertTrue(CronUtils.isValid(expression));
    }

    @Test
    void testBuildRandomQuartzExpOverrideExp() {
        setStringSystemProperty(ConstantValues.updateStorageStatusJobCron, "0 0/15 * ? * *");
        String expression = UpdateStorageMetricJob.buildRandomQuartzExp();
        Assertions.assertEquals("0 0/15 * ? * *", expression);
    }

    @Test
    void testBuildRandomQuartzExpOverrideWrong() {
        setStringSystemProperty(ConstantValues.updateStorageStatusJobCron, "wrong exp");
        String expression = UpdateStorageMetricJob.buildRandomQuartzExp();
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
