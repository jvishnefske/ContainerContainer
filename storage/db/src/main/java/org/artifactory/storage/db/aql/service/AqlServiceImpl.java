/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.aql.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.AqlStatsCollectionService;
import org.artifactory.aql.AqlTooManyRequestsException;
import org.artifactory.aql.api.AqlApiElement;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlOperatorEnum;
import org.artifactory.aql.model.AqlOverloadSettings;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlQueryContext;
import org.artifactory.aql.model.AqlQueryTemplate;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.model.AqlSqlFunction;
import org.artifactory.aql.model.AqlTrackingInfo;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.AqlComposedResult;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.storage.common.semaphore.UserGlobalSemaphoresManager;
import org.artifactory.storage.db.aql.dao.AqlDao;
import org.artifactory.storage.db.aql.parser.AqlParser;
import org.artifactory.storage.db.aql.parser.ParserElementResultContainer;
import org.artifactory.storage.db.aql.parser.elements.ParserElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.LimitValueElement;
import org.artifactory.storage.db.aql.service.decorator.AqlQueryDecoratorContext;
import org.artifactory.storage.db.aql.service.decorator.ArtifactRetentionColdSearchDecorator;
import org.artifactory.storage.db.aql.service.decorator.DefaultSortDecorator;
import org.artifactory.storage.db.aql.service.decorator.EvidenceDecorator;
import org.artifactory.storage.db.aql.service.decorator.InTransitRepoDecorator;
import org.artifactory.storage.db.aql.service.decorator.RemoteRepoDecorator;
import org.artifactory.storage.db.aql.service.decorator.SupportBundlesRepoDecorator;
import org.artifactory.storage.db.aql.service.decorator.TrashcanDecorator;
import org.artifactory.storage.db.aql.service.decorator.VirtualRepoCriteriaDecorator;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlApiToAqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OperatorQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ParserToAqlAdapter;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SortDetails;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlQueryBuilder;
import org.artifactory.storage.db.aql.sql.result.AqlComposedResultImpl;
import org.artifactory.storage.db.aql.sql.result.AqlEagerResultImpl;
import org.artifactory.storage.db.aql.stats.AqlQueryStatsManager;
import org.artifactory.storage.db.aql.stats.AqlQueryTemplateBuilder;
import org.artifactory.storage.db.aql.stats.AqlStatsEvent;
import org.jfrog.security.util.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.createWithEmptyResults;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildName;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildNumber;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.buildRepo;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemName;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemPath;
import static org.artifactory.aql.model.AqlPhysicalFieldEnum.itemRepo;
import static org.artifactory.common.ConstantValues.aqlQueriesLimit;
import static org.artifactory.common.ConstantValues.aqlQueriesPendingTimeoutMillis;
import static org.artifactory.common.ConstantValues.aqlQueriesUserLimitPercent;
import static org.artifactory.common.ConstantValues.aqlQueriesWorkersLimit;
import static org.artifactory.common.ConstantValues.aqlQueriesWorkersPendingTimeoutMillis;
import static org.artifactory.common.ConstantValues.aqlStreamedFetchSize;
import static org.artifactory.request.RequestThreadLocal.isUnlimitedUserAgent;
import static org.artifactory.request.RequestThreadLocal.scanFactor;
import static org.artifactory.storage.db.util.ArtifactoryDbUtils.isSupportsStreaming;

/**
 * Execute the Aql queries by processing the three Aql steps one after the other:
 * Step 1 Convert the AqlApi or the parser result into AqlQuery.
 * Step 2 Convert the AqlQuery into SqlQuery.
 * Step 3 Execute the SqlQuery and return the results.
 *
 * @author Gidi Shabat
 */
@Slf4j
@Service
public class AqlServiceImpl implements AqlService {
    private final AqlPermissionProvider permissionProvider = new AqlPermissionProviderImpl();
    private final AqlRepoProvider repoProvider = new AqlRepoProviderImpl();
    @Autowired
    private AqlDao aqlDao;
    @Autowired
    private ArtifactoryDbProperties storageProperties;
    @Autowired
    private AqlQueryStreamService streamService;
    @Autowired
    private AqlStatsCollectionService aqlStatsCollectionService;
    @Autowired
    private AqlQueryStatsManager aqlQueryStatsManager;
    @Autowired
    private SecurityService securityService;
    @Autowired
    @Qualifier("applicationMeterRegistry")
    private MeterRegistry metrics;

    private AqlParser parser;
    private ParserToAqlAdapter parserToAqlAdapter;
    private AqlApiToAqlAdapter aqlApiToAqlAdapter;
    private SqlQueryBuilder sqlQueryBuilder;
    private AqlQueryOptimizer optimizer;
    private AqlQueryValidator validator;
    private AqlQueryDecorator externalDecorator; //Decorator used for user queries
    private AqlQueryDecorator internalDecorator; //Decorator used for internal api queries
    private List<Pattern> userAgentPatterns;
    private final EnumSet<AqlPhysicalFieldEnum> requiredAqlItemsEnums = EnumSet.of(itemRepo, itemPath, itemName);
    private final EnumSet<AqlPhysicalFieldEnum> requiredAqlBuildsEnums = EnumSet.of(buildName, buildNumber, buildRepo);
    private final LargestResultSetSize largestResultSetSize =
            new LargestResultSetSize(0, 0);
    private final UserGlobalSemaphoresManager regularSemaphoreManager = new UserGlobalSemaphoresManager(
            aqlQueriesLimit.getInt(), aqlQueriesUserLimitPercent.getInt(), aqlQueriesPendingTimeoutMillis.getInt(),
            "Search-AQL limiter");
    // All workers use the same username, therefore we bypass the per-user limit for workers by setting
    // userPercentageOfGlobal to 0
    private final UserGlobalSemaphoresManager workersSemaphoreManager = new UserGlobalSemaphoresManager(
            aqlQueriesWorkersLimit.getInt(), 0, aqlQueriesWorkersPendingTimeoutMillis.getInt(),
            "AQL-Workers limiter");

    @PostConstruct
    private void init() {
        initDb();
    }

    private void initDb() {
        // The parser is constructed by many internal elements therefore we create it once and then reuse it.
        // Please note that it doesn't really have state therefore we can use it simultaneously
        // TODO init the parser eagerly here not lazy
        parser = new AqlParser();
        parserToAqlAdapter = new ParserToAqlAdapter();
        sqlQueryBuilder = new SqlQueryBuilder();
        aqlApiToAqlAdapter = new AqlApiToAqlAdapter();
        optimizer = new AqlQueryOptimizer(storageProperties.getDbType());
        validator = new AqlQueryValidator();
        externalDecorator = new AqlQueryDecorator(
                new DefaultSortDecorator(),
                new TrashcanDecorator(),
                new SupportBundlesRepoDecorator(),
                new RemoteRepoDecorator(),
                new VirtualRepoCriteriaDecorator(),
                new ArtifactRetentionColdSearchDecorator(),
                new InTransitRepoDecorator(),
                new EvidenceDecorator()
        );

        internalDecorator = new AqlQueryDecorator(
                new TrashcanDecorator(),
                new SupportBundlesRepoDecorator(),
                new VirtualRepoCriteriaDecorator(),
                new EvidenceDecorator()
        );
    }

    @Scheduled(fixedRateString = "#{${artifactory.aql.largest.result.set.size.log.interval.minutes} * 60 * 1000}")
    public void scheduledLogLargestResultSetSize() {
        logLargestResultSetSize();
    }

    /**
     * On the 429 path the cache is empty (execution was rejected before parsing), so this
     * method parses the query to populate it. On the success path the cache is already set
     * by {@link #cacheQueryTemplateIfEligible} and this is a cheap read.
     */
    @Override
    public AqlQueryTemplate computeQueryTemplate(String query) {
        if (shouldSkipAqlStats(query)) {
            return null;
        }
        AqlQueryTemplate cached = RequestThreadLocal.getRequestAttribute(
                AqlQueryTemplate.REQ_ATTR_KEY, AqlQueryTemplate.class);
        if (cached != null) {
            return cached;
        }
        try {
            ParserElementResultContainer parserResult = parser.parse(query);
            AqlQuery<AqlRowResult> aqlQuery = parserToAqlAdapter.toAqlModel(parserResult);
            AqlQueryTemplate template = AqlQueryTemplateBuilder.build(aqlQuery);
            if (template != null) {
                RequestThreadLocal.setRequestAttribute(AqlQueryTemplate.REQ_ATTR_KEY, template);
            }
            return template;
        } catch (Exception e) {
            log.warn("Failed to compute AQL query template; skipping statistics for this request", e);
            return null;
        }
    }

    /**
     * Builds and caches the template using the already-converted {@code aqlQuery} -- no extra parse. Idempotent.
     */
    @VisibleForTesting
    <T extends AqlRowResult> void cacheQueryTemplateIfEligible(AqlQuery<T> aqlQuery) {
        if (shouldSkipAqlStats() ||
                RequestThreadLocal.getRequestAttribute(AqlQueryTemplate.REQ_ATTR_KEY, AqlQueryTemplate.class) != null) {
            return;
        }
        try {
            AqlQueryTemplate template = AqlQueryTemplateBuilder.build(aqlQuery);
            if (template != null) {
                RequestThreadLocal.setRequestAttribute(AqlQueryTemplate.REQ_ATTR_KEY, template);
            }
        } catch (Exception e) {
            log.warn("Failed to compute AQL query template; skipping statistics for this request", e);
        }
    }

    private boolean shouldSkipAqlStats() {
        return !ConstantValues.aqlStatisticsCollectionEnabled.getBoolean()
                || isUnlimitedUserAgent(RequestThreadLocal.getUserAgent());
    }

    private boolean shouldSkipAqlStats(String query) {
        return query == null || query.isBlank() || shouldSkipAqlStats();
    }

    /**
     * Converts the Json query into SQL query and executes the query eagerly
     */
    @Override
    public AqlEagerResult executeQueryEager(String query) {
        log.debug("Processing textual AqlApi query: {}", query);
        ParserElementResultContainer parserResult = parser.parse(query);
        return executeQueryEager(parserResult, null);
    }

    @Override
    public AqlEagerResult<AqlRowResult> executeQueryEager(String query,
            @Nonnull AqlOverloadSettings aqlOverloadSettings) {
        log.debug("Processing textual AqlApi query: {}", query);
        ParserElementResultContainer parserResult = parser.parse(query);
        return executeQueryEager(parserResult, aqlOverloadSettings);
    }

    /**
     * Converts the Json query into SQL query and executes the query lazy
     */
    @Override
    public AqlRestResult executeQueryLazy(String query) {
        log.debug("Processing textual AqlApi query: {}", query);
        return executeQueryLazy(query, null);
    }

    @Override
    public AqlRestResult executeQueryLazyWithLimit(String query) {
        try (UserGlobalSemaphoresManager.PermitsHolder permits = tryAcquirePermits(query)) {
            AqlOverloadSettings aqlOverloadSettings = prepareAqlOverloadSettings();
            ParsedAqlQuery<AqlRowResult> parsed = parseAndBuildAqlQuery(query);
            cacheQueryTemplateIfEligible(parsed.aqlQuery());
            return optimizeAndStreamLazy(parsed.aqlQuery(), aqlOverloadSettings, parsed.aqlLimitByConfiguration());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AQL Query execution interrupted while acquiring semaphore");
        }
    }

    private UserGlobalSemaphoresManager.PermitsHolder tryAcquirePermits(String query) throws InterruptedException {
        boolean searchQueryLimited = isSearchQueryLimited();
        boolean isWorker = isWorker();
        String username = securityService.currentUsername();
        UserGlobalSemaphoresManager.PermitsHolder permits;

        if (searchQueryLimited && !SecurityService.USER_SYSTEM.equals(username)) {
            UserGlobalSemaphoresManager semaphoreManager = isWorker ? workersSemaphoreManager : regularSemaphoreManager;

            permits = semaphoreManager.tryAcquire(username);

            if (permits == null) {
                log.debug("Didn't acquire semaphore. Too-Many-Requests response will be sent. " +
                        "The query that was not executed: {}", query);
                throw new AqlTooManyRequestsException();
            }
            return permits;
        }
        return null;
    }

    /**
     *
     * @return AqlOverloadSettings with aqlStreamedFetchSize
     * in case a header "stream" is passed in the payload of the search/aql request
     * or in case the global property aql.search.stream is set to true.
     * Else, return null.
     */
    @VisibleForTesting
    AqlOverloadSettings prepareAqlOverloadSettings() {
        return Boolean.TRUE.equals(RequestThreadLocal.shouldStream())
                || ConstantValues.aqlSearchStream.getBoolean() ?
                AqlOverloadSettings.builder()
                        .fetchSize(aqlStreamedFetchSize.getInt())
                        .build()
                : null;
    }

    @Override
    public AqlRestResult executeQueryLazy(String query, AqlOverloadSettings aqlOverloadSettings) {
        ParsedAqlQuery<AqlRowResult> parsed = parseAndBuildAqlQuery(query);
        return optimizeAndStreamLazy(parsed.aqlQuery(), aqlOverloadSettings, parsed.aqlLimitByConfiguration());
    }

    private <T extends AqlRowResult> ParsedAqlQuery<T> parseAndBuildAqlQuery(String query) {
        ParserElementResultContainer parserResult = parser.parse(query);
        boolean aqlLimitByConfiguration = optionallyApplyAqlLimit(parserResult);
        AqlQuery<T> aqlQuery = parserToAqlAdapter.toAqlModel(parserResult);
        return new ParsedAqlQuery<>(aqlQuery, aqlLimitByConfiguration);
    }

    private record ParsedAqlQuery<T extends AqlRowResult>(AqlQuery<T> aqlQuery, boolean aqlLimitByConfiguration) {
    }

    /**
     * Converts the Json query into SQL query and executes the query lazy
     */
    @Override
    public <T extends AqlRowResult> AqlComposedResult executeQueryLazy(String originalMainQuery,
            AqlOperatorEnum operatorEnum,
            AqlBase<? extends AqlBase, T> extensionQuery) {
        log.debug("Processing textual AqlApi query {} and API AqlApi", originalMainQuery);
        ParserElementResultContainer parserResult = parser.parse(originalMainQuery);
        AqlQuery<AqlRowResult> originalMainAqlQuery = parserToAqlAdapter.toAqlModel(parserResult);
        AqlQuery<AqlRowResult> extensionAqlQuery = aqlApiToAqlAdapter.toAqlModel(extensionQuery);
        log.debug("Attempting to merge original query with the extension query");
        AqlQuery<AqlRowResult> mergedAqlQuery = merge(originalMainAqlQuery, extensionAqlQuery, operatorEnum);
        optimizer.optimize(mergedAqlQuery);
        validator.validate(mergedAqlQuery, permissionProvider);
        externalDecorator.decorate(mergedAqlQuery, new AqlQueryDecoratorContext(repoProvider, permissionProvider));
        log.trace("Successfully finished to convert the parser result into AqlApi query");
        return new AqlComposedResultImpl(streamService.getAqlQueryStreamResult(mergedAqlQuery,
                streamService.getQueryTimeout()),
                originalMainAqlQuery,
                extensionAqlQuery, mergedAqlQuery);
    }

    public <T extends AqlRowResult> AqlEagerResult<T> executeQueryEager(AqlBase<? extends AqlBase, T> aql,
            AqlOverloadSettings aqlOverloadSettings) {
        AqlQuery<T> aqlQuery = aqlApiToAqlAdapter.toAqlModel(aql);

        applySettingsOnAqlQuery(aqlOverloadSettings, aqlQuery);

        optimizer.optimize(aqlQuery);
        validator.validate(aqlQuery, permissionProvider);
        externalDecorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, permissionProvider));
        return getAqlQueryResult(aqlQuery);
    }

    @Override
    public Response streamResponse(boolean compact, AqlRestResult restResult) {
        return streamResponse(compact, restResult, null);
    }

    @Override
    public Response streamResponse(boolean compact, AqlRestResult restResult, AqlTrackingInfo aqlTrackingInfo) {
        try {
            long startNanos = System.nanoTime();
            StreamingOutput stream = os -> {
                byte[] array = read(compact, restResult);
                try {
                    while (array != null) {
                        os.write(array);
                        array = read(compact, restResult);
                    }
                    os.flush();
                    if (aqlTrackingInfo != null) {
                        monitorResultSetSize(restResult, aqlTrackingInfo);
                        recordAqlStatistics(restResult, aqlTrackingInfo, startNanos);
                    }
                } finally {
                    IOUtils.closeQuietly(restResult, null);
                }
            };
            return Response.ok(stream).build();
        } catch (Exception e) {
            log.error("An error has occurred while streaming the AQL response: {}", e.getMessage());
            log.debug("An error has occurred while streaming the AQL response: ", e);
            throw new AqlException("An error has occurred while streaming the AQL response ", e);
        }
    }

    /**
     * Enqueue a stats event when: origin is {@code search/aql}, user-agent is external, and a
     * template was computed earlier. Queries that fail before reaching this point are already
     * excluded by control flow (this is only called after a successful {@code os.flush()}).
     * Successful empty result sets ARE recorded -- a 10s scan that returns 0 rows is exactly
     * the kind of query the report should surface.
     */
    private void recordAqlStatistics(AqlRestResult restResult, AqlTrackingInfo aqlTrackingInfo, long startNanos) {
        if (aqlTrackingInfo == null || !AqlTrackingInfo.SEARCH_AQL_ORIGIN.equals(aqlTrackingInfo.origin())) {
            return;
        }
        AqlQueryTemplate template = RequestThreadLocal.getRequestAttribute(
                AqlQueryTemplate.REQ_ATTR_KEY, AqlQueryTemplate.class);
        if (template == null) {
            return;
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        aqlQueryStatsManager.offer(new AqlStatsEvent(
                template.checksum(),
                template.template(),
                aqlTrackingInfo.query(),
                durationMs,
                Math.max(0, restResult.getRowsCount())));
    }

    private void monitorResultSetSize(AqlRestResult restResult, AqlTrackingInfo aqlTrackingInfo) {
        long rowsCount = restResult.getRowsCount();
        DistributionSummary.builder("search.aql.result.set.size")
                .description("Size of a result set for a search aql statement")
                .tag("origin", aqlTrackingInfo.origin())
                .tag("query_repo_type", restResult.getAqlQueryContext().queryRepoType().getName())
                .tag("query_domain", restResult.getAqlQueryContext().domain().signature)
                .tag("unlimited_agent", prepareUnlimitedUserAgentTag())
                .register(this.metrics)
                .record(rowsCount);
        if (largestResultSetSize.isNewMaxSize(rowsCount)) {
            log.info(String.format("Larger AQL was added at %s " +
                            "by the user %s " +
                            "with a size of %s records, " +
                            "queried by the following aql query: %s",
                    new Date(largestResultSetSize.getTimestamp()),
                    RequestThreadLocal.getUserAgent().trim(),
                    rowsCount,
                    aqlTrackingInfo.query()));
        }
    }

    private String prepareUnlimitedUserAgentTag() {
        String user = RequestThreadLocal.getUserAgent().trim();
        return isUnlimitedUserAgent(user) ? user : "limited-user-agent";
    }

    protected byte[] read(boolean compact, AqlRestResult restResult) {
        return compact ? restResult.readCompact() : restResult.read();
    }

    /**
     * Converts the API's AqlApi query into SQL query and executes the query eagerly
     */
    @Override
    public <T extends AqlRowResult> AqlEagerResult<T> executeQueryEager(AqlBase<? extends AqlBase, T> aql) {
        AqlQuery<T> aqlQuery = aqlApiToAqlAdapter.toAqlModel(aql);
        optimizer.optimize(aqlQuery);
        internalDecorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, permissionProvider));
        return getAqlQueryResult(aqlQuery);
    }

    @Override
    public <T extends AqlRowResult> AqlLazyResult<T> executeQueryLazy(AqlBase<? extends AqlBase, T> aql) {
        return executeQueryLazy(aql, (Long) null);
    }

    @Override
    public <T extends AqlRowResult> AqlLazyResult<T> executeQueryLazy(AqlBase<? extends AqlBase, T> aql, Long timeout) {
        log.debug("Processing API AqlApi query with timeout {}", timeout);
        AqlQuery<T> aqlQuery = aqlApiToAqlAdapter.toAqlModel(aql);
        optimizer.optimize(aqlQuery);
        internalDecorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, permissionProvider));
        return streamService.getAqlQueryStreamResult(aqlQuery, timeout);
    }

    @Override
    public <T extends AqlRowResult> AqlRestResult executeQueryLazy(AqlBase<? extends AqlBase, T> aql,
            AqlOverloadSettings settings) {
        log.debug("Processing API AqlApi query");
        AqlQuery<T> aqlQuery = aqlApiToAqlAdapter.toAqlModel(aql);
        optimizer.optimize(aqlQuery);
        internalDecorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, permissionProvider));
        if (Objects.nonNull(settings) && settings.getFetchSize() != 0 &&
                isSupportsStreaming(storageProperties.getDbType())) {
            return streamService.getStreamedDbResponse(aqlQuery, settings.getFetchSize());
        }

        return streamService.getLocalStreamerWithWrapper(aqlQuery);
    }

    /**
     * Converts the parser elements into AqlApi query, convert the AqlApi query to sql and executes the query eagerly
     */
    private <T extends AqlRowResult> AqlEagerResult<T> executeQueryEager(ParserElementResultContainer parserResult,
            AqlOverloadSettings aqlOverloadSettings) {
        log.trace("Converting the parser result into AqlApi query");
        AqlQuery<T> aqlQuery = parserToAqlAdapter.toAqlModel(parserResult);

        applySettingsOnAqlQuery(aqlOverloadSettings, aqlQuery);

        optimizer.optimize(aqlQuery);
        validator.validate(aqlQuery, permissionProvider);
        externalDecorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, permissionProvider,
                aqlOverloadSettings));
        log.trace("Successfully finished to convert the parser result into AqlApi query");
        return getAqlQueryResult(aqlQuery);
    }

    private <T extends AqlRowResult> void applySettingsOnAqlQuery(AqlOverloadSettings aqlOverloadSettings,
            AqlQuery<T> aqlQuery) {
        if (Objects.isNull(aqlOverloadSettings)) {
            return;
        }

        Long newLimit = aqlOverloadSettings.getLimit();
        if (Objects.nonNull(newLimit)) {
            aqlQuery.setLimit(newLimit);
        }

        SortDetails newSortDetails = getSortDetails(aqlOverloadSettings);
        if (Objects.nonNull(newSortDetails)) {
            aqlQuery.setSort(newSortDetails);
        }

        List<AqlApiElement<AqlApiItem>> queryElementsExtension = aqlOverloadSettings.getQueryElementsExtension();
        AqlOperatorEnum operatorEnum = aqlOverloadSettings.getQueryElementsExtensionOperatorEnum();
        if (!CollectionUtils.isEmpty(queryElementsExtension) && Objects.nonNull(operatorEnum)) {
            AqlApiItem extensionQuery =
                    createWithEmptyResults().filter(and(queryElementsExtension.toArray(AqlApiElement[]::new)));
            AqlQuery<AqlRowResult> extensionAqlQuery = aqlApiToAqlAdapter.toAqlModel(extensionQuery);
            aqlQuery.getAqlElements().add(new OperatorQueryElement(operatorEnum));
            aqlQuery.getAqlElements().add(AqlAdapter.open);
            aqlQuery.getAqlElements().addAll(extensionAqlQuery.getAqlElements());
            aqlQuery.getAqlElements().add(AqlAdapter.close);
        }

        if (aqlOverloadSettings.isClearExistingResultFields()) {
            aqlQuery.getResultFields().clear();
        }

        List<DomainSensitiveField> newResultFields = aqlOverloadSettings.getResultFields();
        if (!CollectionUtils.isEmpty(newResultFields)) {
            aqlQuery.getResultFields().addAll(newResultFields.stream()
                    .filter(field -> !aqlQuery.getResultFields().contains(field))
                    .collect(Collectors.toList()));
        }

        AqlSqlFunction sqlFunction = aqlOverloadSettings.getSqlFunction();
        if (Objects.nonNull(sqlFunction)) {
            aqlQuery.setSqlFunction(sqlFunction);
        }
    }

    /**
     * Template caching is intentionally not done here — only {@link #executeQueryLazyWithLimit(String)} performs it.
     */
    @VisibleForTesting
    protected <T extends AqlRowResult> AqlRestResult optimizeAndStreamLazy(AqlQuery<T> aqlQuery,
            AqlOverloadSettings aqlOverloadSettings, boolean aqlLimitByConfiguration) {
        optimizer.optimize(aqlQuery);
        validator.validate(aqlQuery, permissionProvider);
        addRequiredFieldsToAqlQueryForAdmin(aqlQuery);
        externalDecorator.decorate(aqlQuery, new AqlQueryDecoratorContext(repoProvider, permissionProvider,
                aqlOverloadSettings));
        log.trace("Successfully finished to convert the parser result into AqlApi query");
        return chooseStreamer(aqlQuery, aqlOverloadSettings, aqlLimitByConfiguration);
    }

    @VisibleForTesting
    <T extends AqlRowResult> void addRequiredFieldsToAqlQueryForAdmin(AqlQuery<T> aqlQuery) {
        if (permissionProvider.isAdmin()) {
            List<DomainSensitiveField> resultFields = aqlQuery.getResultFields();
            EnumSet<AqlPhysicalFieldEnum> resultPhysicalFields = resultFields.stream()
                    .filter(d -> d.getField() instanceof AqlPhysicalFieldEnum)
                    .map(d -> (AqlPhysicalFieldEnum) d.getField())
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(AqlPhysicalFieldEnum.class)));
            if (AqlDomainEnum.items == aqlQuery.getDomain()) {
                requiredAqlItemsEnums.forEach(field -> {
                    if (!resultPhysicalFields.contains(field)) {
                        resultFields.add(new DomainSensitiveField(field, List.of(AqlDomainEnum.items), true));
                    }
                });
            }
            if (AqlDomainEnum.builds == aqlQuery.getDomain()) {
                requiredAqlBuildsEnums.forEach(field -> {
                    if (!resultPhysicalFields.contains(field)) {
                        resultFields.add(new DomainSensitiveField(field, List.of(AqlDomainEnum.builds), true));
                    }
                });
            }
        }
    }

    @VisibleForTesting
    <T extends AqlRowResult> AqlRestResult chooseStreamer(AqlQuery<T> aqlQuery,
            AqlOverloadSettings aqlOverloadSettings,
            boolean aqlLimitByConfiguration) {
        AqlQueryContext context = createContext(aqlQuery, aqlLimitByConfiguration);
        if (Objects.nonNull(aqlOverloadSettings)) {
            if (Objects.nonNull(aqlOverloadSettings.getStreamFunc())) {
                return streamService.getMultiStreamerWithStreamersFunctions(aqlQuery, aqlOverloadSettings);
            }

            if (aqlOverloadSettings.getFetchSize() != 0
                    && isSupportsStreaming(storageProperties.getDbType())) {
                enlargeLimitIfNeeded(aqlQuery);
                return streamService.getStreamedDbResponse(aqlQuery, aqlOverloadSettings.getFetchSize(), context);
            }
        }
        if (enlargeLimitIfNeeded(aqlQuery)) {
            return streamService.getStreamedDbResponse(aqlQuery, aqlStreamedFetchSize.getInt(), context);
        }

        switch (aqlQuery.getQueryRepoType()) {
            case remote -> {
                AqlRestResult transitiveRemoteStreamer = streamService.getTransitiveRemoteStreamer(aqlQuery);
                aqlStatsCollectionService.collectRemoteSearchStats(transitiveRemoteStreamer);
                return transitiveRemoteStreamer;
            }
            case virtual -> {
                AqlRestResult transitiveVirtualStreamer = streamService.getTransitiveVirtualStreamer(aqlQuery);
                aqlStatsCollectionService.collectRemoteSearchStats(transitiveVirtualStreamer);
                return transitiveVirtualStreamer;
            }
            default -> {
                return streamService.getLocalStreamerWithWrapper(aqlQuery, aqlLimitByConfiguration);
            }
        }
    }

    private AqlQueryContext createContext(AqlQuery<?> aqlQuery, boolean aqlLimitByConfiguration) {
        return new AqlQueryContext(aqlQuery.getQueryRepoType(), aqlQuery.getDomain(),
                aqlLimitByConfiguration, aqlQuery.getLimit());
    }

    private <T extends AqlRowResult> boolean shouldLimitResultsDuringStreaming(AqlQuery<T> aqlQuery) {
        long limit = aqlQuery.getLimit();
        boolean hasLimitInsideQuery = limit > 0
                && limit != Long.MAX_VALUE
                && (ConstantValues.aqlSearchQueryMaxLimit.getInt() <= 0 ||
                limit < ConstantValues.aqlSearchQueryMaxLimit.getInt());
        return hasLimitInsideQuery
                && isSupportsStreaming(storageProperties.getDbType());
    }

    private <T extends AqlRowResult> boolean enlargeLimitIfNeeded(AqlQuery<T> aqlQuery) {
        int scanFactor = scanFactor();
        if (shouldLimitResultsDuringStreaming(aqlQuery) && scanFactor > 1) {
            aqlQuery.enlargeLimit(scanFactor);
            return true;
        }
        return false;
    }

    /**
     * Converts the AqlApi query into SQL query and executes the query eagerly
     */
    private <T extends AqlRowResult> AqlEagerResult<T> getAqlQueryResult(AqlQuery<T> aqlQuery) {
        log.trace("Converting the AqlApi query into SQL query: {}", aqlQuery);
        SqlQuery<T> sqlQuery = sqlQueryBuilder.buildQuery(aqlQuery);
        log.trace("processing the following SQL query: {}", sqlQuery);
        AqlEagerResultImpl<T> aqlQueryResult = aqlDao.executeQueryEager(sqlQuery, repoProvider);
        log.debug("Successfully finished to process SQL query with the following size: {}", aqlQueryResult.getSize());
        return aqlQueryResult;
    }

    private AqlQuery<AqlRowResult> merge(AqlQuery<AqlRowResult> originalMainQueryAqlQuery,
            AqlQuery<AqlRowResult> extensionAqlQuery, AqlOperatorEnum operatorEnum) {
        AqlQuery<AqlRowResult> merge = new AqlQuery<>();
        merge.setAction(originalMainQueryAqlQuery.getAction());
        merge.setDomain(originalMainQueryAqlQuery.getDomain());
        merge.setLimit(extensionAqlQuery.getLimit() != Long.MAX_VALUE ? extensionAqlQuery.getLimit() :
                originalMainQueryAqlQuery.getLimit());
        merge.setOffset(extensionAqlQuery.getOffset() != 0 ? extensionAqlQuery.getOffset() :
                originalMainQueryAqlQuery.getOffset());
        merge.setSort(extensionAqlQuery.getSort() != null ? extensionAqlQuery.getSort() :
                originalMainQueryAqlQuery.getSort());
        Set<DomainSensitiveField> mergedFields = Sets.newHashSet();
        mergedFields.addAll(originalMainQueryAqlQuery.getResultFields());
        mergedFields.addAll(extensionAqlQuery.getResultFields());
        merge.getResultFields().addAll(mergedFields);
        if (extensionAqlQuery.getAqlElements().isEmpty()) {
            merge.getAqlElements().addAll(originalMainQueryAqlQuery.getAqlElements());
        } else {
            merge.getAqlElements().add(AqlAdapter.open);
            merge.getAqlElements().addAll(originalMainQueryAqlQuery.getAqlElements());
            merge.getAqlElements().add(AqlAdapter.close);
            merge.getAqlElements().add(getOperator(operatorEnum));
            merge.getAqlElements().add(AqlAdapter.open);
            merge.getAqlElements().addAll(extensionAqlQuery.getAqlElements());
            merge.getAqlElements().add(AqlAdapter.close);
        }
        return merge;
    }

    /*
      Apply default limit on AQL query if there is no limit on query, or limit greater than system default
     */
    @VisibleForTesting
    boolean optionallyApplyAqlLimit(final ParserElementResultContainer parserResult) {
        final int aqlMaxLimit = ConstantValues.aqlSearchQueryMaxLimit.getInt();
        //System default aql limit will be applied only for requests from api
        if (aqlMaxLimit <= 0 || !RequestThreadLocal.getRequestPath().contains("search/aql")) {
            return false;
        }
        if (isUnlimitedUserAgent(RequestThreadLocal.getUserAgent().trim())) {
            return false;
        }
        /*
        Trying to find limit element in query and check if limit is greater than system default limitation
        then change it to system default, in case if there is no limit in request applying default to avoid huge result
        and OOM.
         */
        final Optional<Pair<ParserElement, String>> found = parserResult.getAll().stream()
                .filter(it -> it.getFirst() instanceof LimitValueElement)
                .findFirst();
        final String aqlMaxLimitToSet = ConstantValues.aqlSearchVerifyMergedEntities.getBoolean() ?
                String.valueOf(aqlMaxLimit + 1) :
                String.valueOf(aqlMaxLimit);
        if (found.isPresent()) {
            int requestLimit = Integer.parseInt(found.get().getSecond());
            if (requestLimit <= 0 || requestLimit > aqlMaxLimit) {
                parserResult.add(new LimitValueElement(), aqlMaxLimitToSet);
                return true;
            }
        } else {
            parserResult.add(new LimitValueElement(), aqlMaxLimitToSet);
            return true;
        }
        return false;
    }

    private AqlQueryElement getOperator(AqlOperatorEnum operatorEnum) {
        switch (operatorEnum) {
            case or:
                return AqlAdapter.or;
            case and:
                return AqlAdapter.and;
            default:
                throw new UnsupportedOperationException("Aql merger support only And / or operations");
        }
    }

    @Nullable
    private SortDetails getSortDetails(AqlOverloadSettings aqlOverloadSettings) {
        if (Objects.isNull(aqlOverloadSettings.getSortField()) || Objects.isNull(aqlOverloadSettings.getSortType())) {
            return null;
        }
        SortDetails sortDetails = new SortDetails();
        sortDetails.setSortType(aqlOverloadSettings.getSortType());
        sortDetails.addField(aqlOverloadSettings.getSortField());
        return sortDetails;
    }

    protected boolean isWorker() {
        return RequestThreadLocal.getUserAgent().trim().equalsIgnoreCase("jfrog-worker");
    }


    /**
     * @return whether we'll use the semaphore for limiting the number of search calls, given 3 conditions:
     * 1. The feature flag aqlSearchCallsLimitEnable is ON
     * 2. The platform runs on cloud and is not self-hosted
     * 3. The current user agent is not set to have unlimited search calls
     */
    private boolean isSearchQueryLimited() {
        return ConstantValues.aqlQueriesLimitEnabled.getBoolean()
                && !isUnlimitedUserAgent(RequestThreadLocal.getUserAgent().trim());
    }

    private void logLargestResultSetSize() {
        if (largestResultSetSize.getSize() > 0) {
            log.info("Current largest result set size: {} , executed at {}",
                    largestResultSetSize.getSize(), new Date(largestResultSetSize.getTimestamp()));
        }
    }

    @Getter
    protected static class LargestResultSetSize {
        private long size;
        private long timestamp;

        public LargestResultSetSize(long size, long timestamp) {
            this.size = size;
            this.timestamp = timestamp;
        }

        public boolean isNewMaxSize(long newSize) {
            if (newSize > size) {
                size = newSize;
                timestamp = System.currentTimeMillis();
                return true;
            }
            return false;
        }

    }
}