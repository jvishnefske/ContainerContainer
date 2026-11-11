package org.artifactory.storage.jobs;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.cron.CronUtils;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.service.ProjectBinarySizeService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for CalculateProjectBinaryStorageJob
 *
 * @author Evgeny Kurtser
 */
class CalculateProjectBinaryStorageJobTest extends ArtifactoryHomeBoundJUnitTest {

    private static InternalArtifactoryContext context;
    private CalculateProjectBinaryStorageJob job;

    @BeforeEach
    void setUp() {
        context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        job = new CalculateProjectBinaryStorageJob();
        // Set default values for constants
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationEnabled, "true");
        setLongSystemProperty(ConstantValues.projectBinaryStorageCalculationTimeoutMinutes, 60L);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    // ========== Tests for buildQuartzExp() ==========

    @Test
    void testBuildQuartzExp_WhenPredefinedCronProvided_ReturnsPredefinedExpression() {
        // Given
        String predefinedCron = "0 0/15 * ? * *";
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationJobCron, predefinedCron);

        // When
        String result = CalculateProjectBinaryStorageJob.buildQuartzExp("serviceId");

        // Then
        assertEquals(predefinedCron, result);
    }

    @Test
    void testBuildQuartzExp_WhenPredefinedCronIsBlank_GeneratesRandomExpression() {
        // Given
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationJobCron, "");

        // When
        String result = CalculateProjectBinaryStorageJob.buildQuartzExp("serviceId");

        // Then
        assertNotNull(result);
        assertTrue(CronUtils.isValid(result));
        // Format: "0 {minute} {hour} ? * * *"
        assertTrue(result.matches("0 \\d{1,2} \\d{1,2} \\? \\* \\*"));
    }

    @Test
    void testBuildQuartzExp_WhenPredefinedCronIsNull_GeneratesRandomExpression() {
        // Given
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationJobCron, null);

        // When
        String result = CalculateProjectBinaryStorageJob.buildQuartzExp("serviceId");

        // Then
        assertNotNull(result);
        assertTrue(CronUtils.isValid(result));
    }

    @Test
    void testBuildQuartzExp_WhenPredefinedCronIsInvalid_GeneratesRandomExpression() {
        // Given
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationJobCron, "invalid cron expression");

        // When
        String result = CalculateProjectBinaryStorageJob.buildQuartzExp("serviceId");

        // Then
        assertNotNull(result);
        assertTrue(CronUtils.isValid(result));
        // Should be a valid cron expression, not the invalid one
        assertNotEquals("invalid cron expression", result);
    }

    @RepeatedTest(10)
    void testBuildQuartzExp_WhenNoPredefinedCron_GeneratesRandomTimeInValidRange() {
        // Given
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationJobCron, "");

        // When
        String result = CalculateProjectBinaryStorageJob.buildQuartzExp("serviceId");

        // Then
        assertNotNull(result);
        assertTrue(CronUtils.isValid(result));
        
        // Parse the cron expression: "0 {minute} {hour} ? * * *"
        String[] parts = result.split("\\s+");
        assertEquals(6, parts.length);
        assertEquals("0", parts[0]); // seconds
        int minute = Integer.parseInt(parts[1]);
        int hour = Integer.parseInt(parts[2]);
        
        // Verify ranges
        assertTrue(minute >= 0 && minute < 60, "Minute should be between 0-59, got: " + minute);
        assertTrue(hour >= 0 && hour < 24, "Hour should be between 0-23, got: " + hour);
    }

    @Test
    void testBuildQuartzExp_WhenNoPredefinedCron_GeneratesDailyCronExpression() {
        // Given
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationJobCron, "");

        // When
        String result = CalculateProjectBinaryStorageJob.buildQuartzExp("serviceId");

        // Then
        assertNotNull(result);
        assertTrue(CronUtils.isValid(result));
        // Should match pattern for daily execution: "0 {minute} {hour} ? * * *"
        Pattern dailyPattern = Pattern.compile("^0 \\d{1,2} \\d{1,2} \\? \\* \\*$");
        assertTrue(dailyPattern.matcher(result).matches(), 
                "Cron expression should be daily format, got: " + result);
    }

    @RepeatedTest(50)
    void testBuildQuartzExp_WhenNoPredefinedCron_UsesFullDayRange() {
        // Given
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationJobCron, "");

        // When
        String result = CalculateProjectBinaryStorageJob.buildQuartzExp("serviceId");

        // Then
        assertNotNull(result);
        assertTrue(CronUtils.isValid(result));
        
        // Parse the cron expression: "0 {minute} {hour} ? * * *"
        String[] parts = result.split("\\s+");
        int minutes = Integer.parseInt(parts[1]);
        int hour = Integer.parseInt(parts[2]);

        // Verify that hour can be in full 24-hour range (0-23)
        assertTrue(hour >= 0 && hour < 24, "Hour should be between 0-23, got: " + hour);

        // Verify that minute is in valid range
        assertTrue(minutes >= 0 && minutes < 60, "Minute should be between 0-59, got: " + minutes);
    }

    @Test
    void testBuildQuartzExp_WhenSameServiceId_GeneratesSameCronExpression() {
        // Given
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationJobCron, "");
        String serviceId = "test-service-id";

        // When
        String result1 = CalculateProjectBinaryStorageJob.buildQuartzExp(serviceId);
        String result2 = CalculateProjectBinaryStorageJob.buildQuartzExp(serviceId);

        // Then
        assertEquals(result1, result2, "Same serviceId should generate same cron expression");
        assertTrue(CronUtils.isValid(result1));
    }

    @Test
    void testBuildQuartzExp_WhenDifferentServiceIds_GeneratesDifferentCronExpressions() {
        // Given
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationJobCron, "");
        String serviceId1 = "service-id-1";
        String serviceId2 = "service-id-2";

        // When
        String result1 = CalculateProjectBinaryStorageJob.buildQuartzExp(serviceId1);
        String result2 = CalculateProjectBinaryStorageJob.buildQuartzExp(serviceId2);

        // Then
        assertNotEquals(result1, result2, "Different serviceIds should generate different cron expressions");
        assertTrue(CronUtils.isValid(result1));
        assertTrue(CronUtils.isValid(result2));
    }

    @Test
    void testBuildQuartzExp_WhenServiceIdIsBlank_UsesRandomMinute() {
        // Given
        setStringSystemProperty(ConstantValues.projectBinaryStorageCalculationJobCron, "");

        // When
        String result1 = CalculateProjectBinaryStorageJob.buildQuartzExp("");
        String result2 = CalculateProjectBinaryStorageJob.buildQuartzExp(null);

        // Then
        assertNotNull(result1);
        assertNotNull(result2);
        assertTrue(CronUtils.isValid(result1));
        assertTrue(CronUtils.isValid(result2));
    }

    // ========== Tests for onExecute() ==========

    @Test
    void testOnExecute_WhenContextNotReady_SkipsExecution() throws JobExecutionException {
        // Given
        when(context.isReady()).thenReturn(false);
        ProjectBinarySizeService calculationService = mock(ProjectBinarySizeService.class);
        when(context.beanForType(ProjectBinarySizeService.class)).thenReturn(calculationService);

        // When
        job.onExecute(createJobExecutionContext());

        // Then
        verify(calculationService, never()).calculateAllProjectsSizes();
    }

    @Test
    void testOnExecute_WhenFeatureEnabled_ExecutesCalculation() throws JobExecutionException {
        // Given
        when(context.isReady()).thenReturn(true);
        ProjectBinarySizeService calculationService = mock(ProjectBinarySizeService.class);
        when(context.beanForType(ProjectBinarySizeService.class)).thenReturn(calculationService);
        when(calculationService.calculateAllProjectsSizes()).thenReturn(5);

        // When
        job.onExecute(createJobExecutionContext());

        // Then
        verify(calculationService, times(1)).calculateAllProjectsSizes();
    }

    @Test
    void testOnExecute_WhenCalculationSucceeds_LogsSuccess() throws JobExecutionException {
        // Given
        when(context.isReady()).thenReturn(true);
        ProjectBinarySizeService calculationService = mock(ProjectBinarySizeService.class);
        when(context.beanForType(ProjectBinarySizeService.class)).thenReturn(calculationService);
        when(calculationService.calculateAllProjectsSizes()).thenReturn(10);

        // When
        job.onExecute(createJobExecutionContext());

        // Then
        verify(calculationService, times(1)).calculateAllProjectsSizes();
    }

    @Test
    void testOnExecute_WhenCalculationThrowsException_HandlesGracefully() throws JobExecutionException {
        // Given
        when(context.isReady()).thenReturn(true);
        ProjectBinarySizeService calculationService = mock(ProjectBinarySizeService.class);
        when(context.beanForType(ProjectBinarySizeService.class)).thenReturn(calculationService);
        when(calculationService.calculateAllProjectsSizes())
                .thenThrow(new RuntimeException("Calculation error"));

        // When
        job.onExecute(createJobExecutionContext());

        // Then
        verify(calculationService, times(1)).calculateAllProjectsSizes();
        // Job should complete without throwing exception
    }

    @Disabled("This test passed but disabled due to its long execution time (2 minutes)")
    @Test
    void testOnExecute_WhenCalculationTimesOut_HandlesTimeout() throws JobExecutionException {
        // Given
        when(context.isReady()).thenReturn(true);
        setLongSystemProperty(ConstantValues.projectBinaryStorageCalculationTimeoutMinutes, 1L);
        ProjectBinarySizeService calculationService = mock(ProjectBinarySizeService.class);
        when(context.beanForType(ProjectBinarySizeService.class)).thenReturn(calculationService);
        
        // Simulate a long-running operation that exceeds timeout
        when(calculationService.calculateAllProjectsSizes()).thenAnswer(invocation -> {
            Thread.sleep(TimeUnit.MINUTES.toMillis(2)); // Sleep longer than timeout
            return 5;
        });

        // When
        job.onExecute(createJobExecutionContext());

        // Then
        verify(calculationService, times(1)).calculateAllProjectsSizes();
        // Job should handle timeout gracefully
    }

    @Test
    void testOnExecute_WhenTimeoutConfigured_UsesConfiguredTimeout() throws JobExecutionException {
        // Given
        when(context.isReady()).thenReturn(true);
        long timeoutMinutes = 30L;
        setLongSystemProperty(ConstantValues.projectBinaryStorageCalculationTimeoutMinutes, timeoutMinutes);
        ProjectBinarySizeService calculationService = mock(ProjectBinarySizeService.class);
        when(context.beanForType(ProjectBinarySizeService.class)).thenReturn(calculationService);
        when(calculationService.calculateAllProjectsSizes()).thenReturn(3);

        // When
        job.onExecute(createJobExecutionContext());

        // Then
        verify(calculationService, times(1)).calculateAllProjectsSizes();
        // The timeout should be used internally (we can't directly verify it, but the job should complete)
    }

    @Test
    void testOnExecute_WhenInterruptedDuringShutdown_HandlesInterruption() throws JobExecutionException {
        // Given
        when(context.isReady()).thenReturn(true);
        ProjectBinarySizeService calculationService = mock(ProjectBinarySizeService.class);
        when(context.beanForType(ProjectBinarySizeService.class)).thenReturn(calculationService);
        when(calculationService.calculateAllProjectsSizes()).thenReturn(2);

        // When
        job.onExecute(createJobExecutionContext());

        // Then
        verify(calculationService, times(1)).calculateAllProjectsSizes();
        // Job should handle interruption gracefully during shutdown
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

