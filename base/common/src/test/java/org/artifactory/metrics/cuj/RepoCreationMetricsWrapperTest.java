package org.artifactory.metrics.cuj;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Providers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

import static jakarta.ws.rs.core.Response.Status.BAD_GATEWAY;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Oleksandr Legenkiy
 */
class RepoCreationMetricsWrapperTest {

    private static final String METRIC_NAME = "repo.create.time";
    private static final String METRIC_DESCRIPTION = "The time taken to create the repo";
    private static final int DEFAULT_UNKNOWN_ERROR_STATUS_CODE = 999;
    private static final String UNKNOWN_TAG_VALUE = "unknown";

    private MeterRegistry meterRegistryMock;
    private Timer.Builder timerBuilderMock;
    private Timer timerMock;
    private Providers jerseyProvidersMock;
    private RepoCreationMetricsWrapper repoCreationMetricsWrapper;

    @BeforeEach
    void setUp() {
        meterRegistryMock = mock(MeterRegistry.class);
        timerBuilderMock = mock(Timer.Builder.class);
        when(timerBuilderMock.description(METRIC_DESCRIPTION)).thenReturn(timerBuilderMock);
        when(timerBuilderMock.tags(anyIterable())).thenReturn(timerBuilderMock);
        timerMock = mock(Timer.class);
        when(timerBuilderMock.register(any())).thenReturn(timerMock);
        doNothing().when(timerMock).record(anyLong(), eq(TimeUnit.MILLISECONDS));
        LongSupplier timeSupplierMock = mock(LongSupplier.class);
        when(timeSupplierMock.getAsLong()).thenReturn(1L).thenReturn(1001L);
        repoCreationMetricsWrapper = new RepoCreationMetricsWrapper(meterRegistryMock);
        repoCreationMetricsWrapper.currentTimeSupplier = timeSupplierMock;
    }

    @Test
    void testMetricsCollectedProperly() {
        runWithMockedMetricsTimerBuilder(() -> {
            var response = repoCreationMetricsWrapper.createRepoAndCollectMetrics(() -> "Local", () -> "Docker",
                    () -> Response.status(Response.Status.OK).build(), null);
            assertNotNull(response);
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            verify(timerBuilderMock, times(1)).tags(Tags.of("repoType", "local", "repoPackageType", "docker",
                    "status", "200"));
            verify(timerBuilderMock, times(1)).register(meterRegistryMock);
            verify(timerMock, times(1)).record(1000, TimeUnit.MILLISECONDS);
        });
    }

    @Test
    void testErrorsInParamSuppliersResultInUnknownValuesInMetrics() {
        runWithMockedMetricsTimerBuilder(() -> {
            var response = repoCreationMetricsWrapper.createRepoAndCollectMetrics(
                    () -> null, // should result in unknown value for the repoTypeTag
                    () -> {throw new RuntimeException();}, // should result in unknown value for repoPackageType tag
                    () -> Response.status(Response.Status.OK).build(), null);
            assertNotNull(response);
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            verify(timerBuilderMock, times(1)).tags(
                    Tags.of("repoType", UNKNOWN_TAG_VALUE, "repoPackageType", UNKNOWN_TAG_VALUE,
                            "status", "200"));
            verify(timerBuilderMock, times(1)).register(meterRegistryMock);
            verify(timerMock, times(1)).record(1000, TimeUnit.MILLISECONDS);
        });
    }

    @Test
    void testErrorDuringMetricsSavingDoesNotAffectTheReturnedValue() {
        try (MockedStatic<Timer> timerMockedStatic = Mockito.mockStatic(Timer.class)) {
            timerMockedStatic.when(() -> Timer.builder(METRIC_NAME)).thenThrow(new RuntimeException("Test"));
            var response = repoCreationMetricsWrapper.createRepoAndCollectMetrics(() -> "local", () -> "docker",
                    () -> Response.status(Response.Status.OK).build(), null);
            assertNotNull(response);
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
            verifyNoInteractions(timerMock, timerBuilderMock);
        }
    }

    @ParameterizedTest
    @MethodSource("provideRuntimeExceptionHandlingTestParams")
    void testRuntimeExceptionHandlingDuringRepoCreation(boolean useExceptionMapping, boolean mapperIsPresent,
            String expectedMetricsStatusTagValue) {
        if (useExceptionMapping) {
            jerseyProvidersMock = mock(Providers.class);
            ExceptionMapper<RuntimeException> exceptionMapperMock = null;
            if (mapperIsPresent) {
                exceptionMapperMock = mock(ExceptionMapper.class);
                when(exceptionMapperMock.toResponse(any())).thenReturn(Response.status(BAD_GATEWAY).build());
            }
            when(jerseyProvidersMock.getExceptionMapper(RuntimeException.class)).thenReturn(exceptionMapperMock);
        }
        runWithMockedMetricsTimerBuilder(() -> {
            RuntimeException runtimeException = new RuntimeException("Test");
            RuntimeException t = assertThrows(RuntimeException.class,
                    () -> repoCreationMetricsWrapper.createRepoAndCollectMetrics(() -> "local", () -> "docker",
                            () -> {throw runtimeException;}, jerseyProvidersMock));
            assertEquals(runtimeException, t);
            verify(timerBuilderMock, times(1)).tags(Tags.of("repoType", "local", "repoPackageType", "docker",
                    "status", expectedMetricsStatusTagValue));
            verify(timerBuilderMock, times(1)).register(meterRegistryMock);
            verify(timerMock, times(1)).record(1000, TimeUnit.MILLISECONDS);
        });
    }

    @Test
    void testCheckedExceptionHandlingDuringRepoCreation() {
        runWithMockedMetricsTimerBuilder(() -> {
            Response response = repoCreationMetricsWrapper.createRepoAndCollectMetrics(() -> "local", () -> "docker",
                    () -> {throw new Exception("test");}, jerseyProvidersMock);
            assertNotNull(response);
            assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
            assertEquals("test", response.getEntity());
            verify(timerBuilderMock, times(1)).tags(Tags.of("repoType", "local", "repoPackageType", "docker",
                    "status", "500"));
            verify(timerBuilderMock, times(1)).register(meterRegistryMock);
            verify(timerMock, times(1)).record(1000, TimeUnit.MILLISECONDS);
        });
    }

    private void runWithMockedMetricsTimerBuilder(Runnable runnable) {
        try (MockedStatic<Timer> timerMockedStatic = Mockito.mockStatic(Timer.class)) {
            timerMockedStatic.when(() -> Timer.builder(METRIC_NAME)).thenReturn(timerBuilderMock);
            runnable.run();
        }
    }

    private static Stream<Arguments> provideRuntimeExceptionHandlingTestParams() {
        return Stream.of(
                Arguments.of(true, true, String.valueOf(BAD_GATEWAY.getStatusCode())),
                Arguments.of(true, false, String.valueOf(INTERNAL_SERVER_ERROR.getStatusCode())),
                Arguments.of(false, false, String.valueOf(DEFAULT_UNKNOWN_ERROR_STATUS_CODE))
        );
    }
}