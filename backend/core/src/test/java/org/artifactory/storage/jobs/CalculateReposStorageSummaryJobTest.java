package org.artifactory.storage.jobs;

import lombok.SneakyThrows;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.storage.service.InternalStorageService;
import org.artifactory.storage.service.StorageSummaryBackofficeMetricLogger;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.metrics.backoffice.exception.BackofficeMetricsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.StringUtils;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author annakv
 */
class CalculateReposStorageSummaryJobTest extends ArtifactoryHomeBoundJUnitTest {

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
    @SneakyThrows
    void testOnExecute() {
        when(context.isReady()).thenReturn(true);
        InternalStorageService storageService = mock(InternalStorageService.class);
        when(context.beanForType(InternalStorageService.class)).thenReturn(storageService);

        StorageSummaryBackofficeMetricLogger metricsLogger = mock(StorageSummaryBackofficeMetricLogger.class);
        when(context.beanForType(StorageSummaryBackofficeMetricLogger.class)).thenReturn(metricsLogger);

        StorageSummaryService storageSummaryService = mock(StorageSummaryService.class);
        when(context.beanForType(StorageSummaryService.class)).thenReturn(storageSummaryService);
        when(storageSummaryService.getLocalCache())
                .thenReturn(mock(StorageSummaryInfo.class))
                .thenThrow(CacheUnAvailableException.class)
                .thenReturn(mock(StorageSummaryInfo.class));
        // Normal flow
        CalculateReposStorageSummaryJob job = new CalculateReposStorageSummaryJob();
        job.onExecute(createJobExecutionContext());
        verify(storageService, times(1)).calculateStorageSummary();
        verify(metricsLogger, times(1)).logMetrics(any(StorageSummaryInfo.class));

        // Exception flow
        //doThrow(CacheUnAvailableException.class).when(storageSummaryService).getLocalCache();
        job.onExecute(createJobExecutionContext());
        verify(metricsLogger, times(1)).logMetrics(any(StorageSummaryInfo.class));

        doThrow(BackofficeMetricsException.class).when(metricsLogger).logMetrics(any(StorageSummaryInfo.class));
        job.onExecute(createJobExecutionContext());
        verify(metricsLogger, times(2)).logMetrics(any(StorageSummaryInfo.class));
    }

    @Test
    void testOnExecuteNotReady() throws JobExecutionException {
        when(context.isReady()).thenReturn(false);
        InternalStorageService storageService = mock(InternalStorageService.class);
        when(context.beanForType(InternalStorageService.class)).thenReturn(storageService);
        CalculateReposStorageSummaryJob job = new CalculateReposStorageSummaryJob();
        job.onExecute(createJobExecutionContext());
        verify(storageService, times(0)).calculateStorageSummary();
    }

    @Test
    void testBuildRandomQuartzExp() {
        String expression = CalculateReposStorageSummaryJob.buildRandomQuartzExp("serviceId");
        Assertions.assertEquals("0 52 4/6 ? * *", expression);
    }

    @Test
    void testBuildRandomQuartzExpNoServiceId() {
        String expression = CalculateReposStorageSummaryJob.buildRandomQuartzExp("");
        Assertions.assertTrue(StringUtils.isNotBlank(expression));
    }

    @Test
    void testBuildRandomQuartzExpOverrideExp() {
        setStringSystemProperty(ConstantValues.calculateStorageSummaryJobCron, "0 0/15 * ? * *");
        String expression = CalculateReposStorageSummaryJob.buildRandomQuartzExp("");
        Assertions.assertEquals("0 0/15 * ? * *", expression);
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
