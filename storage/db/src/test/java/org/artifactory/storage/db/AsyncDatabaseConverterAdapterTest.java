package org.artifactory.storage.db;

import jakarta.validation.constraints.NotNull;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.artifactory.storage.jobs.async.database.DatabaseConverterRunnable;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.artifactory.common.ConstantValues.delayDbAsyncConvertorsExecution;
import static org.jfrog.converter.model.ConverterExecutionType.SEQ_ASYNC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Alexei Vainshtein
 */
public class AsyncDatabaseConverterAdapterTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private ArtifactoryContext context;
    private AsyncDatabaseConverterAdapter converterAdapter;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setup() {
        autoCloseable = openMocks(this);
        converterAdapter = spy(new AsyncDatabaseConverterAdapter(getOrCreateArtifactoryHomeStub()));
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    public void testConvert() {
        createAolService(context, false);
        doReturn(List.of(ArtifactoryDBVersion.get(SEQ_ASYNC))).when(converterAdapter).getDbVersions(any(), any());
        CompoundVersionDetails source = mock(CompoundVersionDetails.class);
        when(source.getVersion()).thenReturn(ArtifactoryServiceVersionProvider.v790m001.get());
        CompoundVersionDetails target = mock(CompoundVersionDetails.class);
        ScheduledExecutorService executor = mock(TestArtifactoryExecutor.class, InvocationOnMock::callRealMethod);
        when(converterAdapter.getScheduledExecutorService()).thenReturn(executor);
        converterAdapter.convert(source, target);
        verify(executor, Mockito.times(1)).shutdown();
        when(context.beanForType(ArtifactoryJdbcHelper.class))
                .thenReturn(mock(ArtifactoryJdbcHelper.class));
        ArtifactoryContextThreadBinder.bind(context);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testGetMinutesDelayEnabled(boolean enabled) {
        createAolService(context, true);
        getOrCreateArtifactoryHomeStub().setProperty(delayDbAsyncConvertorsExecution.getPropertyName(),
                String.valueOf(enabled));
        int minutesDelay = converterAdapter.getMinutesDelay();
        if (enabled) {
            assertTrue(minutesDelay > 0);
        } else {
            assertEquals(0, minutesDelay);
        }
    }

    @NotNull
    private AolService createAolService(ArtifactoryContext context, boolean isAol) {
        AolService aolService = mock(AolService.class);
        when(aolService.isAol()).thenReturn(isAol);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        return aolService;
    }

    private static abstract class TestArtifactoryExecutor implements ScheduledExecutorService {

        @NotNull
        @Override
        public ScheduledFuture<?> schedule(@NotNull Runnable command, long delay,
                @NotNull TimeUnit unit) {
            assertTrue(command instanceof DatabaseConverterRunnable);
            return null;
        }

        @Override
        public void shutdown() {

        }
    }
}