package org.artifactory.storage.db.aql.service;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.aql.AqlResultRuntimeException;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlQueryRepoType;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.result.AqlJsonLocalStreamer;
import org.artifactory.aql.result.AqlJsonMultiStreamer;
import org.artifactory.aql.result.AqlJsonRemoteStreamer;
import org.artifactory.aql.result.AqlJsonStreamer;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.aql.dao.AqlDao;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlQueryBuilder;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AqlQueryStreamServiceTest extends ArtifactoryHomeBoundJUnitTest {
    @Spy
    SqlQueryBuilder sqlQueryBuilder;
    @Mock
    AqlDao dao;
    @Mock
    ArtifactoryContext context;
    @Spy
    @InjectMocks
    AqlQueryStreamService streamService;

    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        getOrCreateArtifactoryHomeStub();
        doReturn(mock(SqlQuery.class)).when(sqlQueryBuilder).buildQuery(any());
        doReturn(mock(AqlLazyResult.class)).when(dao).executeStreamedResponse(any(), any(), any(), anyLong(), anyInt());
        ReflectionTestUtils.setField(streamService, "sqlQueryBuilder", sqlQueryBuilder);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void getStreamedDbResponse() {
        streamService.getAqlQueryStreamedDbResponse(null, 2);
        verify(dao, times(1)).executeStreamedResponse(any(SqlQuery.class), any(AqlPermissionProviderImpl.class), any(
                AqlRepoProvider.class), eq(ConstantValues.aqlStreamedQueryTimeout.getLong()), eq(2));
    }

    @Test
    void getStreamedDbResponseChangedConstantValue() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.aqlStreamedQueryTimeout, "2");
        streamService.getAqlQueryStreamedDbResponse(null, 2);
        verify(dao, times(1)).executeStreamedResponse(any(SqlQuery.class), any(AqlPermissionProviderImpl.class), any(
                AqlRepoProvider.class), eq(2L), eq(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetCombinedResponsesSuccessful() throws ExecutionException, InterruptedException {
        Future<AqlJsonStreamer> futureLocal = mock(Future.class);
        Future<AqlJsonStreamer> futureRemote = mock(Future.class);
        AqlQuery<AqlRowResult> aqlQuery = mock(AqlQuery.class);
        AqlJsonStreamer localResult = mock(AqlJsonLocalStreamer.class);
        AqlJsonStreamer remoteResult = mock(AqlJsonMultiStreamer.class);
        when(futureLocal.get()).thenReturn(localResult);
        when(futureRemote.get()).thenReturn(remoteResult);

        List<AqlJsonStreamer> responses = streamService.getCombinedResponses(futureLocal, futureRemote, aqlQuery);
        assertTrue(responses.containsAll(List.of(localResult, remoteResult)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGetCombinedResponsesWithException() throws ExecutionException, InterruptedException, IOException {
        Future<AqlJsonStreamer> futureLocal = mock(Future.class);
        Future<AqlJsonStreamer> futureRemote = mock(Future.class);
        AqlQuery<AqlRowResult> aqlQuery = mock(AqlQuery.class);
        when(aqlQuery.toNative()).thenReturn("");
        AqlJsonStreamer localResult = spy(AqlJsonStreamer.class);
        when(futureLocal.get()).thenReturn(localResult);
        when(futureRemote.get()).thenThrow(ExecutionException.class);

        AqlResultRuntimeException ex = assertThrows(AqlResultRuntimeException.class, () -> {
            streamService.getCombinedResponses(futureLocal, futureRemote, aqlQuery);
        });
        assertTrue(ex.getMessage().contains("Transitive AQL query has failed to process"));
        verify(localResult, times(1)).close();
    }

    @ParameterizedTest
    @MethodSource("provideAqlQueries")
    void testGetStreamResponseForSonarCoverage(Function<AqlQueryStreamService, AqlRestResult> function)
            throws IOException {
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.getAqlQueryTimeoutByLicense()).thenReturn(0L);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        AqlLazyResult<AqlRowResult> aqlQueryStreamResult = mock(AqlLazyResult.class);
        when(dao.executeQueryLazy(any(), any(), any(), anyLong())).thenReturn(aqlQueryStreamResult);

        try (AqlRestResult localStreamerWithWrapper = function.apply(streamService)) {
            assertTrue(localStreamerWithWrapper instanceof AqlJsonLocalStreamer);
        }
    }

    @Test
    void testGetTransitiveRemoteStreamerObjectInitiation() {
        AqlQuery<AqlRowResult> aqlQuery = mock(AqlQuery.class);
        when(aqlQuery.getLimit()).thenReturn(0L);
        when(aqlQuery.getOffset()).thenReturn(0L);
        when(aqlQuery.getQueryRepoType()).thenReturn(AqlQueryRepoType.remote);
        when(aqlQuery.getDomain()).thenReturn(AqlDomainEnum.items);
        doReturn(List.of()).when(streamService).handleTransitiveRemote(aqlQuery);

        AqlRestResult aqlRestResult = streamService.getTransitiveRemoteStreamer(aqlQuery);

        assertInstanceOf(AqlJsonMultiStreamer.class, aqlRestResult);
    }

    @Test
    void getAqlJsonStreamerCallableReturnsRemoteStreamer() throws Exception {
        AqlRepoProvider repoProvider = mock(AqlRepoProvider.class);
        ReflectionTestUtils.setField(streamService, "repoProvider", repoProvider);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine line = mock(StatusLine.class);
        when(line.getStatusCode()).thenReturn(HttpURLConnection.HTTP_CREATED);
        when(response.getStatusLine()).thenReturn(line);
        doReturn(response).when(repoProvider).executeAqlRemotely(anyString(), anyString());

        AqlJsonStreamer streamer = streamService.getAqlJsonStreamerCallable(anyString(), anyString()).call();

        assertInstanceOf(AqlJsonRemoteStreamer.class, streamer);
    }

    @Test
    void testGetTransitiveVirtualStreamerObjectInitiation() {
        AqlQuery<AqlRowResult> aqlQuery = mock(AqlQuery.class);
        when(aqlQuery.getLimit()).thenReturn(0L);
        when(aqlQuery.getOffset()).thenReturn(0L);
        when(aqlQuery.getQueryRepoType()).thenReturn(AqlQueryRepoType.virtual);
        when(aqlQuery.getDomain()).thenReturn(AqlDomainEnum.items);
        doReturn(List.of()).when(streamService).handleTransitiveVirtual(aqlQuery);

        AqlRestResult aqlRestResult = streamService.getTransitiveVirtualStreamer(aqlQuery);

        assertInstanceOf(AqlJsonMultiStreamer.class, aqlRestResult);
    }

    static Stream<Arguments> provideAqlQueries() {
        return Stream.of(
                Arguments.of((Function<AqlQueryStreamService, AqlRestResult>) service ->
                        service.getLocalStreamerWithWrapper(mock(AqlQuery.class))),
                Arguments.of((Function<AqlQueryStreamService, AqlRestResult>) service ->
                        service.getStreamedDbResponse(mock(AqlQuery.class), 0))
        );
    }
}