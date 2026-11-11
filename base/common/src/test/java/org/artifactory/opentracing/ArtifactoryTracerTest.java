package org.artifactory.opentracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.ActivityScope;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.artifactory.opentracing.ArtifactoryTracer.UBER_TRACE_ID;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class ArtifactoryTracerTest {
    private final Tracer tracer = ArtifactoryTracerConfiguration.testTracer();
    private final ArtifactoryTracer artiTracer = new ArtifactoryTracer(tracer);

    @Mock
    private ArtifactoryContext artifactoryContext;
    private AutoCloseable mocks;

    @BeforeClass
    private void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        MDC.clear();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
        MDC.clear();
    }

    public void testSpanCreatedAndFinishedOnCurrentThread() {
        // No active span to begin with
        assertNull(artiTracer.getCurrentThreadActiveSpan());
        // Start a new span on this thread
        ServerTracingScope scope = artiTracer.startSpanOnCurrentThread("say-hello");
        assertNotNull(scope);
        assertNotNull(artiTracer.getCurrentThreadActiveSpan());
        assertTrue(StringUtils.isNotBlank(artiTracer.getCurrentSpanTraceId()));
        scope.close();
        // Span is closed!
        assertNull(artiTracer.getCurrentThreadActiveSpan());
    }

    public void testSpanCreatedWithoutOverrideMDCWithReference() {
        // No active span to begin with
        ServerTracingSpan activeSpan = artiTracer.getCurrentThreadActiveSpan();
        assertNull(activeSpan);

        // Start a new span on this thread
        try (ServerTracingScope scope = artiTracer.startSpanOnCurrentThread("say-hello")) {
            assertNotNull(scope);
            activeSpan = artiTracer.getCurrentThreadActiveSpan();
            assertNotNull(activeSpan);
            String firstSpanTraceId = artiTracer.getCurrentSpanTraceId();
            assertTrue(StringUtils.isNotBlank(firstSpanTraceId));

            // Starting new span with referenced follows from span
            String testTraceId = "750f00638ec180dc750f00638ec180dc";

            // Start a new span without override MDC on this thread
            try (ServerTracingScope ignored = artiTracer.startSpanThatFollows("say-hello",
                    Map.of(UBER_TRACE_ID, String.format("%s:7f1ff4d9814f0704:0:0", testTraceId)))) {
                ServerTracingSpan serverSpan = artiTracer.getCurrentThreadActiveSpan();
                assertNotNull(serverSpan);

                Span span = serverSpan.getSpan();
                assertNotNull(span);

                if (span instanceof ReadableSpan readableSpan) {
                    SpanData data = readableSpan.toSpanData();

                    assertEquals(data.getLinks().size(), 1);
                    LinkData followsLink = data.getLinks().getFirst();
                    assertEquals(followsLink.getSpanContext().getTraceId(), testTraceId);
                }

                //  check that the new span is the child of the parent
                String secondSpanTraceId = span.getSpanContext().getTraceId();
                assertEquals(secondSpanTraceId, firstSpanTraceId);
                assertNotEquals(secondSpanTraceId, testTraceId);
                assertTrue(StringUtils.isNotBlank(artiTracer.getCurrentSpanTraceId()));
                span.end();
            }
        }
        assertNull(artiTracer.getCurrentThreadActiveSpan());
    }

    public void testSpanCreatedWithoutOverrideMDCWithoutReference() {
        // No active span to begin with
        ServerTracingSpan activeSpan = artiTracer.getCurrentThreadActiveSpan();
        assertNull(activeSpan);
        // Start a new span on this thread
        try (ServerTracingScope scope = artiTracer.startSpanOnCurrentThread("say-hello")) {
            activeSpan = artiTracer.getCurrentThreadActiveSpan();
            assertNotNull(activeSpan);
            String firstSpanTraceId = artiTracer.getCurrentSpanTraceId();
            assertTrue(StringUtils.isNotBlank(firstSpanTraceId));
            // Start a new span without override MDC on this thread
            try (ServerTracingScope ignored = artiTracer.startSpanThatFollows("say-hello", null)) {
                ServerTracingSpan serverSpan = artiTracer.getCurrentThreadActiveSpan();
                Span span = serverSpan.getSpan();
                assertNotNull(span);
                String secondSpanTraceId = span.getSpanContext().getTraceId();
                assertEquals(secondSpanTraceId, firstSpanTraceId);
                assertTrue(StringUtils.isNotBlank(artiTracer.getCurrentSpanTraceId()));
                span.end();
            }
        }
        assertNull(artiTracer.getCurrentThreadActiveSpan());
    }

    public void testSpanIsolatedToOneThread() throws InterruptedException, ExecutionException {
        // No active span to begin with
        assertNull(artiTracer.getCurrentThreadActiveSpan());
        // Start a new span on this thread
        ServerTracingScope scope = artiTracer.startSpanOnCurrentThread("say-hello");
        assertNotNull(scope);
        assertNotNull(artiTracer.getCurrentThreadActiveSpan());
        assertTrue(StringUtils.isNotBlank(artiTracer.getCurrentSpanTraceId()));
        ExecutorService executorService = ArtifactoryExecutorFactory.newFixedThreadPool(1, null, false);

        // new thread, new scope -> no span
        Future<?> invocation = executorService.submit(() -> {
            assertNull(artiTracer.getCurrentThreadActiveSpan());
            assertNull(MDC.get(UBER_TRACE_ID));
        });
        invocation.get();

        scope.close();
        assertNull(artiTracer.getCurrentThreadActiveSpan());
    }

    public void testSpanPropagatedBetweenThread() throws ExecutionException, InterruptedException {
        // No active span to begin with
        assertNull(artiTracer.getCurrentThreadActiveSpan());
        // Start a new span on this thread
        ServerTracingScope scope = artiTracer.startSpanOnCurrentThread("say-hello");
        assertNotNull(scope);
        assertNotNull(artiTracer.getCurrentThreadActiveSpan());
        String currentSpanTraceId = artiTracer.getCurrentSpanTraceId();
        assertTrue(StringUtils.isNotBlank(currentSpanTraceId));

        ExecutorService executorService = ArtifactoryExecutorFactory.newFixedThreadPool(1, artifactoryContext, false);
        when(artifactoryContext.beanForType(ArtifactoryTracer.class)).thenReturn(artiTracer);

        // The executor has access to the Tracer - the thread should inherit the active span
        Future<?> invocation = executorService.submit(() -> {
            assertNotNull(artiTracer.getCurrentThreadActiveSpan());
            assertTrue(StringUtils.isNotBlank(artiTracer.getCurrentSpanTraceId()));
            assertEquals(artiTracer.getCurrentSpanTraceId(), currentSpanTraceId);
            assertEquals(MDC.get(UBER_TRACE_ID), currentSpanTraceId);
        });
        invocation.get();

        scope.close();
        assertNull(artiTracer.getCurrentThreadActiveSpan());
    }

    public void headersToInject_shouldReturnEmptyMap_whenNoActiveSpan() {
        // No active span, should return empty map
        Map<String, String> headers = artiTracer.headersToInject("GET", "/api/test");
        assertNotNull(headers);
        assertTrue(headers.isEmpty());
    }

    public void headersToInject_shouldAddHeaders_whenThereIsAnActiveSpan() {
        // No active span, should return empty map
        // With active span, should return map with injected headers
        try (ServerTracingScope ignored = artiTracer.startSpanOnCurrentThread("inject-span", null)) {
            Map<String, String> headers = artiTracer.headersToInject("POST", "/api/other");
            assertNotNull(headers);
            // Jaeger injects at least "uber-trace-id"
            assertTrue(headers.containsKey(ArtifactoryTracer.UBER_TRACE_ID));
        }
    }

    public void getCurrentTraceId_shouldReturnNull_whenNoActiveSpan() {
        // No active span, should return null
        String traceId = artiTracer.getCurrentSpanTraceId();
        assertNull(traceId);
    }

    public void closingInnerScope_shouldRestoreMdc() {
        // No active span to begin with
        assertNull(artiTracer.getCurrentThreadActiveSpan());
        assertNull(MDC.get(UBER_TRACE_ID), "Initially, trace id should not be set in the MDC");

        MDC.put(UBER_TRACE_ID, "fake");
        try (ServerTracingScope ignored = artiTracer.startSpanOnCurrentThread("outer")) {
            String rootTraceId = artiTracer.getCurrentSpanTraceId();
            assertNotNull(rootTraceId);
            assertNotEquals(rootTraceId, "fake");
            assertEquals(MDC.get(UBER_TRACE_ID), rootTraceId, "The current trace id should be set in the MDC");
        }
        assertEquals(MDC.get(UBER_TRACE_ID), "fake", "Closing a span should restore previous MDC");
        MDC.remove(UBER_TRACE_ID);

        // Start a new span on this thread
        try (ServerTracingScope ignored = artiTracer.startSpanOnCurrentThread("outer")) {
            String rootTraceId = artiTracer.getCurrentSpanTraceId();
            assertNotNull(rootTraceId);
            assertEquals(MDC.get(UBER_TRACE_ID), rootTraceId, "The current trace id should be set in the MDC");

            try (ServerTracingScope ignored1 = artiTracer.startSpanOnCurrentThread("inner")) {
                String childTraceId = artiTracer.getCurrentSpanTraceId();

                assertEquals(childTraceId, rootTraceId, "Child trace ID should be the same as the parent one");
                assertEquals(MDC.get(UBER_TRACE_ID), childTraceId, "Child trace ID should be set in the MDC");
            }

            assertEquals(MDC.get(UBER_TRACE_ID), rootTraceId, "Closing child span should restore previous MDC");
        }
        assertNull(MDC.get(UBER_TRACE_ID), "After closing all spans, MDC should be clear");
    }

    public void openScopeForCurrentSpan_shouldUpdateMdc() {
        Span outerSpan = tracer.spanBuilder("test-span")
                .setSpanKind(io.opentelemetry.api.trace.SpanKind.SERVER)
                .startSpan();
        try (var ignored = outerSpan.makeCurrent()) {
            assertNull(MDC.get(UBER_TRACE_ID), "Initially, trace id should not be set in the MDC");
            try (var ignoredInner = artiTracer.openScopeForCurrentSpan()) {
                assertEquals(outerSpan.getSpanContext().getTraceId(), MDC.get(UBER_TRACE_ID),
                        "openScopeForCurrentSpan() did not update MDC with the current trace ID");
            }
        }
    }

    public void startServerSpan_shouldUpdateTraceId_fromUberHeaders() {
        try (ServerTracingScope ignored = artiTracer.startServerSpan("test-span",
                Map.of(UBER_TRACE_ID, "750f00638ec180dc750f00638ec180dc:7f1ff4d9814f0704:0:0"))) {
            assertEquals(artiTracer.getCurrentSpanTraceId(), "750f00638ec180dc750f00638ec180dc");
            assertEquals(MDC.get(UBER_TRACE_ID), "750f00638ec180dc750f00638ec180dc");
        }
        assertNull(MDC.get(UBER_TRACE_ID));
    }

    public void startServerSpan_shouldStartNewTraceId_wnenNoHeaders() {
        try (ServerTracingScope ignored = artiTracer.startServerSpan("test-span", Map.of())) {
            assertNotNull(artiTracer.getCurrentSpanTraceId());
            assertEquals(MDC.get(UBER_TRACE_ID), artiTracer.getCurrentSpanTraceId());
        }
        assertNull(MDC.get(UBER_TRACE_ID));
    }

    public void enrichCurrentSpanFromActivity_shouldSetSpanAttributes() {
        try (ActivityScope ignoredScope = CurrentActivity.newScope();
                ServerTracingScope ignored = artiTracer.startSpanOnCurrentThread("enrich-test")) {
            CurrentActivity.KIND.put("http/get");
            CurrentActivity.GROUP.put("downloads");
            CurrentActivity.PACKAGE.put("npm");
            CurrentActivity.REPO.put("npm-remote");
            CurrentActivity.REPO_TYPE.put("remote");
            CurrentActivity.ACTOR.put("admin");

            ArtifactoryTracer.enrichCurrentSpanFromActivity();

            Span span = Span.current();
            assertTrue(span instanceof ReadableSpan, "Expected SDK span for attribute inspection");
            SpanData data = ((ReadableSpan) span).toSpanData();
            assertEquals(data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.kind")), "http/get");
            assertEquals(data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.group")), "downloads");
            assertEquals(data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.package")), "npm");
            assertEquals(data.getAttributes().get(AttributeKey.stringKey("artifactory.repo.key")), "npm-remote");
            assertEquals(data.getAttributes().get(AttributeKey.stringKey("artifactory.repo.type")), "remote");
            assertEquals(data.getAttributes().get(AttributeKey.stringKey("artifactory.actor")), "admin");
        }
    }

    public void enrichCurrentSpanFromActivity_shouldSkipNullAndEmptyValues() {
        try (ActivityScope ignoredScope = CurrentActivity.newScope();
                ServerTracingScope ignored = artiTracer.startSpanOnCurrentThread("enrich-partial")) {
            CurrentActivity.KIND.put("http/post");
            // Leave GROUP, PACKAGE, NAME, REPO, REPO_TYPE, ACTOR unset

            ArtifactoryTracer.enrichCurrentSpanFromActivity();

            Span span = Span.current();
            SpanData data = ((ReadableSpan) span).toSpanData();
            assertEquals(data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.kind")), "http/post");
            assertNull(data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.group")));
            assertNull(data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.package")));
            assertNull(data.getAttributes().get(AttributeKey.stringKey("artifactory.repo.key")));
        }
    }

    public void enrichCurrentSpanFromActivity_shouldBeNoOp_whenNoActiveSpan() {
        assertNull(artiTracer.getCurrentThreadActiveSpan());
        ArtifactoryTracer.enrichCurrentSpanFromActivity();
    }

    public void buildSpanName_shouldReturnKindAndName_whenNameIsPresent() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            CurrentActivity.KIND.put("async_task");
            CurrentActivity.GROUP.put("api");
            CurrentActivity.NAME.put("RepoService.deploy");
            assertEquals("async_task RepoService.deploy",
                    ArtifactoryTracer.buildSpanName(CurrentActivity.context()));
        }
    }

    public void buildSpanName_shouldFallBackToGroup_whenNameIsEmpty() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            CurrentActivity.KIND.put("http/get");
            CurrentActivity.GROUP.put("download");
            assertEquals("http/get download",
                    ArtifactoryTracer.buildSpanName(CurrentActivity.context()));
        }
    }

    public void buildSpanName_shouldReturnKindOnly_whenNoQualifier() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            CurrentActivity.KIND.put("http/get");
            assertEquals("http/get",
                    ArtifactoryTracer.buildSpanName(CurrentActivity.context()));
        }
    }

    public void buildSpanName_shouldReturnNull_whenKindIsEmpty() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            assertNull(ArtifactoryTracer.buildSpanName(CurrentActivity.context()));
        }
    }

    public void enrichAndRenameSpan_shouldUpdateSpanName() {
        try (ActivityScope ignoredScope = CurrentActivity.newScope();
                ServerTracingScope ignored = artiTracer.startSpanOnCurrentThread("original-name")) {
            CurrentActivity.KIND.put("job");
            CurrentActivity.NAME.put("DOCKER_V2_TAG_CLEANUP");

            ArtifactoryTracer.enrichAndRenameSpanFromActivity();

            Span span = Span.current();
            SpanData data = ((ReadableSpan) span).toSpanData();
            assertEquals("job DOCKER_V2_TAG_CLEANUP", data.getName());
            assertEquals("job", data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.kind")));
        }
    }

    public void enrichAndRenameHttpSpan_shouldUpdateSpanName() {
        try (ActivityScope ignoredScope = CurrentActivity.newScope();
                ServerTracingScope ignored = artiTracer.startSpanOnCurrentThread("GET /artifactory/*")) {
            CurrentActivity.KIND.put("http/get");
            CurrentActivity.GROUP.put("download");

            ArtifactoryTracer.enrichAndRenameHttpSpanFromActivity();

            Span span = Span.current();
            SpanData data = ((ReadableSpan) span).toSpanData();
            assertEquals("http/get download", data.getName());
            assertEquals("http/get", data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.kind")));
        }
    }

    public void enrichWithoutRename_shouldNotUpdateSpanName() {
        try (ActivityScope ignoredScope = CurrentActivity.newScope();
                ServerTracingScope ignored = artiTracer.startSpanOnCurrentThread("original-name")) {
            CurrentActivity.KIND.put("job");
            CurrentActivity.NAME.put("DOCKER_V2_TAG_CLEANUP");

            ArtifactoryTracer.enrichCurrentSpanFromActivity();

            Span span = Span.current();
            SpanData data = ((ReadableSpan) span).toSpanData();
            assertEquals("original-name", data.getName());
            assertEquals("job", data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.kind")));
        }
    }

    public void buildHttpSpanName_shouldUseGroupNotName() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            CurrentActivity.KIND.put("http/get");
            CurrentActivity.GROUP.put("download");
            CurrentActivity.NAME.put("/api/npm/{repoKey}/{path: .+}");
            assertEquals("http/get download",
                    ArtifactoryTracer.buildHttpSpanName(CurrentActivity.context()));
        }
    }

    public void buildHttpSpanName_shouldReturnKindOnly_whenNoGroup() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            CurrentActivity.KIND.put("http/put");
            assertEquals("http/put",
                    ArtifactoryTracer.buildHttpSpanName(CurrentActivity.context()));
        }
    }

    public void buildHttpSpanName_shouldReturnNull_whenKindIsEmpty() {
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            assertNull(ArtifactoryTracer.buildHttpSpanName(CurrentActivity.context()));
        }
    }

    public void enrichAndRenameSpan_shouldBeNoOp_whenEnrichmentDisabledViaConstantValues() {
        ArtifactoryHome.bind(new org.artifactory.test.ArtifactoryHomeStub("ArtifactoryTracerTest")
                .setProperty(ConstantValues.otelActivityEnrichmentDisabled, "true"));
        try (ActivityScope ignoredScope = CurrentActivity.newScope();
                ServerTracingScope ignored = artiTracer.startSpanOnCurrentThread("original-name")) {
            CurrentActivity.KIND.put("job");
            CurrentActivity.NAME.put("DOCKER_V2_TAG_CLEANUP");
            CurrentActivity.REPO.put("docker-local");

            ArtifactoryTracer.enrichAndRenameSpanFromActivity();

            Span span = Span.current();
            SpanData data = ((ReadableSpan) span).toSpanData();
            assertEquals("original-name", data.getName());
            assertNull(data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.kind")));
            assertNull(data.getAttributes().get(AttributeKey.stringKey("artifactory.repo.key")));
        } finally {
            ArtifactoryHome.unbind();
        }
    }

    public void serverTracingScope_close_shouldEnrichSpan() {
        try (ActivityScope ignoredScope = CurrentActivity.newScope()) {
            CurrentActivity.KIND.put("http/get");
            CurrentActivity.REPO.put("libs-release");

            Span span;
            try (ServerTracingScope scope = artiTracer.startSpanOnCurrentThread("auto-enrich")) {
                span = Span.current();
            }
            // After close(), the span should have been enriched automatically
            SpanData data = ((ReadableSpan) span).toSpanData();
            assertEquals(data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.kind")), "http/get");
            assertEquals(data.getAttributes().get(AttributeKey.stringKey("artifactory.repo.key")), "libs-release");
        }
    }

}