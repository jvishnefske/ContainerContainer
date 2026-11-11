package org.artifactory.repo.cleanup;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author annakv
 */
class EmptyFolderScanJobTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void onExecute_shouldCallScanEmptyFolders() throws JobExecutionException {
        try (MockedStatic<ContextHelper> contextHelper = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
            EmptyFolderScanService emptyFolderScanService = mock(EmptyFolderScanService.class);
            contextHelper.when(ContextHelper::get).thenReturn(artifactoryContext);
            when(artifactoryContext.beanForType(EmptyFolderScanService.class)).thenReturn(emptyFolderScanService);
            EmptyFolderScanJob job = new EmptyFolderScanJob();
            job.onExecute(mock(JobExecutionContext.class));
            verify(emptyFolderScanService, times(1)).scanEmptyFolders();
            verifyNoMoreInteractions(emptyFolderScanService);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1, '0 0/1 * ? * * *'",
            "5, '0 0/5 * ? * * *'",
            "10, '0 0/10 * ? * * *'",
            "60, '0 0/60 * ? * * *'"
    })
    void buildRandomQuartzExp_shouldReturnCorrectExpression_whenGivenInterval(int interval, String expectedExpression) {
        setStringSystemProperty(ConstantValues.emptyFolderScanJobIntervalMin, String.valueOf(interval));
        String result = EmptyFolderScanJob.buildRandomQuartzExp();
        assertEquals(expectedExpression, result);
    }

    @Test
    void buildRandomQuartzExp_shouldReturnDefaultExpression_whenNoSystemPropertySet() {
        String result = EmptyFolderScanJob.buildRandomQuartzExp();
        assertEquals("0 0/1 * ? * * *", result);
    }
}

