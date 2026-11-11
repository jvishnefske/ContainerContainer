package org.artifactory.storage.db.aql.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import org.apache.commons.io.IOUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.aql.AqlResultRuntimeException;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlOverloadSettings;
import org.artifactory.aql.model.AqlPermissionProvider;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlQueryContext;
import org.artifactory.aql.model.AqlQueryRepoType;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.model.AqlVariable;
import org.artifactory.aql.result.AqlJsonLocalStreamer;
import org.artifactory.aql.result.AqlJsonMultiStreamer;
import org.artifactory.aql.result.AqlJsonRemoteStreamer;
import org.artifactory.aql.result.AqlJsonStreamer;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.aql.dao.AqlDao;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.TransitiveMetadata;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Service
public class AqlQueryStreamService {
    private static final Logger log = LoggerFactory.getLogger(AqlQueryStreamService.class);
    private final SqlQueryBuilder sqlQueryBuilder;
    private final AqlPermissionProvider permissionProvider;
    private final AqlRepoProvider repoProvider;
    private final ArtifactoryContext artifactoryContext;
    private final AqlDao aqlDao;

    @Autowired
    public AqlQueryStreamService(AqlDao aqlDao) {
        this.aqlDao = aqlDao;
        this.sqlQueryBuilder = new SqlQueryBuilder();
        this.permissionProvider = new AqlPermissionProviderImpl();
        this.repoProvider = new AqlRepoProviderImpl();
        this.artifactoryContext = ContextHelper.get();
    }

    public <T extends AqlRowResult> AqlLazyResult<T> getAqlQueryStreamResult(AqlQuery<T> aqlQuery, Long queryTimeout) {
        log.trace("Converting the AqlApi query into SQL query: {}", aqlQuery);
        SqlQuery<T> sqlQuery = sqlQueryBuilder.buildQuery(aqlQuery);
        log.trace("processing the following SQL query: {}", sqlQuery);
        AqlLazyResult<T> aqlQueryStreamResult =
                aqlDao.executeQueryLazy(sqlQuery, permissionProvider, repoProvider, queryTimeout);
        log.debug("Successfully finished to process SQL query (lazy)");
        return aqlQueryStreamResult;
    }

    public <T extends AqlRowResult> AqlRestResult getLocalStreamerWithWrapper(AqlQuery<T> aqlQuery) {
        return getLocalStreamerWithWrapper(aqlQuery, false);
    }

    public <T extends AqlRowResult> AqlRestResult getLocalStreamerWithWrapper(AqlQuery<T> aqlQuery,
            boolean aqlLimitByConfiguration) {
        AqlQueryContext context = createContext(aqlQuery);
        return new AqlJsonLocalStreamer(
                this.getAqlQueryStreamResult(aqlQuery, getQueryTimeout()),
                context, aqlLimitByConfiguration
        );
    }

    private <T extends AqlRowResult> AqlJsonStreamer getLocalStreamerWithoutWrapper(AqlQuery<T> aqlQuery) {
        return new AqlJsonLocalStreamer(this.getAqlQueryStreamResult(aqlQuery, getQueryTimeout()),
                true,
                createContext(aqlQuery));
    }

    public <T extends AqlRowResult> AqlRestResult getStreamedDbResponse(AqlQuery<T> aqlQuery, int fetchSize) {
        return getStreamedDbResponse(aqlQuery, fetchSize, createContext(aqlQuery));
    }

    public <T extends AqlRowResult> AqlRestResult getStreamedDbResponse(AqlQuery<T> aqlQuery,
            int fetchSize,
            AqlQueryContext context) {
        return new AqlJsonLocalStreamer(
                this.getAqlQueryStreamedDbResponse(aqlQuery, fetchSize),
                context,
                context.aqlLimitByConfiguration()
        );
    }

    private AqlQueryContext createContext(AqlQuery<?> aqlQuery) {
        return new AqlQueryContext(aqlQuery.getQueryRepoType(), aqlQuery.getDomain(),
                false, aqlQuery.getLimit());
    }

    @VisibleForTesting
    <T extends AqlRowResult> AqlLazyResult<T> getAqlQueryStreamedDbResponse(AqlQuery<T> aqlQuery,
            int fetchSize) {
        log.trace("Converting the AqlApi query into SQL query: {}", aqlQuery);
        SqlQuery<T> sqlQuery = sqlQueryBuilder.buildQuery(aqlQuery);
        log.debug("processing the following SQL query: {}", sqlQuery);
        AqlLazyResult<T> aqlQueryStreamResult = aqlDao.executeStreamedResponse(sqlQuery, permissionProvider,
                repoProvider, ConstantValues.aqlStreamedQueryTimeout.getLong(), fetchSize);
        log.debug("Successfully finished to process SQL query (lazy)");
        return aqlQueryStreamResult;
    }

    public <T extends AqlRowResult> AqlRestResult getMultiStreamerWithStreamersFunctions(AqlQuery<T> aqlQuery,
            AqlOverloadSettings aqlOverloadSettings) {
        log.trace("Starting remote transitive process");
        return new AqlJsonMultiStreamer(
                aqlOverloadSettings.getStreamFunc()
                        .apply(aqlQuery.toNative(), this.permissionProvider, this.repoProvider),
                this.permissionProvider, this.repoProvider, aqlQuery.getLimit(), aqlQuery.getOffset(),
                aqlOverloadSettings.getAqlRowMapper(),
                createContext(aqlQuery));
    }

    public <T extends AqlRowResult> AqlRestResult getTransitiveRemoteStreamer(AqlQuery<T> aqlQuery) {
        log.trace("Starting remote transitive process");
        return new AqlJsonMultiStreamer(this.handleTransitiveRemote(aqlQuery),
                this.permissionProvider, this.repoProvider, aqlQuery.getLimit(), aqlQuery.getOffset(),
                createContext(aqlQuery));
    }

    public <T extends AqlRowResult> AqlRestResult getTransitiveVirtualStreamer(AqlQuery<T> aqlQuery) {
        log.trace("Starting virtual transitive process");
        return new AqlJsonMultiStreamer(this.handleTransitiveVirtual(aqlQuery),
                this.permissionProvider, this.repoProvider, aqlQuery.getLimit(), aqlQuery.getOffset(),
                createContext(aqlQuery));
    }


    /**
     * Handles the whole process of getting the AqlJsonStreamers for a remote repo transitive request
     */
    @VisibleForTesting
    <T extends AqlRowResult> List<AqlJsonStreamer> handleTransitiveRemote(AqlQuery<T> aqlQuery) {
        ExecutorService executorService = null;
        List<AqlJsonStreamer> responses;
        try {
            executorService = ArtifactoryExecutorFactory.newFixedThreadPool(2, this.artifactoryContext, false);
            Future<AqlJsonStreamer> remoteResponse = this.getResultForRemote(aqlQuery, executorService,
                    aqlQuery.getTransitiveMetadata().getBaseRepoName());
            log.trace("Successfully created and submitted request for the remote transitive repo");
            Future<AqlJsonStreamer> localResponse = this.getResultForLocalCache(aqlQuery, executorService);
            log.trace("Successfully created and sent request for the local cache for the remote repo");
            responses = getCombinedResponses(localResponse, remoteResponse, aqlQuery);
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
        log.trace("Returning the responses for the transitive remote search");
        return responses;
    }

    @Nonnull
    @VisibleForTesting
    <T extends AqlRowResult> List<AqlJsonStreamer> getCombinedResponses(Future<AqlJsonStreamer> localResponse,
            Future<AqlJsonStreamer> remoteResponse, AqlQuery<T> aqlQuery) {
        List<AqlJsonStreamer> responses = Lists.newArrayList();
        try {
            responses.add(localResponse.get());
            responses.add(remoteResponse.get());
            return responses;
        } catch (InterruptedException | ExecutionException e) {
            // since a part of the response has failed, need to make sure that successful parts are closed to avoid
            // db connection leak
            responses.forEach(streamerResponse -> IOUtils.closeQuietly(streamerResponse, null));
            throw new AqlResultRuntimeException("Transitive AQL query has failed to process, the following query: "
                    + aqlQuery.toNative() + " with error: ", e);
        }
    }

    /**
     * Gets the future object of the AQL query result for the requested remote repo
     */
    private <T extends AqlRowResult> Future<AqlJsonStreamer> getResultForRemote(AqlQuery<T> aqlQuery,
            ExecutorService executorService, String repoKey) {
        log.trace("Creating request for the remote transitive repo");
        TransitiveMetadata details = aqlQuery.getTransitiveMetadata();

        // This will replace the repo name to the actual repo name that exists in the remote
        this.prepareRepoCriterion(aqlQuery.getElementsForNative(),
                AqlQueryRepoType.remote,
                repoKey);

        // Remove the transitive if we reach the end of the chain
        if (details.getTransitiveDepth() <= 0) {
            details.setTransitive(false);
        }

        String aqlString = aqlQuery.toNative();
        log.trace("Creating the callable for remote transitive request with AQL: {}", aqlString);
        Callable<AqlJsonStreamer> request = getAqlJsonStreamerCallable(repoKey, aqlString);

        return executorService.submit(request);
    }

    @VisibleForTesting
    Callable<AqlJsonStreamer> getAqlJsonStreamerCallable(String repoKey, String aqlString) {
        return () -> new AqlJsonRemoteStreamer(this.repoProvider.executeAqlRemotely(aqlString, repoKey),
                this.permissionProvider, this.repoProvider, repoKey);
    }


    /**
     * Gets the future object of the AQL query result for the requested local cache
     */
    private <T extends AqlRowResult> Future<AqlJsonStreamer> getResultForLocalCache(AqlQuery<T> aqlQuery,
            ExecutorService executorService) {
        log.trace("Creating request for the local cache for the remote repo");
        TransitiveMetadata details = aqlQuery.getTransitiveMetadata();

        // This will replace the repo name for the cache name in the local machine
        this.prepareRepoCriterion(aqlQuery.getAqlElements(),
                AqlQueryRepoType.local, details.getBaseRepoName());

        log.trace("Creating the callable for local-cache request");
        Callable<AqlJsonStreamer> request =
                () -> this.getLocalStreamerWithoutWrapper(aqlQuery);

        return executorService.submit(request);
    }

    /**
     * Handles the whole process of getting the AqlJsonStreamers for a virtual repo transitive request
     */
    @VisibleForTesting
    <T extends AqlRowResult> List<AqlJsonStreamer> handleTransitiveVirtual(AqlQuery<T> aqlQuery) {
        ExecutorService executorService = null;
        List<AqlJsonStreamer> responses = null;
        try {
            executorService = ArtifactoryExecutorFactory
                    .newFixedThreadPool(ConstantValues.aqlTransitiveRemoteReposLimit.getInt() + 1,
                            this.artifactoryContext,
                            false);
            log.trace("Retrieving the responses from the remote repos for the requested virtual repo");
            List<Future<AqlJsonStreamer>> remoteResponses =
                    this.getResultsForVirtualRemote(aqlQuery, executorService);
            log.trace("Creating request for the local cache and repos for the virtual repo");
            Future<AqlJsonStreamer> localResponse =
                    executorService.submit(() -> this.getLocalStreamerWithoutWrapper(aqlQuery));
            log.trace("Successfully created and submitted request for the local cache and repos for the virtual repo");
            try {
                responses = new ArrayList<>();
                responses.add(localResponse.get());
                for (Future<AqlJsonStreamer> remoteResponse : remoteResponses) {
                    responses.add(remoteResponse.get());
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Transitive AQL query has failed to process, the following query: {} with error: {}",
                        aqlQuery.toNative(), e.getMessage());
                log.debug("Transitive AQL query has failed to process, the following query: {} with error: ",
                        aqlQuery.toNative(), e);
            }
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
        log.trace("Returning the responses for the transitive search for the virtual repo");
        return responses;
    }

    /**
     * Gets a list of futures of the AQL query result for the requested remote repos
     * that are related to the virtual repo
     */
    private <T extends AqlRowResult> List<Future<AqlJsonStreamer>> getResultsForVirtualRemote(AqlQuery<T> aqlQuery,
            ExecutorService executorService) {
        log.trace("Retrieving the valid remote repos that are related to the virtual repo: {}",
                aqlQuery.getTransitiveMetadata().getBaseRepoName());
        List<String> remoteRepos = this.repoProvider
                .getTransitiveRepoKeysFromVirtual(aqlQuery.getTransitiveMetadata().getBaseRepoName());
        log.trace("Successfully retrieved the smart repos: {}", remoteRepos);
        log.trace("Setting the limit for the remote repos to be queried");
        int limit = Math.min(remoteRepos.size(), ConstantValues.aqlTransitiveRemoteReposLimit.getInt());
        log.trace("Set the limit: {} for the amount of remote repos to be queried", limit);
        List<Future<AqlJsonStreamer>> remoteResponses = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            if (!this.repoProvider.isRemoteRepoOffline(remoteRepos.get(i))) {
                log.trace("The following remote repo has been found as online: {}", remoteRepos.get(i));
                remoteResponses
                        .add(this.getResultForRemote(aqlQuery, executorService, remoteRepos.get(i)));
                log.trace(
                        "Successfully created and submitted request for the remote repo {} belonging to the requested" +
                                " virtual repo",
                        remoteRepos.get(i));
            }
        }

        return remoteResponses;
    }

    /**
     * Changes the query repo parameter to the actual repo that should be searched in
     */
    private void prepareRepoCriterion(List<AqlQueryElement> elements, AqlQueryRepoType queryType, String repoKey) {
        for (AqlQueryElement element : elements) {
            if (element instanceof Criterion) {
                Criterion currCriterion = (Criterion) element;
                AqlVariable variable = currCriterion.getVariable1();
                if (variable instanceof AqlField &&
                        ((AqlField) variable).getFieldEnum().equals(AqlPhysicalFieldEnum.itemRepo)) {
                    log.trace("The original repoKey for the remote query is: {}", repoKey);
                    switch (queryType) {
                        case remote:
                            String actualRepoName = this.repoProvider.remoteRepoActualNameByKey(repoKey);
                            log.trace("The actual repoKey for the remote query is: {}", actualRepoName);
                            ((AqlValue) currCriterion.getVariable2()).setValue(actualRepoName);
                            break;
                        case local:
                            ((AqlValue) currCriterion.getVariable2()).setValue(repoKey + RepoPath.REMOTE_CACHE_SUFFIX);
                            break;
                    }
                }
            }
        }
    }

    public Long getQueryTimeout() {
        Long timeoutSeconds =
                ContextHelper.get().beanForType(AddonsManager.class).getAqlQueryTimeoutByLicense();
        log.debug("AQL JDBC query timeout: {}s (source=AddonsManager.getAqlQueryTimeoutByLicense)", timeoutSeconds);
        return timeoutSeconds;
    }
}
