package org.artifactory.request.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class HttpHeadersAuditServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private TaskService taskService;

    private AutoCloseable closeable;
    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void initMocks() {
        closeable = openMocks(this);
        doReturn("task-id").when(taskService).startTask(any(), anyBoolean());

        serviceLogger = (Logger) LoggerFactory.getLogger(HttpHeadersAuditServiceImpl.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
        serviceLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void closeMocks() throws Exception {
        serviceLogger.detachAppender(logAppender);
        logAppender.stop();
        closeable.close();
    }

    @Test
    void constructor_shouldScheduleCronTask_whenFeatureFlagEnabled() {
        homeStub.setProperty(ConstantValues.httpHeadersAuditEnabled, "true");
        homeStub.setProperty(ConstantValues.httpHeadersAuditCron, "0 0/5 * * * ? *");

        Task expectedTask = mock(Task.class);
        try (MockedStatic<TaskUtils> taskUtils = mockStatic(TaskUtils.class)) {
            taskUtils.when(() -> TaskUtils.createCronTask(HttpHeadersAuditJob.class, "0 0/5 * * * ? *"))
                    .thenReturn(expectedTask);

            new HttpHeadersAuditServiceImpl(taskService);

            taskUtils.verify(() -> TaskUtils.createCronTask(HttpHeadersAuditJob.class, "0 0/5 * * * ? *"));
            verify(taskService).startTask(expectedTask, false);
        }
    }

    @Test
    void constructor_shouldNotScheduleCronTask_whenFeatureFlagDisabled() {
        homeStub.setProperty(ConstantValues.httpHeadersAuditEnabled, "false");

        try (MockedStatic<TaskUtils> taskUtils = mockStatic(TaskUtils.class)) {
            new HttpHeadersAuditServiceImpl(taskService);

            taskUtils.verifyNoInteractions();
            verify(taskService, never()).startTask(any(), anyBoolean());
        }
    }

    @Test
    void recordHeaderNames_shouldStoreLowercasedDistinctHeaders() {
        HttpHeadersAuditServiceImpl service = newServiceWithSchedulingDisabled();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumerationOf("Content-Type", "X-JFrog-Art-Api", "content-type"));

        service.recordHeaderNames(request);
        service.flush();

        assertEquals(Set.of("content-type", "x-jfrog-art-api"), lastFlushSet());
    }

    @Test
    void recordHeaderNames_shouldBeNoOp_whenHeaderNamesIsNull() {
        HttpHeadersAuditServiceImpl service = newServiceWithSchedulingDisabled();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(null);

        service.recordHeaderNames(request);
        service.flush();

        assertEmptyFlushLogged();
    }

    @Test
    void recordHeaderNames_shouldSkipNullEntries() {
        HttpHeadersAuditServiceImpl service = newServiceWithSchedulingDisabled();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumerationOf("Accept", null, "User-Agent"));

        service.recordHeaderNames(request);
        service.flush();

        assertEquals(Set.of("accept", "user-agent"), lastFlushSet());
    }

    @Test
    void flush_shouldBeNoOp_whenNoHeadersRecorded() {
        HttpHeadersAuditServiceImpl service = newServiceWithSchedulingDisabled();

        service.flush();

        assertEmptyFlushLogged();
    }

    @Test
    void flush_shouldAccumulateObservedHeadersAcrossFlushes() {
        HttpHeadersAuditServiceImpl service = newServiceWithSchedulingDisabled();

        HttpServletRequest first = mock(HttpServletRequest.class);
        when(first.getHeaderNames()).thenReturn(enumerationOf("Accept"));
        service.recordHeaderNames(first);

        service.flush();
        assertEquals(Set.of("accept"), lastFlushSet());

        HttpServletRequest second = mock(HttpServletRequest.class);
        when(second.getHeaderNames()).thenReturn(enumerationOf("X-Custom"));
        service.recordHeaderNames(second);

        logAppender.list.clear();
        service.flush();
        assertEquals(Set.of("accept", "x-custom"), lastFlushSet());
    }

    private HttpHeadersAuditServiceImpl newServiceWithSchedulingDisabled() {
        homeStub.setProperty(ConstantValues.httpHeadersAuditEnabled, "false");
        return new HttpHeadersAuditServiceImpl(taskService);
    }

    private static Enumeration<String> enumerationOf(String... values) {
        return Collections.enumeration(Arrays.asList(values));
    }

    @SuppressWarnings("unchecked")
    private Set<String> lastFlushSet() {
        List<ILoggingEvent> events = logAppender.list;
        for (int i = events.size() - 1; i >= 0; i--) {
            ILoggingEvent event = events.get(i);
            if (event.getMessage() != null
                    && event.getMessage().startsWith("HTTP headers audit: distinct header names observed")) {
                Object[] args = event.getArgumentArray();
                if (args != null && args.length > 0 && args[0] instanceof Set) {
                    return Set.copyOf((Set<String>) args[0]);
                }
            }
        }
        throw new AssertionError("No 'distinct header names observed' log event captured. Captured: " + events);
    }

    private void assertEmptyFlushLogged() {
        boolean foundEmptyLog = logAppender.list.stream()
                .anyMatch(event -> "HTTP headers audit: no headers observed in the last period"
                        .equals(event.getFormattedMessage()));
        assertTrue(foundEmptyLog, "Expected the 'no headers observed' log line, got: " + logAppender.list);
    }
}
