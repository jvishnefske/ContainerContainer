package org.artifactory.ui.rest.service.artifacts.search.syntax;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.NestedRealRepositoriesWithRoutes;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Item;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Response;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Search;
import org.artifactory.ui.rest.model.artifacts.search.syntax.State;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Type;
import org.artifactory.ui.utils.BrowseUtils;
import org.artifactory.ui.utils.RequestUtils;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.bundles.BundleConfigurationUtil;
import org.jfrog.storage.JdbcHelper;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_PARTIAL_CONTENT;
import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.NestedRealRepositoriesWithRoutes.resolveNestedRepositories;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoPath.REMOTE_CACHE_SUFFIX;
import static org.artifactory.repo.RepoPathFactory.create;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.getSystemRepo;
import static org.artifactory.ui.rest.model.artifacts.search.syntax.State.STOP_STATE;
import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.FILE;
import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.FOLDER;
import static org.artifactory.ui.rest.model.artifacts.search.syntax.Type.REPO;
import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchUtils.getDownloadUrl;
import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchUtils.getFullPath;
import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchUtils.getSearchTypes;
import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchUtils.shouldSearch;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isHiddenReleaseBundlePath;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundleNavigationDisabled;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidenceNavigationDisabled;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidencePath;
import static org.artifactory.utils.PathUtils.removeDashCache;

/**
 * @author Igor Usenko
 */
@Slf4j
@Component
public class SyntaxSearchService implements SyntaxSearchOperations {

    private static final int QUERY_LIMIT = 500;
    private static final String SYNTAX_ERROR = "42601";

    private final JdbcHelper jdbcHelper;
    private final InternalRepositoryService repositoryService;
    private final SecurityService securityService;
    private final AuthorizationService authorizationService;
    private final ProjectsService projectsService;
    private final ItemActionsPopulator itemActionsPopulator;

    private SearchFunctionProvider searchFunctionProvider;

    public SyntaxSearchService(JdbcHelper jdbcHelper, InternalRepositoryService repositoryService,
            SecurityService securityService, AuthorizationService authorizationService,
            SearchFunctionProvider searchFunctionProvider, ProjectsService projectsService,
            ItemActionsPopulator itemActionsPopulator) {
        this.jdbcHelper = jdbcHelper;
        this.repositoryService = repositoryService;
        this.securityService = securityService;
        this.authorizationService = authorizationService;
        this.searchFunctionProvider = searchFunctionProvider;
        this.projectsService = projectsService;
        this.itemActionsPopulator = itemActionsPopulator;
    }

    @Override
    public void setSearchFunctionProvider(SearchFunctionProvider searchFunctionProvider) {
        this.searchFunctionProvider = searchFunctionProvider;
    }

    @FunctionalInterface
    private interface SearchFunction<T> {
        List<T> search(Query query, Filters filters, int limit, State state, boolean isExactMatch,
                JdbcHelper jdbcHelper, Deadline deadline) throws SQLException;
    }

    @FunctionalInterface
    private interface StateExtractor<T> {
        State extract(List<T> results);
    }

    @FunctionalInterface
    private interface ItemsMapper<T> {
        List<Item> map(String contextUrl, boolean includeActions, List<T> results);
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        Search search = (Search) request.getImodel();

        if (search == null) {
            throw new BadRequestException("Search request can't be empty");
        }

        Query searchQuery = search.query();
        if (searchQuery == null) {
            throw new BadRequestException("Search query can't be empty");
        }

        String traceId = MDC.get("uber-trace-id");

        String projectKey = RequestUtils.getProjectKeyFromRequest(request);
        Set<String> projectRepos = DEFAULT_PROJECT.equals(projectKey) ? Set.of() :
                projectsService.getProjectRepositoriesKeys(projectKey, false);
        if (!DEFAULT_PROJECT.equals(projectKey) && projectRepos.isEmpty()) {
            log.warn("Project '{}' does not exist or has no repositories assigned", projectKey);
            response.iModel(new Response(new ArrayList<>(), STOP_STATE, false, traceId));
            return;
        }
        log.info("Project key: {}", projectKey);

        State state = search.state();

        if (state != null && state.type().equals(Type.STOP)) {
            response.iModel(new Response(new ArrayList<>(), state, false, traceId));
            return;
        }

        Filters filters = Filters.orEmpty(search.filters());
        log.info("Search filters: {}", filters);

        Set<Type> types = getSearchTypes(searchQuery, filters);
        log.info("Search types: {}", types);

        int limit = search.limit();
        boolean autoComplete = limit <= 5;

        searchQuery = Query.create(searchQuery, autoComplete);
        log.info("Search query: {}", searchQuery);

        String contextUrl = getContextUrl(request.getServletRequest());
        Deadline deadline = createDeadline(searchQuery);
        SearchParams params = new SearchParams(contextUrl, types, limit, filters, projectRepos,
                search.isExactMatch(), deadline, !autoComplete);
        if (autoComplete) {
            autoComplete(params, response, state, searchQuery, traceId);
        } else {
            allResults(params, response, state, searchQuery, traceId);
        }
    }

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Full-results path. Orchestration (stage sequencing, filtering, accumulation) runs on the request thread;
     * each individual DB query is executed through {@link #executeSearchStages} with racing enabled, so every
     * query gets its own short transaction and the two planner variants compete per query.
     */
    private void allResults(SearchParams params, RestResponse<Response> response, State state, Query searchQuery,
            String traceId) {
        try {
            Result result = executeSearchStages(params, state, searchQuery);
            if (result.hasTimeout()) {
                response.responseCode(SC_PARTIAL_CONTENT);
            }
            response.iModel(new Response(result.items(),
                    result.state() == null ? STOP_STATE : result.state(),
                    result.hasTimeout(),
                    traceId));
        } catch (SyntaxSearchServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new SyntaxSearchServiceException(e);
        }
    }

    /**
     * Autocomplete path. Mirrors {@link #allResults} but single-threaded: stage orchestration runs on the
     * request thread and each individual DB query is executed through {@link #executeSearchStages} with racing
     * disabled (one worker thread instead of the two used by the full-results path). Every query still runs in
     * its own short read-only transaction (see the non-raced fetchers), so a statement timeout that aborts one
     * query's transaction can no longer poison the following stage.
     */
    private void autoComplete(SearchParams params, RestResponse<Response> response, State state, Query searchQuery,
            String traceId) {
        long start = System.currentTimeMillis();
        try {
            Result result = executeSearchStages(params, state, searchQuery);
            if (result.hasTimeout()) {
                response.responseCode(SC_PARTIAL_CONTENT);
            }
            response.iModel(new Response(result.items(),
                    state == null ? STOP_STATE : result.state(),
                    result.hasTimeout(),
                    traceId));
        } catch (Exception e) {
            log.error("Failed to execute syntax search", e);
            response.error("Failed to execute syntax search");
        } finally {
            log.info("Syntax search completed in {} ms", System.currentTimeMillis() - start);
        }
    }

    @Override
    public StageResult<Repository> runRepoQuery(boolean disableIndexes, Query query, Filters filters, int limit,
            State state, boolean isExactMatch, Deadline deadline) {
        return runSingleQuery(disableIndexes, searchFunctionProvider::queryRepositories, query, filters, limit,
                state, isExactMatch, deadline);
    }

    @Override
    public StageResult<Node> runNodeQuery(boolean disableIndexes, boolean isFile, Query query, Filters filters,
            int limit, State state, boolean isExactMatch, Deadline deadline) {
        SearchFunction<Node> searcher =
                isFile ? searchFunctionProvider::queryFiles : searchFunctionProvider::queryFolders;
        return runSingleQuery(disableIndexes, searcher, query, filters, limit, state, isExactMatch, deadline);
    }

    /**
     * Executes one DB query (optionally with the planner's index/scan plans disabled) and captures whether it
     * timed out. Each invocation runs inside its own transaction when reached through the transactional proxy, so
     * a statement timeout that aborts the transaction cannot affect any other query.
     */
    private <T> StageResult<T> runSingleQuery(boolean disableIndexes, SearchFunction<T> searcher, Query query,
            Filters filters, int limit, State state, boolean isExactMatch, Deadline deadline) {
        SyntaxSearchTimeoutTracker.reset();
        try {
            if (disableIndexes) {
                disableIndexes(jdbcHelper);
            }
            List<T> rows = searcher.search(query, filters, limit, state, isExactMatch, jdbcHelper, deadline);
            return new StageResult<>(rows, SyntaxSearchTimeoutTracker.hasTimeout(), disableIndexes);
        } catch (SQLException e) {
            if (SYNTAX_ERROR.equals(e.getSQLState())) {
                log.warn("SyntaxSearch SQL syntax error {}", e.getMessage());
                return new StageResult<>(List.of(), SyntaxSearchTimeoutTracker.hasTimeout(), disableIndexes);
            }
            log.error("Failed to execute syntax search query", e);
            throw new SyntaxSearchServiceException("Failed to execute syntax search");
        }
    }

    /**
     * Shared stage orchestration for both the autocomplete ({@code raced == false}, single-threaded) and full
     * ({@code raced == true}, two planner variants raced) paths. Runs on the calling thread; each query gets its
     * own short read-only transaction via the transactional proxy, and only the per-query fetch is dispatched to
     * worker threads when racing.
     */
    private Result executeSearchStages(SearchParams params, State state, Query searchQuery) {
        List<Item> items = new ArrayList<>();
        State stateCopy = State.copy(state);
        boolean hasTimeout = false;

        searchQuery = adjustQueryForNonAdminUsers(searchQuery, params.projectRepos());

        if (shouldSearch(REPO, params.types(), stateCopy)) {
            if (params.deadline().expired()) {
                hasTimeout = true;
            } else {
                SearchContext<Repository> context = new SearchContext<>(params.contextUrl(), items, params.limit(),
                        stateCopy, searchQuery, params.projectRepos(), SyntaxSearchUtils::getRepoSearchState,
                        this::getRepoItems, params.deadline());
                StageProgress progress = executeSearchStage(context,
                        repoFetcher(params.raced(), searchQuery, params.filters(), params.isExactMatch(),
                                params.deadline()));
                stateCopy = progress.state();
                hasTimeout |= progress.hasTimeout();
            }
        }

        VirtualRepo virtualRepo = repositoryService.virtualRepositoryByKey(searchQuery.repo());
        StageProgress nodeProgress = virtualRepo != null
                ? executeSearchStageForVirtualRepo(params, searchQuery, virtualRepo, items, stateCopy)
                : executeFolderAndFileStages(params, searchQuery, items, stateCopy);
        stateCopy = nodeProgress.state();
        hasTimeout |= nodeProgress.hasTimeout();

        return new Result(items, stateCopy, hasTimeout);
    }

    private StageProgress executeFolderAndFileStages(SearchParams params, Query searchQuery, List<Item> items,
            State stateCopy) {
        boolean hasTimeout = false;
        if ((items.size() < params.limit()) && shouldSearch(FOLDER, params.types(), stateCopy)) {
            if (params.deadline().expired()) {
                hasTimeout = true;
            } else {
                StageProgress progress = runNodeStage(false, params, searchQuery, items, stateCopy,
                        this::getFoldersItems);
                stateCopy = progress.state();
                hasTimeout |= progress.hasTimeout();
            }
        }

        if ((items.size() < params.limit()) && shouldSearch(FILE, params.types(), stateCopy)) {
            if (params.deadline().expired()) {
                hasTimeout = true;
            } else {
                StageProgress progress = runNodeStage(true, params, searchQuery, items, stateCopy,
                        this::getFileItems);
                stateCopy = progress.state();
                hasTimeout |= progress.hasTimeout();
            }
        }
        return new StageProgress(stateCopy, hasTimeout);
    }

    private StageProgress runNodeStage(boolean isFile, SearchParams params, Query searchQuery, List<Item> items,
            State stateCopy, ItemsMapper<Node> mapper) {
        StateExtractor<Node> extractor =
                isFile ? SyntaxSearchUtils::getFileSearchState : SyntaxSearchUtils::getPathSearchState;
        SearchContext<Node> context = new SearchContext<>(params.contextUrl(), items, params.limit(), stateCopy,
                searchQuery, params.projectRepos(), extractor, mapper, params.deadline());
        return executeSearchStage(context, nodeFetcher(params.raced(), isFile, searchQuery, params.filters(),
                params.isExactMatch(), params.deadline()));
    }

    private RowFetcher<Repository> repoFetcher(boolean raced, Query query, Filters filters, boolean isExactMatch,
            Deadline deadline) {
        if (!raced) {
            return (limit, state) -> getTransactionalMe().runRepoQuery(false, query, filters, limit, state,
                    isExactMatch, deadline);
        }
        PlanState plan = new PlanState();
        return (limit, state) -> {
            if (plan.decided()) {
                return getTransactionalMe().runRepoQuery(plan.disableIndexes(), query, filters, limit, state,
                        isExactMatch, deadline);
            }
            StageResult<Repository> winner = race(disableIndexes ->
                    getTransactionalMe().runRepoQuery(disableIndexes, query, filters, limit, state, isExactMatch,
                            deadline));
            plan.decide(winner.disableIndexesUsed());
            return winner;
        };
    }

    private RowFetcher<Node> nodeFetcher(boolean raced, boolean isFile, Query query, Filters filters,
            boolean isExactMatch, Deadline deadline) {
        if (!raced) {
            return (limit, state) -> getTransactionalMe().runNodeQuery(false, isFile, query, filters, limit, state,
                    isExactMatch, deadline);
        }
        PlanState plan = new PlanState();
        return (limit, state) -> {
            if (plan.decided()) {
                return getTransactionalMe().runNodeQuery(plan.disableIndexes(), isFile, query, filters, limit, state,
                        isExactMatch, deadline);
            }
            StageResult<Node> winner = race(disableIndexes ->
                    getTransactionalMe().runNodeQuery(disableIndexes, isFile, query, filters, limit, state,
                            isExactMatch, deadline));
            plan.decide(winner.disableIndexesUsed());
            return winner;
        };
    }

    /**
     * Races the index-disabled and default planner variants of a single query and returns the first
     * <em>successful, non-timed-out</em> result. If neither variant is clean, prefers any non-error (possibly
     * partial) result over an error; if both error, the error is propagated. The losing variant is left to finish
     * on its worker thread; its in-flight statement is bounded by the request {@link Deadline}.
     */
    @VisibleForTesting
    <T> StageResult<T> race(Function<Boolean, StageResult<T>> run) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String traceId = MDC.get("uber-trace-id");
        CompletableFuture<Outcome<T>> disabled = supplyOutcome(() -> run.apply(true), authentication, traceId, "-ds");
        CompletableFuture<Outcome<T>> enabled = supplyOutcome(() -> run.apply(false), authentication, traceId, "-en");

        CompletableFuture<StageResult<T>> result = new CompletableFuture<>();
        disabled.thenAccept(outcome -> completeIfClean(result, outcome));
        enabled.thenAccept(outcome -> completeIfClean(result, outcome));
        CompletableFuture.allOf(disabled, enabled)
                .whenComplete((ignored, ignoredError) -> completeFallback(result, disabled.join(), enabled.join()));

        try {
            return result.get();
        } catch (ExecutionException e) {
            throw new SyntaxSearchServiceException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SyntaxSearchServiceException(e);
        }
    }

    @SuppressWarnings("java:S1181") // intentional: worker-thread isolation must capture all throwables
    private <T> CompletableFuture<Outcome<T>> supplyOutcome(Supplier<StageResult<T>> supplier,
            Authentication authentication, String traceId, String traceSuffix) {
        return CompletableFuture.supplyAsync(() -> {
            if (traceId != null) {
                MDC.put("uber-trace-id", traceId + traceSuffix);
            }
            SecurityContextHolder.getContext().setAuthentication(authentication);
            try {
                return new Outcome<>(supplier.get(), null);
            } catch (Throwable t) {
                return new Outcome<>(null, t);
            } finally {
                MDC.remove("uber-trace-id");
                SecurityContextHolder.clearContext();
            }
        }, executor);
    }

    private static <T> void completeIfClean(CompletableFuture<StageResult<T>> result, Outcome<T> outcome) {
        if (isClean(outcome)) {
            result.complete(outcome.result());
        }
    }

    private static <T> boolean isClean(Outcome<T> outcome) {
        return outcome.error() == null && outcome.result() != null && !outcome.result().hasTimeout();
    }

    private static <T> void completeFallback(CompletableFuture<StageResult<T>> result, Outcome<T> disabled,
            Outcome<T> enabled) {
        if (isClean(disabled)) {
            result.complete(disabled.result());
        } else if (isClean(enabled)) {
            result.complete(enabled.result());
        } else if (disabled.error() == null) {
            result.complete(disabled.result());
        } else if (enabled.error() == null) {
            result.complete(enabled.result());
        } else {
            result.completeExceptionally(disabled.error());
        }
    }

    @VisibleForTesting
    SyntaxSearchOperations getTransactionalMe() {
        return ContextHelper.get().beanForType(SyntaxSearchOperations.class);
    }

    @VisibleForTesting
    Query adjustQueryForNonAdminUsers(Query searchQuery, Set<String> projectRepos) {
        if (securityService.isAdmin() || StringUtils.isNotEmpty(searchQuery.repo())) {
            return searchQuery;
        }
        List<String> permittedRepoKeys = collectPermittedRepoKeys(projectRepos);
        log.debug("Syntax search permitted repositories: {}", permittedRepoKeys);
        return Query.adjustQueryToNonAdminUser(searchQuery, false, permittedRepoKeys);
    }

    private List<String> collectPermittedRepoKeys(Set<String> projectRepos) {
        Map<String, Boolean> canAccessRepoCache = new HashMap<>();
        Stream<RepoConfig> repoConfigsStream = repositoryService.getAllRepoConfigs().stream();
        if (!projectRepos.isEmpty()) {
            repoConfigsStream = repoConfigsStream.filter(repo -> projectRepos.contains(repo.getKey()));
        }
        List<String> permittedRepoKeys = new ArrayList<>();
        int rawLimit = ConstantValues.syntaxSearchRepositoriesLimit.getInt();
        int repositoriesLimit = rawLimit <= 0 ? 100 : rawLimit;
        AtomicBoolean exceeded = new AtomicBoolean(false);
        repoConfigsStream
                .takeWhile(ignoredRepoConfig -> !exceeded.get())
                .forEach(repoConfig -> {
                    addPermittedRepoIfAllowed(repoConfig, permittedRepoKeys, canAccessRepoCache);
                    if (permittedRepoKeys.size() > repositoriesLimit) {
                        exceeded.set(true);
                    }
                });
        if (exceeded.get()) {
            permittedRepoKeys.clear();
        }
        return permittedRepoKeys;
    }

    private void addPermittedRepoIfAllowed(RepoConfig repoConfig, List<String> permittedRepoKeys,
            Map<String, Boolean> canAccessRepoCache) {
        String repoKey = repoConfig.getKey();
        if (!authorizationService.userHasPermissionsOnRepositoryRoot(repoKey,
                Set.of(ArtifactoryPermission.values()), canAccessRepoCache)) {
            return;
        }
        if (repoConfig.getRepoType() == REMOTE) {
            addRemoteRepoWithCacheIfAllowed(repoKey, permittedRepoKeys, canAccessRepoCache);
        } else {
            permittedRepoKeys.add(repoKey);
        }
    }

    private void addRemoteRepoWithCacheIfAllowed(String repoKey, List<String> permittedRepoKeys,
            Map<String, Boolean> canAccessRepoCache) {
        String cacheRepoKey = repoKey + REMOTE_CACHE_SUFFIX;
        if (authorizationService.userHasPermissionsOnRepositoryRoot(cacheRepoKey,
                Set.of(ArtifactoryPermission.values()), canAccessRepoCache)) {
            permittedRepoKeys.add(cacheRepoKey);
        }
    }

    private StageProgress executeSearchStageForVirtualRepo(SearchParams params, Query searchQuery,
            VirtualRepo virtualRepo, List<Item> items, State stateCopy) {
        log.debug("Virtual repo found: {}", virtualRepo);
        boolean hasTimeout = false;
        NestedRealRepositoriesWithRoutes realRepositoriesRoutes =
                resolveNestedRepositories(virtualRepo, "", new HashSet<>(), new HashMap<>());
        if (realRepositoriesRoutes == null) {
            return new StageProgress(stateCopy, false);
        }
        List<String> allReposKeys = new ArrayList<>();
        allReposKeys.addAll(realRepositoriesRoutes.getLocalReposKeys());
        allReposKeys.addAll(realRepositoriesRoutes.getRemoteReposKeys()
                .stream()
                .map(k -> k + "-cache")
                .toList());
        String virtualRepoKey = virtualRepo.getKey();
        ItemsMapper<Node> folderMapper = (url, actions, folders) ->
                getFoldersItems(url, actions, folders, virtualRepoKey);
        ItemsMapper<Node> fileMapper = (url, actions, files) ->
                getFileItems(url, actions, files, virtualRepoKey);
        for (String nestedRepoKey : allReposKeys) {
            boolean limitReached = items.size() >= params.limit();
            boolean deadlineExpired = !limitReached && params.deadline().expired();
            if (deadlineExpired) {
                hasTimeout = true;
            }
            if (limitReached || deadlineExpired) {
                break;
            }
            Query nestedRepoSearchQuery = Query.copyWithDifferentRepo(searchQuery, nestedRepoKey);
            if (shouldSearch(FOLDER, params.types(), stateCopy)) {
                StageProgress progress = runNodeStage(false, params, nestedRepoSearchQuery, items, stateCopy,
                        folderMapper);
                stateCopy = progress.state();
                hasTimeout |= progress.hasTimeout();
            }
            if (shouldSearch(FILE, params.types(), stateCopy)) {
                StageProgress progress = runNodeStage(true, params, nestedRepoSearchQuery, items, stateCopy,
                        fileMapper);
                stateCopy = progress.state();
                hasTimeout |= progress.hasTimeout();
            }
        }
        return new StageProgress(stateCopy, hasTimeout);
    }

    private static void disableIndexes(JdbcHelper jdbcHelper) {
        try {
            jdbcHelper.executeUpdate("SET LOCAL ENABLE_BITMAPSCAN=off");
            log.info("SET LOCAL ENABLE_BITMAPSCAN=off");
            jdbcHelper.executeUpdate("SET LOCAL ENABLE_SEQSCAN=off");
            log.info("SET LOCAL ENABLE_SEQSCAN=off");
        } catch (SQLException e) {
            log.error("Error disabling indexes", e);
        }
    }

    @VisibleForTesting
    String getContextUrl(HttpServletRequest request) {
        return HttpUtils.getServletContextUrl(request);
    }

    @VisibleForTesting
    Deadline createDeadline(Query searchQuery) {
        return Deadline.startingNow(SyntaxSearchQueryUtils.getRequestBudgetSeconds(searchQuery));
    }

    private <T> StageProgress executeSearchStage(SearchContext<T> context, RowFetcher<T> fetcher) {
        State state = context.currentState();
        boolean autoComplete = context.query().autoComplete();
        boolean includeActions = !autoComplete;
        boolean hasTimeout = false;

        boolean needToContinue;
        do {
            if (context.deadline().expired()) {
                hasTimeout = true;
                break;
            }

            StageResult<T> chunk = fetcher.fetch(QUERY_LIMIT, state);
            hasTimeout |= chunk.hasTimeout();

            List<T> allItems = chunk.rows().stream()
                    .filter(item -> matchesProject(item, context.projectRepos()))
                    .filter(this::isNotHidden)
                    .toList();

            List<T> visibleItems = allItems.stream()
                    .filter(this::isVisible)
                    .toList();

            int max = context.limit() - context.items().size();
            boolean didVisibleItemsExceededMax = visibleItems.size() > max;
            List<T> visibleItemsToReturn = didVisibleItemsExceededMax ? visibleItems.subList(0, max) : visibleItems;

            state = generateStateByResults(context, didVisibleItemsExceededMax, visibleItemsToReturn, allItems,
                    autoComplete);
            context.items().addAll(context.mapper().map(context.contextUrl(), includeActions, visibleItemsToReturn));

            needToContinue = !autoComplete && (state != null &&
                    context.items().size() < context.limit());
        } while (needToContinue);

        return new StageProgress(state, hasTimeout);
    }

    private static <T> State generateStateByResults(SearchContext<T> context, boolean didVisibleItemsExceededMax,
            List<T> visibleItemsToReturn, List<T> allItems, boolean autoComplete) {
        State state;
        List<T> stateResults = didVisibleItemsExceededMax ? visibleItemsToReturn : allItems;
        state = autoComplete ? null : context.stateExtractor().extract(stateResults);
        return state;
    }

    @VisibleForTesting
    <T> boolean isVisible(T item) {
        if (securityService.isAdmin()) {
            return true;
        }
        RepoPath repoPath = switch (item) {
            case Repository r -> create(r.repo());
            case Node n -> create(n.repo(), n.path() + "/" + n.name());
            default -> throw new IllegalArgumentException("Unsupported item type: " + item.getClass());
        };
        return repositoryService.isRepoPathVisible(repoPath);
    }

    @VisibleForTesting
    <T> boolean matchesProject(T item, Set<String> projectRepos) {
        if (projectRepos.isEmpty()) {
            return true;
        }
        String repoKey = switch (item) {
            case Repository r -> r.repo();
            case Node n -> n.repo();
            default -> throw new IllegalArgumentException("Unsupported item type: " + item.getClass());
        };
        return projectRepos.contains(repoKey);
    }

    @VisibleForTesting
    <T> boolean isNotHidden(T item) {
        String repoKey = getRepoKeyFromItem(item);
        if (!isRepoNotHidden(repoKey)) {
            return false;
        }
        if (!isObservabilityVisible(repoKey)) {
            return false;
        }
        if (item instanceof Node node) {
            return isNotHiddenNode(node);
        }
        return true;
    }

    private <T> String getRepoKeyFromItem(T item) {
        return switch (item) {
            case Repository repo -> repo.repo();
            case Node node -> node.repo();
            default -> throw new IllegalArgumentException("Unsupported item type: " + item.getClass());
        };
    }

    private boolean isObservabilityVisible(String repoKey) {
        boolean isObservabilityOrFullSync =
                SystemRepos.isObservabilityRepo(repoKey) || SystemRepos.isFullSyncRepo(repoKey)
                        || SystemRepos.isGridInfoRepo(repoKey);
        return !isObservabilityOrFullSync || getSystemRepo(repoKey).isVisible();
    }

    private boolean isRepoNotHidden(String repoKey) {
        return !SystemRepos.isInTransitRepo(repoKey) &&
                !BundleConfigurationUtil.isReleaseBundlesV2DistributionRepoKey(repoKey);
    }

    private boolean isNotHiddenNode(Node node) {
        String repoKey = node.repo();
        String relativePath = getRelativePath(node);
        if (InternalMetadataChecker.isInternalMetadata(relativePath)) {
            return false;
        }
        String repoType = getRepoType(repoKey);
        boolean canManage = authorizationService.canManage(create(repoKey));
        if (!BrowseUtils.passesCommonFilters(node.name(), relativePath, repoType, canManage)) {
            return false;
        }
        if (isEvidenceNavigationDisabled() && isEvidencePath(relativePath)) {
            return false;
        }
        return !isReleaseBundleNavigationDisabled() || !isHiddenReleaseBundlePath(repoKey, relativePath);
    }

    private String getRelativePath(Node node) {
        String path = node.path();
        String name = node.name();
        return ".".equals(path) ? name : path + "/" + name;
    }

    private String getRepoType(String repoKey) {
        RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoKey);
        return repoConfig != null ? repoConfig.getPackageType().getType() : null;
    }

    private List<Item> getFileItems(String contextUrl, boolean includeActions, List<Node> files) {
        return getNodeItems(contextUrl, includeActions, files, FILE, null);
    }

    private List<Item> getFoldersItems(String contextUrl, boolean includeActions, List<Node> folders) {
        return getNodeItems(contextUrl, includeActions, folders, FOLDER, null);
    }

    private List<Item> getFileItems(String contextUrl, boolean includeActions, List<Node> files,
            String virtualRepoKey) {
        return getNodeItems(contextUrl, includeActions, files, FILE, virtualRepoKey);
    }

    private List<Item> getFoldersItems(String contextUrl, boolean includeActions, List<Node> folders,
            String virtualRepoKey) {
        return getNodeItems(contextUrl, includeActions, folders, FOLDER, virtualRepoKey);
    }

    private List<Item> getNodeItems(String contextUrl, boolean includeActions, List<Node> nodes,
            Type itemType, String virtualRepoKey) {
        return nodes.stream()
                .map(node -> createNodeItem(contextUrl, includeActions, node, itemType, virtualRepoKey))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Item> createNodeItem(String contextUrl, boolean includeActions, Node node,
            Type itemType, String virtualRepoKey) {
        Optional<RepositoryTypes> repositoryTypes = getRepositoryTypes(node.repo());
        if (repositoryTypes.isEmpty()) {
            return Optional.empty();
        }

        RepositoryTypes repoTypes = repositoryTypes.get();
        String originalRepoKey = node.repo();
        String repoKey = virtualRepoKey != null ? virtualRepoKey : originalRepoKey;

        String downloadUrl = getDownloadUrl(contextUrl, node.repo(), node.path(), node.name());
        String fullPath = getFullPath(originalRepoKey, node.path(), node.name());
        String updatedDownloadUrl = replaceRepoKeyInDownloadUrl(downloadUrl, originalRepoKey, virtualRepoKey);
        String updatedFullPath = replaceRepoKeyInFullPath(fullPath, originalRepoKey, virtualRepoKey);

        String repoType;
        if (virtualRepoKey != null) {
            repoType = "virtual";
        } else if (repoTypes.repoType().equals(RepoDetailsType.REMOTE)) {
            repoType = "cached";
        } else {
            repoType = repoTypes.repoType().typeNameLowercase();
        }

        List<String> actions = null;
        if (includeActions) {
            String path = create(node.repo(), getRelativePath(node)).toPath();
            actions = itemType == FILE ?
                    itemActionsPopulator.populateForFile(repoTypes.repoType().typeName(),
                            repoTypes.repoPkgType(), path) :
                    itemActionsPopulator.populateForFolder(repoTypes.repoType().typeName(),
                            repoTypes.repoPkgType(), path);
        }

        return Optional.of(new Item(
                repoKey,
                node.path(),
                node.name(),
                repoTypes.repoPkgType(),
                repoType,
                updatedDownloadUrl,
                updatedFullPath,
                itemType == FILE ? node.binLength() : 0,
                node.modified(),
                node.created(),
                itemType,
                actions
        ));
    }

    /**
     * Replaces the local repo key with virtual repo key in downloadUrl.
     *
     * @param downloadUrl     the original download URL
     * @param originalRepoKey the original repository key to replace
     * @param virtualRepoKey  the virtual repository key to use as replacement (null if no replacement needed)
     * @return the updated downloadUrl
     */
    private String replaceRepoKeyInDownloadUrl(String downloadUrl, String originalRepoKey, String virtualRepoKey) {
        if (virtualRepoKey == null) {
            return downloadUrl;
        }

        String localRepoKeyPattern = Pattern.quote(originalRepoKey);
        return downloadUrl.replaceFirst("/" + localRepoKeyPattern + "(/|$)", "/" + virtualRepoKey + "$1");
    }

    /**
     * Replaces the local repo key with virtual repo key in fullPath.
     *
     * @param fullPath        the original full path
     * @param originalRepoKey the original repository key to replace
     * @param virtualRepoKey  the virtual repository key to use as replacement (null if no replacement needed)
     * @return the updated fullPath
     */
    private String replaceRepoKeyInFullPath(String fullPath, String originalRepoKey, String virtualRepoKey) {
        if (virtualRepoKey == null) {
            return fullPath;
        }

        String localRepoKeyPattern = Pattern.quote(originalRepoKey);
        return fullPath.replaceFirst("^" + localRepoKeyPattern + "(/|$)", virtualRepoKey + "$1");
    }

    @VisibleForTesting
    Optional<RepositoryTypes> getRepositoryTypes(String repoKey) {
        repoKey = removeDashCache(repoKey);
        RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repoKey);
        if (repoConfig == null) {
            log.warn("Repository '{}' not found, skipping item", repoKey);
            return Optional.empty();
        }
        RepoDetailsType repoType = repoConfig.getRepoType();
        String repoPkgType = repoConfig.getPackageType().getType();
        return Optional.of(new RepositoryTypes(repoType, repoPkgType));
    }

    private List<Item> getRepoItems(String contextUrl, boolean includeActions, List<Repository> repositories) {
        return repositories.stream().map(repository -> {
            String repoKey =
                    repository.type().equals("remote") ? (repository.repo() + REMOTE_CACHE_SUFFIX) : repository.repo();
            List<String> actions = null;
            if (includeActions) {
                String path = create(repository.repo(), "").toPath();
                actions = itemActionsPopulator.populateForRepository(repository.type(), repository.packageType(), path);
            }
            return new Item(
                    repoKey,
                    "",
                    repoKey,
                    repository.packageType(),
                    repository.type().equals("remote") ? "cached" : repository.type(),
                    getDownloadUrl(contextUrl, repository.repo(), "", ""),
                    repoKey,
                    0,
                    repository.modified(),
                    repository.created(),
                    REPO,
                    actions
            );
        }).toList();
    }

    record RepositoryTypes(RepoDetailsType repoType, String repoPkgType) {
    }

    private record SearchContext<T>(String contextUrl, List<Item> items, int limit, State currentState, Query query,
                                    Set<String> projectRepos, StateExtractor<T> stateExtractor,
                                    ItemsMapper<T> mapper, Deadline deadline) {
    }

    /**
     * Immutable per-request search parameters threaded unchanged through every stage. Bundled into a single object
     * so the stage methods stay within the parameter-count limit. {@code raced} is {@code true} for the full-results
     * path (two planner variants raced) and {@code false} for autocomplete (single-threaded).
     */
    private record SearchParams(String contextUrl, Set<Type> types, int limit, Filters filters,
                                Set<String> projectRepos, boolean isExactMatch, Deadline deadline, boolean raced) {
    }

    /**
     * Aggregated result of a full syntax-search request across all stages: the collected items, the final
     * pagination cursor, and whether any stage timed out or hit the request deadline.
     */
    private record Result(List<Item> items, State state, boolean hasTimeout) {
    }

    /**
     * Result of running a full stage (all pagination chunks): the next pagination cursor and whether any chunk
     * timed out or the deadline expired mid-stage.
     */
    private record StageProgress(State state, boolean hasTimeout) {
    }

    /**
     * Fetches one pagination chunk for a stage. Implementations either run the query inline (autocomplete) or
     * race the two planner variants (full results), reusing the winning plan for subsequent chunks.
     */
    @FunctionalInterface
    private interface RowFetcher<T> {
        StageResult<T> fetch(int limit, State state);
    }

    /**
     * Holds the planner variant chosen by racing the first chunk of a stage, so chunks 2..n reuse it instead of
     * racing again.
     */
    private static final class PlanState {
        private Boolean disableIndexes;

        boolean decided() {
            return disableIndexes != null;
        }

        boolean disableIndexes() {
            return disableIndexes;
        }

        void decide(boolean value) {
            this.disableIndexes = value;
        }
    }

    private record Outcome<T>(StageResult<T> result, Throwable error) {
    }
}