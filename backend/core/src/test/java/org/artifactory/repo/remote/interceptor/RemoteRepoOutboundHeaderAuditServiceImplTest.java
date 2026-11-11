package org.artifactory.repo.remote.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.apache.http.ProtocolVersion;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.artifactory.common.ConstantValues;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RemoteRepoOutboundHeaderAuditServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private TaskService taskService;
    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Captor
    private ArgumentCaptor<ILoggingEvent> captor;

    private RemoteRepoOutboundHeaderAuditServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RemoteRepoOutboundHeaderAuditServiceImpl(taskService, false);
    }

    private static BasicHttpResponse responseWithHeaders(String... nameValuePairs) {
        BasicHttpResponse response = new BasicHttpResponse(
                new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            response.addHeader(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return response;
    }

    @Test
    void flush_logsRepoKeyRepoUrlAndDistinctHeaderNames() {
        Logger regLog = (Logger) LoggerFactory.getLogger(RemoteRepoOutboundHeaderAuditServiceImpl.class);
        regLog.setLevel(Level.INFO);
        regLog.addAppender(mockAppender);
        try {
            BasicHttpResponse response = responseWithHeaders(
                    "X-Upstream-Trace", "abc123",
                    "Z-H", "1",
                    "A-H", "2",
                    "Set-Cookie", "session=secret");
            service.record("my-remote", "https://upstream.example/artifactory/remote-repo", response);

            service.flush();

            verify(mockAppender, times(1)).doAppend(captor.capture());
            String msg = captor.getValue().getFormattedMessage();
            assertTrue(msg.contains("repoKey=my-remote"), msg);
            assertTrue(msg.contains("repoUrl=https://upstream.example/artifactory/remote-repo"), msg);
            assertTrue(msg.contains("A-H"), msg);
            assertTrue(msg.contains("Z-H"), msg);
            assertTrue(msg.contains("X-Upstream-Trace"), msg);
            assertTrue(msg.contains("Set-Cookie"), msg);
            assertFalse(msg.contains("secret"), msg);
            assertFalse(msg.contains("abc123"), msg);
        } finally {
            regLog.detachAppender(mockAppender);
        }
    }

    @Test
    void flush_retainsAccumulatedHeadersForOfflineGuard() {
        service.record("repo-a", "https://up/a", responseWithHeaders("H1", "v"));
        Set<String> beforeFlush = Set.copyOf(service.getHeaderNames("repo-a"));
        assertFalse(beforeFlush.isEmpty());

        service.flush();

        // Flush only logs the summary; the audit retains headers so the offline guard's evaluator
        // can still match against them on subsequent ticks.
        assertEquals(beforeFlush, service.getHeaderNames("repo-a"));
    }

    @Test
    void record_isNoop_whenRepoKeyBlankOrResponseNull() {
        service.record(null, "https://up/a", responseWithHeaders("H1", "v"));
        service.record("", "https://up/a", responseWithHeaders("H1", "v"));
        service.record("repo-a", "https://up/a", null);
        assertTrue(service.getHeaderNames("repo-a").isEmpty());
    }

    @Test
    void getHeaderNames_returnsLiveViewForExistingRepoAndEmptyForOthers() {
        service.record("repo-a", "https://up/a", responseWithHeaders("H1", "v", "H2", "v"));
        Set<String> names = service.getHeaderNames("repo-a");
        assertEquals(Set.of("H1", "H2"), names);

        // The returned set reflects subsequent records on the same repo.
        service.record("repo-a", "https://up/a", responseWithHeaders("H3", "v"));
        assertEquals(Set.of("H1", "H2", "H3"), service.getHeaderNames("repo-a"));

        assertTrue(service.getHeaderNames("unknown-repo").isEmpty());
        assertTrue(service.getHeaderNames(null).isEmpty());
        assertTrue(service.getHeaderNames("").isEmpty());
    }

    @Test
    void remove_dropsRepoEntry() {
        service.record("repo-a", "https://up/a", responseWithHeaders("H1", "v"));
        service.record("repo-b", "https://up/b", responseWithHeaders("H2", "v"));

        service.remove("repo-a");

        assertTrue(service.getHeaderNames("repo-a").isEmpty());
        assertEquals(Set.of("H2"), service.getHeaderNames("repo-b"));
    }

    @Test
    void remove_isNoopForBlankKey() {
        service.record("repo-a", "https://up/a", responseWithHeaders("H1", "v"));
        service.remove(null);
        service.remove("");
        assertEquals(Set.of("H1"), service.getHeaderNames("repo-a"));
    }

    @Test
    void constructor_schedulesReportJob_whenCronConfigured() {
        homeStub.setProperty(ConstantValues.remoteRepoOutboundHeadersAuditCron, "0 0 * * * ?");

        new RemoteRepoOutboundHeaderAuditServiceImpl(taskService, true);

        verify(taskService, times(1)).startTask(any(Task.class), eq(false));
    }

    @Test
    void constructor_doesNotScheduleReportJob_whenCronBlank() {
        homeStub.setProperty(ConstantValues.remoteRepoOutboundHeadersAuditCron, "");

        new RemoteRepoOutboundHeaderAuditServiceImpl(taskService, true);

        verifyNoInteractions(taskService);
    }

    @Test
    void record_skipsBlankHeaderNames() {
        service.record("repo-a", "https://up/a",
                responseWithHeaders("", "still-recorded-value", "X-Real", "v"));

        // Empty/blank header names are filtered out; only the well-formed name survives.
        assertEquals(Set.of("X-Real"), service.getHeaderNames("repo-a"));
    }

    @Test
    void record_preservesFirstNonBlankRepoUrl_acrossSubsequentCalls() {
        Logger regLog = (Logger) LoggerFactory.getLogger(RemoteRepoOutboundHeaderAuditServiceImpl.class);
        regLog.setLevel(Level.INFO);
        regLog.addAppender(mockAppender);
        try {
            // First call stores the URL.
            service.record("repo-a", "https://up/original", responseWithHeaders("H1", "v"));
            // Second call with a different URL must NOT overwrite the first one (setRepoUrlIfEmpty).
            service.record("repo-a", "https://up/other-url", responseWithHeaders("H2", "v"));
            // Third call with a blank URL must also leave the original intact.
            service.record("repo-a", "", responseWithHeaders("H3", "v"));

            service.flush();

            verify(mockAppender, times(1)).doAppend(captor.capture());
            String msg = captor.getValue().getFormattedMessage();
            assertTrue(msg.contains("repoUrl=https://up/original"), msg);
            assertFalse(msg.contains("https://up/other-url"), msg);
        } finally {
            regLog.detachAppender(mockAppender);
        }
    }

    @Test
    void flush_emitsNoLog_whenNothingRecorded() {
        Logger regLog = (Logger) LoggerFactory.getLogger(RemoteRepoOutboundHeaderAuditServiceImpl.class);
        regLog.setLevel(Level.INFO);
        regLog.addAppender(mockAppender);
        try {
            service.flush();

            verify(mockAppender, never()).doAppend(any());
        } finally {
            regLog.detachAppender(mockAppender);
        }
    }
}
