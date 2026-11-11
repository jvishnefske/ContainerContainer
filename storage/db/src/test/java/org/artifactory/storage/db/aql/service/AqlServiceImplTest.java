package org.artifactory.storage.db.aql.service;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlTooManyRequestsException;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlOverloadSettings;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlQueryContext;
import org.artifactory.aql.model.AqlQueryRepoType;
import org.artifactory.aql.model.AqlQueryTemplate;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.model.AqlTrackingInfo;
import org.artifactory.aql.result.AqlJsonHttpResponseStreamer;
import org.artifactory.aql.result.AqlJsonMultiStreamer;
import org.artifactory.aql.result.AqlJsonStreamer;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.RequestWrapper;
import org.artifactory.storage.common.semaphore.UserGlobalSemaphoresManager;
import org.artifactory.storage.db.aql.parser.AqlParser;
import org.artifactory.storage.db.aql.parser.ParserElementResultContainer;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ParserToAqlAdapter;
import org.artifactory.storage.db.aql.stats.AqlQueryStatsManager;
import org.artifactory.storage.db.aql.stats.AqlQueryTemplateBuilder;
import org.artifactory.storage.db.aql.stats.AqlStatsEvent;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.String.valueOf;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildName;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildNumber;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildRepo;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemName;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemPath;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemRepo;
import static org.artifactory.common.ConstantValues.aqlQueriesLimitEnabled;
import static org.artifactory.common.ConstantValues.aqlQueryScanFactor;
import static org.artifactory.common.ConstantValues.aqlSearchQueryMaxLimit;
import static org.artifactory.common.ConstantValues.aqlStreamedFetchSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Slutsky
 */
class AqlServiceImplTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    AqlServiceImpl service;
    @Mock
    AqlQueryStreamService streamService;
    @Mock
    ArtifactoryDbProperties dbProperties;

    AutoCloseable closeable;

    public static Stream<Arguments> provider() {
        return Stream.of(
                Arguments.of(DbType.POSTGRESQL, AqlOverloadSettings.builder().fetchSize(1).build(), 1),
                Arguments.of(DbType.MYSQL, AqlOverloadSettings.builder().fetchSize(1).build(), 1),
                Arguments.of(DbType.MARIADB, AqlOverloadSettings.builder().fetchSize(1).build(), 1),
                Arguments.of(DbType.ORACLE, AqlOverloadSettings.builder().fetchSize(1).build(), 1),
                Arguments.of(DbType.MSSQL, AqlOverloadSettings.builder().fetchSize(1).build(), 1),
                Arguments.of(DbType.POSTGRESQL, AqlOverloadSettings.builder().build(), 0),
                Arguments.of(DbType.MYSQL, AqlOverloadSettings.builder().build(), 0),
                Arguments.of(DbType.MARIADB, AqlOverloadSettings.builder().build(), 0),
                Arguments.of(DbType.ORACLE, AqlOverloadSettings.builder().build(), 0),
                Arguments.of(DbType.MSSQL, AqlOverloadSettings.builder().build(), 0),
                Arguments.of(DbType.DERBY, AqlOverloadSettings.builder().fetchSize(1).build(), 0),
                Arguments.of(DbType.DERBY, AqlOverloadSettings.builder().build(), 0)
        );
    }

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "streamService", streamService);
        ReflectionTestUtils.setField(service, "storageProperties", dbProperties);
        when(service.chooseStreamer(any(), any(), eq(false))).thenCallRealMethod();
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @ParameterizedTest
    @MethodSource("provider")
    void chooseStreamer(DbType type, AqlOverloadSettings settings, int times) {
        when(dbProperties.getDbType()).thenReturn(type);
        AqlQuery<?> query = mock(AqlQuery.class);
        when(query.getQueryRepoType()).thenReturn(AqlQueryRepoType.local);
        getOrCreateArtifactoryHomeStub().setProperty(aqlQueryScanFactor, "5");
        try (AqlRestResult aqlRestResult = service.chooseStreamer(query, settings, false)) {
            if (times != 1) {
                verify(streamService, times(1)).getLocalStreamerWithWrapper(query, false);
            }
            verify(streamService, times(times))
                    .getStreamedDbResponse(eq(query), eq(settings.getFetchSize()), any(AqlQueryContext.class));
        } catch (Exception e) {
            fail();
        }

        restorePropertiesDefValues(aqlQueryScanFactor);
    }

    @Test
    void chooseMultiStreamerWithStreamersFunctions() throws IOException {
        AqlOverloadSettings settings = AqlOverloadSettings.builder().fetchSize(1)
                .streamFunc(this::mockedStreamFunc).build();
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        var query = mock(AqlQuery.class);
        when(streamService.getMultiStreamerWithStreamersFunctions(query, settings)).thenCallRealMethod();
        when(query.getQueryRepoType()).thenReturn(AqlQueryRepoType.local);
        try (AqlRestResult aqlRestResult = service.chooseStreamer(query, settings, false)) {
            verify(streamService, times(1))
                    .getMultiStreamerWithStreamersFunctions(query, settings);
        }
    }

    @Test
    void chooseStreamerWhenEnlargeLimit() throws IOException {
        long scanFactor = 5L;
        long originalLimit = 30L;

        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        getOrCreateArtifactoryHomeStub().setProperty(aqlQueryScanFactor, valueOf(scanFactor + 1L));
        getOrCreateArtifactoryHomeStub().setProperty(aqlSearchQueryMaxLimit, valueOf(1000L));
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.aqlStreamedFetchSize, valueOf(100L));

        AqlQuery<?> query = new AqlQuery<>();
        query.setQueryRepoType(AqlQueryRepoType.local);
        query.setLimit(originalLimit);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("scan-factor")).thenReturn(valueOf(scanFactor));
        RequestThreadLocal.bind(new RequestWrapper(request));

        AqlRestResult mockResult = mock(AqlRestResult.class);
        when(streamService.getStreamedDbResponse(eq(query), eq(100),
                any(AqlQueryContext.class))).thenReturn(mockResult);

        try (AqlRestResult result = service.chooseStreamer(query, null, false)) {
            AqlQueryContext createdAqlQueryContext = new AqlQueryContext(AqlQueryRepoType.local,
                    null,
                    false,
                    originalLimit);
            verify(streamService, times(1)).getStreamedDbResponse(eq(query),
                    eq(100),
                    eq(createdAqlQueryContext));
            verify(streamService, times(0)).getLocalStreamerWithWrapper(any(), anyBoolean());
            assertEquals(originalLimit * scanFactor, query.getLimit());
        }

        restorePropertiesDefValues(aqlQueryScanFactor, aqlSearchQueryMaxLimit, aqlStreamedFetchSize);
    }

    @Test
    void testAddingRequiredFieldsToAqlQueryForAdmin() throws Exception {
        AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);
        when(permissionProvider.isAdmin()).thenReturn(true);
        ReflectionTestUtils.setField(service, "permissionProvider",
                permissionProvider);

        String query = "items.find().include(\"repo\", \"created\")";
        AqlParser parser = new AqlParser();
        ParserElementResultContainer parserResult = parser.parse(query);
        ParserToAqlAdapter parserToAqlAdapter = new ParserToAqlAdapter();
        AqlQuery<AqlRowResult> aqlQuery = parserToAqlAdapter.toAqlModel(parserResult);
        doCallRealMethod().when(service).addRequiredFieldsToAqlQueryForAdmin(aqlQuery);
        Field field = AqlServiceImpl.class.getDeclaredField("requiredAqlItemsEnums");
        field.setAccessible(true);
        field.set(service, EnumSet.of(itemRepo, itemPath, itemName));
        service.addRequiredFieldsToAqlQueryForAdmin(aqlQuery);

        List<String> list = aqlQuery.getResultFields().stream()
                .map(f -> f.getField().getName())
                .toList();
        assertEquals(list, List.of("itemRepo", "itemCreated", "itemPath", "itemName"));
    }

    @Test
    void testAddingRequiredFieldsToAqlQueryForAdmin_buildsDomain() throws Exception {
        // Arrange
        AqlPermissionProvider permissionProvider = mock(AqlPermissionProvider.class);
        when(permissionProvider.isAdmin()).thenReturn(true);
        ReflectionTestUtils.setField(service, "permissionProvider", permissionProvider);

        String query = "builds.find().include(\"name\", \"created\")";
        AqlParser parser = new AqlParser();
        ParserElementResultContainer parserResult = parser.parse(query);
        ParserToAqlAdapter parserToAqlAdapter = new ParserToAqlAdapter();
        AqlQuery<AqlRowResult> aqlQuery = parserToAqlAdapter.toAqlModel(parserResult);
        doCallRealMethod().when(service).addRequiredFieldsToAqlQueryForAdmin(aqlQuery);
        Field field = AqlServiceImpl.class.getDeclaredField("requiredAqlBuildsEnums");
        field.setAccessible(true);
        field.set(service, EnumSet.of(buildName, buildNumber, buildRepo));

        // Act
        service.addRequiredFieldsToAqlQueryForAdmin(aqlQuery);

        // Assert
        List<String> resultFieldNames = aqlQuery.getResultFields().stream()
                .map(f -> f.getField().getName())
                .toList();
        assertEquals(List.of("buildName", "buildCreated", "buildNumber", "buildRepo"), resultFieldNames);
    }

    @ParameterizedTest
    @MethodSource("restResultClasses")
    void testAddingMetrics(Class<? extends AqlRestResult> restResultClass) throws IOException {
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("agent");
            MeterRegistry metrics = new SimpleMeterRegistry();
            ReflectionTestUtils.setField(service, "metrics", metrics);
            ReflectionTestUtils.setField(service, "largestResultSetSize",
                    new AqlServiceImpl.LargestResultSetSize(0, 0));
            when(service.streamResponse(any(Boolean.class), any(AqlRestResult.class), any(AqlTrackingInfo.class)))
                    .thenCallRealMethod();
            var restResult = mock(restResultClass);
            when(restResult.read()).thenReturn(new byte[0]);
            when(restResult.getRowsCount()).thenReturn(1L);
            when(restResult.getAqlQueryContext()).
                    thenReturn(new AqlQueryContext(AqlQueryRepoType.local, AqlDomainEnum.artifacts));
            Response response = service.streamResponse(false, restResult, new AqlTrackingInfo("origin", "query"));
            StreamingOutput entity = (StreamingOutput) response.getEntity();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            entity.write(outputStream);

            DistributionSummary distributionSummary = metrics
                    .find("search.aql.result.set.size")
                    .tags("origin", "origin",
                            "query_repo_type", restResult.getAqlQueryContext().queryRepoType().getName(),
                            "query_domain", restResult.getAqlQueryContext().domain().signature)
                    .summary();
            assert distributionSummary != null;
            assertEquals(1, distributionSummary.count());
        } catch (Exception e) {
            fail("Should not throw exception when checking for unlimited user agent", e);
        }
    }

    @Test
    void recordAqlStatistics_skipsWhenStreamingThrows() throws IOException {
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("agent");
            mocked.when(() -> RequestThreadLocal.getRequestAttribute(
                            AqlQueryTemplate.REQ_ATTR_KEY, AqlQueryTemplate.class))
                    .thenReturn(new AqlQueryTemplate("tpl", "abc"));

            AqlQueryStatsManager statsManager = mock(AqlQueryStatsManager.class);
            ReflectionTestUtils.setField(service, "aqlQueryStatsManager", statsManager);
            ReflectionTestUtils.setField(service, "metrics", new SimpleMeterRegistry());
            ReflectionTestUtils.setField(service, "largestResultSetSize",
                    new AqlServiceImpl.LargestResultSetSize(0, 0));
            when(service.streamResponse(any(Boolean.class), any(AqlRestResult.class), any(AqlTrackingInfo.class)))
                    .thenCallRealMethod();

            AqlRestResult restResult = mock(AqlJsonHttpResponseStreamer.class);
            when(restResult.read()).thenThrow(new RuntimeException("simulated mid-stream failure"));

            Response response = service.streamResponse(false, restResult,
                    new AqlTrackingInfo(AqlTrackingInfo.SEARCH_AQL_ORIGIN, "items.find({\"name\":\"x\"})"));
            StreamingOutput entity = (StreamingOutput) response.getEntity();
            assertThrows(RuntimeException.class, () -> entity.write(new ByteArrayOutputStream()));

            verify(statsManager, never()).offer(any(AqlStatsEvent.class));
        }
    }

    @Test
    void recordAqlStatistics_recordsSuccessfulEmptyResultSet() throws IOException {
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("agent");
            mocked.when(() -> RequestThreadLocal.getRequestAttribute(
                            AqlQueryTemplate.REQ_ATTR_KEY, AqlQueryTemplate.class))
                    .thenReturn(new AqlQueryTemplate("tpl", "abc"));

            AqlQueryStatsManager statsManager = mock(AqlQueryStatsManager.class);
            ReflectionTestUtils.setField(service, "aqlQueryStatsManager", statsManager);
            ReflectionTestUtils.setField(service, "metrics", new SimpleMeterRegistry());
            ReflectionTestUtils.setField(service, "largestResultSetSize",
                    new AqlServiceImpl.LargestResultSetSize(0, 0));
            when(service.streamResponse(any(Boolean.class), any(AqlRestResult.class), any(AqlTrackingInfo.class)))
                    .thenCallRealMethod();

            AqlRestResult restResult = mock(AqlJsonHttpResponseStreamer.class);
            when(restResult.read()).thenReturn(new byte[0]);
            when(restResult.getRowsCount()).thenReturn(0L);
            when(restResult.getAqlQueryContext())
                    .thenReturn(new AqlQueryContext(AqlQueryRepoType.local, AqlDomainEnum.artifacts));

            Response response = service.streamResponse(false, restResult,
                    new AqlTrackingInfo(AqlTrackingInfo.SEARCH_AQL_ORIGIN, "items.find({\"name\":\"missing\"})"));
            ((StreamingOutput) response.getEntity()).write(new ByteArrayOutputStream());

            verify(statsManager, times(1)).offer(argThat(e ->
                    e != null && "abc".equals(e.checksum()) && e.rowsCount() == 0L));
        }
    }

    @Test
    void recordAqlStatistics_skipsForInternalUserAgent() throws IOException {
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(true);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("xray/3.99.0");

            AqlQueryStatsManager statsManager = mock(AqlQueryStatsManager.class);
            ReflectionTestUtils.setField(service, "aqlQueryStatsManager", statsManager);
            ReflectionTestUtils.setField(service, "metrics", new SimpleMeterRegistry());
            ReflectionTestUtils.setField(service, "largestResultSetSize",
                    new AqlServiceImpl.LargestResultSetSize(0, 0));
            when(service.streamResponse(any(Boolean.class), any(AqlRestResult.class), any(AqlTrackingInfo.class)))
                    .thenCallRealMethod();

            AqlRestResult restResult = mock(AqlJsonHttpResponseStreamer.class);
            when(restResult.read()).thenReturn(new byte[0]);
            when(restResult.getRowsCount()).thenReturn(5L);
            when(restResult.getAqlQueryContext())
                    .thenReturn(new AqlQueryContext(AqlQueryRepoType.local, AqlDomainEnum.artifacts));

            Response response = service.streamResponse(false, restResult,
                    new AqlTrackingInfo(AqlTrackingInfo.SEARCH_AQL_ORIGIN, "items.find()"));
            ((StreamingOutput) response.getEntity()).write(new ByteArrayOutputStream());

            verify(statsManager, never()).offer(any(AqlStatsEvent.class));
        }
    }

    @Test
    void recordAqlStatistics_skipsForNonSearchAqlOrigin() throws IOException {
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("agent");

            AqlQueryStatsManager statsManager = mock(AqlQueryStatsManager.class);
            ReflectionTestUtils.setField(service, "aqlQueryStatsManager", statsManager);
            ReflectionTestUtils.setField(service, "metrics", new SimpleMeterRegistry());
            ReflectionTestUtils.setField(service, "largestResultSetSize",
                    new AqlServiceImpl.LargestResultSetSize(0, 0));
            when(service.streamResponse(any(Boolean.class), any(AqlRestResult.class), any(AqlTrackingInfo.class)))
                    .thenCallRealMethod();

            AqlRestResult restResult = mock(AqlJsonHttpResponseStreamer.class);
            when(restResult.read()).thenReturn(new byte[0]);
            when(restResult.getRowsCount()).thenReturn(5L);
            when(restResult.getAqlQueryContext())
                    .thenReturn(new AqlQueryContext(AqlQueryRepoType.local, AqlDomainEnum.artifacts));

            // Origin is not "search/aql" -- e.g. Mirror full-sync internal traffic
            Response response = service.streamResponse(false, restResult,
                    new AqlTrackingInfo("full-sync/aql", "items.find()"));
            ((StreamingOutput) response.getEntity()).write(new ByteArrayOutputStream());

            verify(statsManager, never()).offer(any(AqlStatsEvent.class));
        }
    }

    @Test
    void cacheQueryTemplateIfEligible_skipsWhenStatsDisabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.aqlStatisticsCollectionEnabled, "false");
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            AqlQuery<AqlRowResult> aqlQuery = parseToAqlQuery("items.find({\"name\":\"manifest.json\"})");
            doCallRealMethod().when(service).cacheQueryTemplateIfEligible(aqlQuery);

            service.cacheQueryTemplateIfEligible(aqlQuery);

            mocked.verify(() -> RequestThreadLocal.setRequestAttribute(eq(AqlQueryTemplate.REQ_ATTR_KEY), any()),
                    never());
        }
        restorePropertiesDefValues(ConstantValues.aqlStatisticsCollectionEnabled);
    }

    @Test
    void cacheQueryTemplateIfEligible_skipsForUnlimitedUserAgent() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.aqlStatisticsCollectionEnabled, "true");
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("xray/3.99.0");
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(true);
            AqlQuery<AqlRowResult> aqlQuery = parseToAqlQuery("items.find({\"name\":\"manifest.json\"})");
            doCallRealMethod().when(service).cacheQueryTemplateIfEligible(aqlQuery);

            service.cacheQueryTemplateIfEligible(aqlQuery);

            mocked.verify(() -> RequestThreadLocal.setRequestAttribute(eq(AqlQueryTemplate.REQ_ATTR_KEY), any()),
                    never());
        }
        restorePropertiesDefValues(ConstantValues.aqlStatisticsCollectionEnabled);
    }

    @Test
    void cacheQueryTemplateIfEligible_idempotent_skipsWhenAlreadyCached() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.aqlStatisticsCollectionEnabled, "true");
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("external-client");
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(() -> RequestThreadLocal.getRequestAttribute(
                            AqlQueryTemplate.REQ_ATTR_KEY, AqlQueryTemplate.class))
                    .thenReturn(new AqlQueryTemplate("already-there", "deadbeef"));
            AqlQuery<AqlRowResult> aqlQuery = parseToAqlQuery("items.find({\"name\":\"manifest.json\"})");
            doCallRealMethod().when(service).cacheQueryTemplateIfEligible(aqlQuery);

            service.cacheQueryTemplateIfEligible(aqlQuery);

            mocked.verify(() -> RequestThreadLocal.setRequestAttribute(eq(AqlQueryTemplate.REQ_ATTR_KEY), any()),
                    never());
        }
        restorePropertiesDefValues(ConstantValues.aqlStatisticsCollectionEnabled);
    }

    @Test
    void cacheQueryTemplateIfEligible_publishesTemplate_onHappyPath() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.aqlStatisticsCollectionEnabled, "true");
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("external-client");
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(() -> RequestThreadLocal.getRequestAttribute(
                            AqlQueryTemplate.REQ_ATTR_KEY, AqlQueryTemplate.class))
                    .thenReturn(null);
            AqlQuery<AqlRowResult> aqlQuery = parseToAqlQuery("items.find({\"name\":\"manifest.json\"})");
            doCallRealMethod().when(service).cacheQueryTemplateIfEligible(aqlQuery);

            service.cacheQueryTemplateIfEligible(aqlQuery);

            mocked.verify(() -> RequestThreadLocal.setRequestAttribute(eq(AqlQueryTemplate.REQ_ATTR_KEY),
                            argThat(o -> o instanceof AqlQueryTemplate t
                                    && t.template() != null && t.checksum() != null)),
                    times(1));
        }
        restorePropertiesDefValues(ConstantValues.aqlStatisticsCollectionEnabled);
    }

    @Test
    void cacheQueryTemplateIfEligible_swallowsBuilderExceptionAndDoesNotPublish() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.aqlStatisticsCollectionEnabled, "true");
        try (
                MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class);
                MockedStatic<AqlQueryTemplateBuilder> builder = mockStatic(AqlQueryTemplateBuilder.class)
        ) {
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("external-client");
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(() -> RequestThreadLocal.getRequestAttribute(
                            AqlQueryTemplate.REQ_ATTR_KEY, AqlQueryTemplate.class))
                    .thenReturn(null);
            builder.when(() -> AqlQueryTemplateBuilder.build(any()))
                    .thenThrow(new RuntimeException("simulated builder failure"));

            AqlQuery<AqlRowResult> aqlQuery = parseToAqlQuery("items.find({\"name\":\"manifest.json\"})");
            doCallRealMethod().when(service).cacheQueryTemplateIfEligible(aqlQuery);

            assertDoesNotThrow(() -> service.cacheQueryTemplateIfEligible(aqlQuery),
                    "Builder failure must be swallowed -- caller does not need to know");
            mocked.verify(() -> RequestThreadLocal.setRequestAttribute(eq(AqlQueryTemplate.REQ_ATTR_KEY), any()),
                    never());
        }
        restorePropertiesDefValues(ConstantValues.aqlStatisticsCollectionEnabled);
    }

    @Test
    void computeQueryTemplate_returnsNullOnParseException() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.aqlStatisticsCollectionEnabled, "true");
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("external-client");
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(() -> RequestThreadLocal.getRequestAttribute(
                            AqlQueryTemplate.REQ_ATTR_KEY, AqlQueryTemplate.class))
                    .thenReturn(null);

            AqlParser mockParser = mock(AqlParser.class);
            when(mockParser.parse(anyString())).thenThrow(new RuntimeException("parse error"));
            ReflectionTestUtils.setField(service, "parser", mockParser);

            doCallRealMethod().when(service).computeQueryTemplate(any());
            assertNull(service.computeQueryTemplate("items.find()"));
        }
        restorePropertiesDefValues(ConstantValues.aqlStatisticsCollectionEnabled);
    }

    @Test
    void computeQueryTemplate_returnsNullWhenStatisticsCollectionDisabled() {
        doCallRealMethod().when(service).computeQueryTemplate(anyString());
        assertNull(service.computeQueryTemplate("items.find()"));
    }

    @Test
    void computeQueryTemplate_returnsCachedTemplateWhenAlreadyPopulated() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.aqlStatisticsCollectionEnabled, "true");
        AqlQueryTemplate cached = mock(AqlQueryTemplate.class);
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("external-client");
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(
                            () -> RequestThreadLocal.getRequestAttribute(AqlQueryTemplate.REQ_ATTR_KEY,
                                    AqlQueryTemplate.class))
                    .thenReturn(cached);
            doCallRealMethod().when(service).computeQueryTemplate(anyString());
            assertSame(cached, service.computeQueryTemplate("items.find()"));
        }
        restorePropertiesDefValues(ConstantValues.aqlStatisticsCollectionEnabled);
    }

    @Test
    void computeQueryTemplate_buildsAndCachesTemplateOnHappyPath() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.aqlStatisticsCollectionEnabled, "true");
        wireRealParserPipeline();
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("external-client");
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(
                            () -> RequestThreadLocal.getRequestAttribute(AqlQueryTemplate.REQ_ATTR_KEY,
                                    AqlQueryTemplate.class))
                    .thenReturn(null);
            doCallRealMethod().when(service).computeQueryTemplate(anyString());

            AqlQueryTemplate result = service.computeQueryTemplate("items.find()");

            assertNotNull(result);
            mocked.verify(() -> RequestThreadLocal.setRequestAttribute(
                    eq(AqlQueryTemplate.REQ_ATTR_KEY), any(AqlQueryTemplate.class)));
        }
        restorePropertiesDefValues(ConstantValues.aqlStatisticsCollectionEnabled);
    }

    private AqlQuery<AqlRowResult> parseToAqlQuery(String query) {
        AqlParser p = new AqlParser();
        ParserElementResultContainer parserResult = p.parse(query);
        return new ParserToAqlAdapter().toAqlModel(parserResult);
    }

    private void wireRealParserPipeline() {
        ReflectionTestUtils.setField(service, "parser", new AqlParser());
        ReflectionTestUtils.setField(service, "parserToAqlAdapter", new ParserToAqlAdapter());
    }


    @Test
    void executeQueryLazy_textualEntryPoint_doesNotCacheTemplate() {
        wireRealParserPipeline();
        when(service.executeQueryLazy(anyString(), any(AqlOverloadSettings.class))).thenCallRealMethod();
        when(service.executeQueryLazy(anyString())).thenCallRealMethod();
        when(service.optimizeAndStreamLazy(any(AqlQuery.class), any(), anyBoolean()))
                .thenReturn(mock(AqlRestResult.class));

        try (AqlRestResult ignored = service.executeQueryLazy("items.find()")) {
            verify(service, never()).cacheQueryTemplateIfEligible(any());
        } catch (Exception e) {
            fail("executeQueryLazy(String) must not propagate exceptions in this test", e);
        }

        try (AqlRestResult ignored = service.executeQueryLazy("items.find()", null)) {
            verify(service, never()).cacheQueryTemplateIfEligible(any());
        } catch (Exception e) {
            fail("executeQueryLazy(String, settings) must not propagate exceptions in this test", e);
        }
    }

    @Test
    void executeQueryLazyWithLimit_searchAqlEntryPoint_cachesTemplate() {
        getOrCreateArtifactoryHomeStub().setProperty(aqlQueriesLimitEnabled, valueOf(false));
        SecurityService mockSecurityService = setupMockSecurityService("test-user");
        ReflectionTestUtils.setField(service, "securityService", mockSecurityService);
        wireRealParserPipeline();

        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("external-client");
            mocked.when(RequestThreadLocal::shouldStream).thenReturn(false);

            when(service.executeQueryLazyWithLimit(anyString())).thenCallRealMethod();
            when(service.prepareAqlOverloadSettings()).thenCallRealMethod();
            when(service.optimizeAndStreamLazy(any(AqlQuery.class), any(), anyBoolean()))
                    .thenReturn(mock(AqlRestResult.class));

            try (AqlRestResult ignored = service.executeQueryLazyWithLimit("items.find()")) {
                verify(service, times(1)).cacheQueryTemplateIfEligible(any(AqlQuery.class));
            } catch (Exception e) {
                fail("executeQueryLazyWithLimit must not propagate exceptions in this test", e);
            }
        }

        restorePropertiesDefValues(aqlQueriesLimitEnabled);
    }

    @Test
    void executeQueryLazyWithLimit_cachesTemplateBeforeOptimize() {
        getOrCreateArtifactoryHomeStub().setProperty(aqlQueriesLimitEnabled, valueOf(false));
        SecurityService mockSecurityService = setupMockSecurityService("test-user");
        ReflectionTestUtils.setField(service, "securityService", mockSecurityService);
        wireRealParserPipeline();

        AqlQueryOptimizer optimizer = mock(AqlQueryOptimizer.class);
        ReflectionTestUtils.setField(service, "optimizer", optimizer);

        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("external-client");
            mocked.when(RequestThreadLocal::shouldStream).thenReturn(false);

            when(service.executeQueryLazyWithLimit(anyString())).thenCallRealMethod();
            when(service.prepareAqlOverloadSettings()).thenCallRealMethod();
            when(service.optimizeAndStreamLazy(any(AqlQuery.class), any(), anyBoolean()))
                    .thenAnswer(invocation -> {
                        // Drive the real optimizer call to surface ordering against cacheTemplate.
                        optimizer.optimize(invocation.getArgument(0));
                        return mock(AqlRestResult.class);
                    });

            try (AqlRestResult ignored = service.executeQueryLazyWithLimit("items.find()")) {
                InOrder inOrder = inOrder(service, optimizer);
                inOrder.verify(service).cacheQueryTemplateIfEligible(any(AqlQuery.class));
                inOrder.verify(optimizer).optimize(any(AqlQuery.class));
            } catch (Exception e) {
                fail("executeQueryLazyWithLimit must not propagate exceptions in this test", e);
            }
        }

        restorePropertiesDefValues(aqlQueriesLimitEnabled);
    }

    @Test
    void optimizeAndStreamLazy_callsCollaboratorsInOrder() {
        AqlQuery<AqlRowResult> aqlQuery = parseToAqlQuery("items.find()");
        AqlQueryOptimizer mockOptimizer = mock(AqlQueryOptimizer.class);
        AqlQueryValidator mockValidator = mock(AqlQueryValidator.class);
        AqlQueryDecorator mockDecorator = mock(AqlQueryDecorator.class);
        ReflectionTestUtils.setField(service, "optimizer", mockOptimizer);
        ReflectionTestUtils.setField(service, "validator", mockValidator);
        ReflectionTestUtils.setField(service, "externalDecorator", mockDecorator);

        ReflectionTestUtils.setField(service, "permissionProvider", mock(AqlPermissionProvider.class));
        when(service.optimizeAndStreamLazy(any(AqlQuery.class), isNull(), anyBoolean())).thenCallRealMethod();
        doCallRealMethod().when(service).addRequiredFieldsToAqlQueryForAdmin(any());
        doReturn(mock(AqlRestResult.class)).when(service).chooseStreamer(any(AqlQuery.class), isNull(), eq(false));

        service.optimizeAndStreamLazy(aqlQuery, null, false);

        InOrder order = inOrder(mockOptimizer, mockValidator, mockDecorator, service);
        order.verify(mockOptimizer).optimize(aqlQuery);
        order.verify(mockValidator).validate(eq(aqlQuery), any());
        order.verify(service).addRequiredFieldsToAqlQueryForAdmin(aqlQuery);
        order.verify(mockDecorator).decorate(eq(aqlQuery), any());
        order.verify(service).chooseStreamer(aqlQuery, null, false);
    }

    @Test
    void testNoMetricsHasBeenReported() {
        MeterRegistry metrics = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(service, "metrics", metrics);
        when(service.streamResponse(any(Boolean.class), any(AqlRestResult.class))).thenCallRealMethod();
        when(service.streamResponse(any(Boolean.class), any(AqlRestResult.class), any(AqlTrackingInfo.class)))
                .thenCallRealMethod();
        var restResult = mock(AqlRestResult.class);
        service.streamResponse(false, restResult);
        verify(service, times(1)).streamResponse(false, restResult, null);
    }

    @Test
    void testScheduledLogLargestResultSetSize() {
        AqlServiceImpl.LargestResultSetSize largestResultSetSize =
                Mockito.mock(AqlServiceImpl.LargestResultSetSize.class);
        when(largestResultSetSize.getSize()).thenReturn(1L);
        ReflectionTestUtils.setField(service, "largestResultSetSize",
                largestResultSetSize);
        doCallRealMethod().when(service).scheduledLogLargestResultSetSize();
        service.scheduledLogLargestResultSetSize();
        verify(largestResultSetSize, times(1)).getTimestamp();
    }

    @Test
    void testNoDefaultLimitForUnlimitedUser() {
        getOrCreateArtifactoryHomeStub().setProperty(aqlSearchQueryMaxLimit, valueOf(2L));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getPathInfo()).thenReturn("search/aql");
        RequestThreadLocal.bind(new RequestWrapper(request));

        doCallRealMethod().when(service).optionallyApplyAqlLimit(any());
        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(true);
            mocked.when(RequestThreadLocal::getRequestPath).thenReturn("search/aql");
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("arti");
            ParserElementResultContainer parserResult = mock(ParserElementResultContainer.class);
            boolean applyAqlLimit = service.optionallyApplyAqlLimit(parserResult);
            assertFalse(applyAqlLimit);
            verify(parserResult, times(0)).getAll();
        } catch (Exception e) {
            fail("Should not throw exception when checking for unlimited user agent", e);
        }

        restorePropertiesDefValues(aqlSearchQueryMaxLimit);
    }

    @ParameterizedTest
    @MethodSource("aqlSearchStreamOptions")
    void testStreamHeaderForAqlSearch(boolean stream, int times) {
        getOrCreateArtifactoryHomeStub().setProperty(aqlStreamedFetchSize, valueOf(2L));

        SecurityService mockSecurityService = setupMockSecurityService("test-user");
        ReflectionTestUtils.setField(service, "securityService", mockSecurityService);
        wireRealParserPipeline();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("stream")).thenReturn(valueOf(stream));
        RequestThreadLocal.bind(new RequestWrapper(request));
        when(service.executeQueryLazyWithLimit("items.find()")).thenCallRealMethod();
        when(service.prepareAqlOverloadSettings()).thenCallRealMethod();
        AqlOverloadSettings expectedSettings = AqlOverloadSettings.builder()
                .fetchSize(aqlStreamedFetchSize.getInt())
                .build();

        when(service.optimizeAndStreamLazy(any(AqlQuery.class), any(AqlOverloadSettings.class), anyBoolean()))
                .thenReturn(mock(AqlRestResult.class));
        when(service.optimizeAndStreamLazy(any(AqlQuery.class), isNull(), anyBoolean()))
                .thenReturn(mock(AqlRestResult.class));
        try (AqlRestResult restResult = service.executeQueryLazyWithLimit("items.find()")) {
            verify(service, times(times)).optimizeAndStreamLazy(any(AqlQuery.class),
                    eq(expectedSettings), anyBoolean());
        } catch (Exception e) {
            fail();
        }

        restorePropertiesDefValues(aqlStreamedFetchSize);
    }

    @ParameterizedTest
    @MethodSource("bypassSemaphoreScenarios")
    void testExecuteQueryLazyWithLimit_shouldBypassSemaphore(boolean limitEnabled, boolean isUnlimitedAgent,
            String userAgent, String username, String description) {
        getOrCreateArtifactoryHomeStub().setProperty(aqlQueriesLimitEnabled, valueOf(limitEnabled));

        SecurityService mockSecurityService = setupMockSecurityService(username);
        ReflectionTestUtils.setField(service, "securityService", mockSecurityService);
        wireRealParserPipeline();

        // Create mock semaphore managers to verify they are NOT called
        UserGlobalSemaphoresManager mockRegularManager = mock(UserGlobalSemaphoresManager.class);
        UserGlobalSemaphoresManager mockWorkersManager = mock(UserGlobalSemaphoresManager.class);
        ReflectionTestUtils.setField(service, "regularSemaphoreManager", mockRegularManager);
        ReflectionTestUtils.setField(service, "workersSemaphoreManager", mockWorkersManager);

        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(isUnlimitedAgent);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn(userAgent);
            mocked.when(RequestThreadLocal::shouldStream).thenReturn(false);

            when(service.prepareAqlOverloadSettings()).thenReturn(null);
            when(service.optimizeAndStreamLazy(any(AqlQuery.class), any(AqlOverloadSettings.class), anyBoolean()))
                    .thenReturn(mock(AqlRestResult.class));
            when(service.optimizeAndStreamLazy(any(AqlQuery.class), isNull(), anyBoolean()))
                    .thenReturn(mock(AqlRestResult.class));
            when(service.executeQueryLazyWithLimit(anyString())).thenCallRealMethod();

            try (AqlRestResult result = service.executeQueryLazyWithLimit("items.find()")) {
                verify(service).optimizeAndStreamLazy(any(AqlQuery.class), isNull(), anyBoolean());
                // Verify that neither semaphore manager was called
                verify(mockRegularManager, times(0)).tryAcquire(anyString());
                verify(mockWorkersManager, times(0)).tryAcquire(anyString());
            } catch (Exception e) {
                fail(description + " - Exception occurred: " + e.getMessage(), e);
            }
        }

        restorePropertiesDefValues(aqlQueriesLimitEnabled);
    }

    private static Stream<Arguments> bypassSemaphoreScenarios() {
        return Stream.of(
                Arguments.of(false, false, "regular-client", "test-user",
                        "Should bypass semaphore when limit is disabled"),
                Arguments.of(true, true, "xray", "test-user",
                        "Should bypass semaphore for unlimited user agent"),
                Arguments.of(true, false, "regular-client", SecurityService.USER_SYSTEM,
                        "Should bypass semaphore for USER_SYSTEM even when limit is enabled")
        );
    }

    @ParameterizedTest
    @MethodSource("semaphoreManagerSelectionScenarios")
    void testExecuteQueryLazyWithLimit_selectsCorrectSemaphoreManager(String userAgent, String username,
            String managerFieldName, String description) throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(aqlQueriesLimitEnabled, valueOf(true));

        SecurityService mockSecurityService = setupMockSecurityService(username);
        ReflectionTestUtils.setField(service, "securityService", mockSecurityService);

        UserGlobalSemaphoresManager mockSemaphoreManager = mock(UserGlobalSemaphoresManager.class);
        UserGlobalSemaphoresManager.PermitsHolder mockPermitsHolder =
                mock(UserGlobalSemaphoresManager.PermitsHolder.class);
        when(mockSemaphoreManager.tryAcquire(anyString())).thenReturn(mockPermitsHolder);
        ReflectionTestUtils.setField(service, managerFieldName, mockSemaphoreManager);
        wireRealParserPipeline();

        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn(userAgent);
            mocked.when(RequestThreadLocal::shouldStream).thenReturn(false);

            when(service.executeQueryLazyWithLimit(anyString())).thenCallRealMethod();
            when(service.prepareAqlOverloadSettings()).thenCallRealMethod();
            when(service.isWorker()).thenCallRealMethod();
            when(service.optimizeAndStreamLazy(any(AqlQuery.class), any(), anyBoolean()))
                    .thenReturn(mock(AqlRestResult.class));

            try (AqlRestResult result = service.executeQueryLazyWithLimit("items.find()")) {
                verify(service).optimizeAndStreamLazy(any(AqlQuery.class), any(), anyBoolean());
                verify(mockSemaphoreManager).tryAcquire(username);
                verify(mockPermitsHolder).close();
            } catch (Exception e) {
                fail(description + " failed: " + e.getMessage(), e);
            }
        }

        restorePropertiesDefValues(aqlQueriesLimitEnabled);
    }

    private static Stream<Arguments> semaphoreManagerSelectionScenarios() {
        return Stream.of(
                Arguments.of("jfrog-worker", "worker-user", "workersSemaphoreManager",
                        "Worker thread should use workersSemaphoreManager"),
                Arguments.of("artifactory-client", "regular-user", "regularSemaphoreManager",
                        "Regular user should use regularSemaphoreManager")
        );
    }

    @Test
    void testExecuteQueryLazyWithLimit_semaphoreTimeout_throwsTooManyRequestsException() throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(aqlQueriesLimitEnabled, valueOf(true));

        SecurityService mockSecurityService = setupMockSecurityService("test-user");
        ReflectionTestUtils.setField(service, "securityService", mockSecurityService);

        AqlServiceImpl realService = new AqlServiceImpl();
        ReflectionTestUtils.setField(realService, "securityService", mockSecurityService);

        UserGlobalSemaphoresManager mockRegularManager = mock(UserGlobalSemaphoresManager.class);
        when(mockRegularManager.tryAcquire(anyString())).thenReturn(null);
        ReflectionTestUtils.setField(realService, "regularSemaphoreManager", mockRegularManager);

        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("regular-client");

            assertThrows(AqlTooManyRequestsException.class,
                    () -> realService.executeQueryLazyWithLimit("items.find()"));
        }

        restorePropertiesDefValues(aqlQueriesLimitEnabled);
    }

    @Test
    void testExecuteQueryLazyWithLimit_interruptedException_handledCorrectly() throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(aqlQueriesLimitEnabled, valueOf(true));

        AqlServiceImpl aqlService = Mockito.spy(new AqlServiceImpl());

        SecurityService mockSecurityService = setupMockSecurityService("test-user");
        ReflectionTestUtils.setField(aqlService, "securityService", mockSecurityService);

        UserGlobalSemaphoresManager mockManager = mock(UserGlobalSemaphoresManager.class);
        when(mockManager.tryAcquire(anyString())).thenThrow(new InterruptedException("Test interruption"));
        ReflectionTestUtils.setField(aqlService, "regularSemaphoreManager", mockManager);

        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("regular-client");

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> aqlService.executeQueryLazyWithLimit("items.find()"));

            assertEquals("AQL Query execution interrupted while acquiring semaphore", exception.getMessage());
        }

        restorePropertiesDefValues(aqlQueriesLimitEnabled);
    }

    @Test
    void testExecuteQueryLazyWithLimit_workerBypassesPerUserLimit() throws Exception {
        getOrCreateArtifactoryHomeStub().setProperty(aqlQueriesLimitEnabled, valueOf(true));

        SecurityService mockSecurityService = setupMockSecurityService("worker-user");
        ReflectionTestUtils.setField(service, "securityService", mockSecurityService);
        wireRealParserPipeline();

        UserGlobalSemaphoresManager workersManager = new UserGlobalSemaphoresManager(3, 0, 1000, "test");
        ReflectionTestUtils.setField(service, "workersSemaphoreManager", workersManager);

        try (MockedStatic<RequestThreadLocal> mocked = mockStatic(RequestThreadLocal.class)) {
            mocked.when(() -> RequestThreadLocal.isUnlimitedUserAgent(any())).thenReturn(false);
            mocked.when(RequestThreadLocal::getUserAgent).thenReturn("jfrog-worker");
            mocked.when(RequestThreadLocal::shouldStream).thenReturn(false);

            when(service.executeQueryLazyWithLimit(anyString())).thenCallRealMethod();
            when(service.prepareAqlOverloadSettings()).thenCallRealMethod();
            when(service.isWorker()).thenCallRealMethod();
            when(service.optimizeAndStreamLazy(any(AqlQuery.class), any(), anyBoolean()))
                    .thenReturn(mock(AqlRestResult.class));

            List<AqlRestResult> results = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                results.add(service.executeQueryLazyWithLimit("items.find()"));
            }

            verify(service, times(3)).optimizeAndStreamLazy(any(AqlQuery.class), any(), anyBoolean());

            for (AqlRestResult result : results) {
                result.close();
            }
        }

        restorePropertiesDefValues(aqlQueriesLimitEnabled);
    }

    private List<AqlJsonStreamer> mockedStreamFunc(String aqlQuery,
            AqlPermissionProvider permissionProvider, AqlRepoProvider repoProvider) {
        return new ArrayList<AqlJsonStreamer>();
    }

    private static Stream<Arguments> restResultClasses() {
        return Stream.of(
                Arguments.of(AqlJsonMultiStreamer.class),
                Arguments.of(AqlJsonHttpResponseStreamer.class)
        );
    }

    private static Stream<Arguments> aqlSearchStreamOptions() {
        return Stream.of(
                Arguments.of(true, 1),
                Arguments.of(false, 0)
        );
    }

    private void restorePropertiesDefValues(ConstantValues... properties) {
        for (ConstantValues property : properties) {
            getOrCreateArtifactoryHomeStub().setProperty(property, property.getDefValue());
        }
    }

    private SecurityService setupMockSecurityService(String username) {
        SecurityService mockSecurityService = mock(SecurityService.class);
        when(mockSecurityService.currentUsername()).thenReturn(username);
        return mockSecurityService;
    }
}