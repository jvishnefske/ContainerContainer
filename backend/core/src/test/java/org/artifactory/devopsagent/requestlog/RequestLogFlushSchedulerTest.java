package org.artifactory.devopsagent.requestlog;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLogFlushSchedulerTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void featureDisabledSkipsAllTasks() {
        TaskService taskService = mock(TaskService.class);
        RequestLogService requestLogService = mock(RequestLogService.class);
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(requestLogService.isFeatureEnabled()).thenReturn(false);

        new RequestLogFlushScheduler(taskService, requestLogService, dbProperties).onContextCreated();

        verify(taskService, never()).startTask(any(), anyBoolean());
    }

    @Test
    void featureEnabledNonPostgresStartsTwoTasks() {
        try (MockedStatic<TaskUtils> taskUtilsMock = Mockito.mockStatic(TaskUtils.class)) {
            Task mockTask = mock(Task.class);
            taskUtilsMock.when(() -> TaskUtils.createRepeatingTask(any(), anyLong(), anyLong()))
                    .thenReturn(mockTask);

            TaskService taskService = mock(TaskService.class);
            RequestLogService requestLogService = mock(RequestLogService.class);
            ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
            when(requestLogService.isFeatureEnabled()).thenReturn(true);
            when(dbProperties.getDbType()).thenReturn(DbType.MYSQL);

            new RequestLogFlushScheduler(taskService, requestLogService, dbProperties).onContextCreated();

            verify(taskService, times(2)).startTask(any(), anyBoolean());
        }
    }

    @Test
    void featureEnabledPostgresStartsThreeTasks() {
        try (MockedStatic<TaskUtils> taskUtilsMock = Mockito.mockStatic(TaskUtils.class)) {
            Task mockTask = mock(Task.class);
            taskUtilsMock.when(() -> TaskUtils.createRepeatingTask(any(), anyLong(), anyLong()))
                    .thenReturn(mockTask);

            TaskService taskService = mock(TaskService.class);
            RequestLogService requestLogService = mock(RequestLogService.class);
            ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
            when(requestLogService.isFeatureEnabled()).thenReturn(true);
            when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);

            new RequestLogFlushScheduler(taskService, requestLogService, dbProperties).onContextCreated();

            verify(taskService, times(3)).startTask(any(), anyBoolean());
        }
    }
}
