package org.artifactory.storage.db.search;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.aql.AqlConverts;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.aql.util.AqlSearchablePath;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;
import org.artifactory.util.CollectionUtils;
import org.jfrog.common.StreamSupportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.aql.api.internal.AqlBase.or;
import static org.artifactory.aql.util.AqlUtils.getSearchClauseForPaths;
import static org.artifactory.aql.util.AqlUtils.getSearchablePathForCurrentFolderAndSubfolders;
import static org.artifactory.mime.DockerNaming.MANIFEST_FILENAME;
import static org.artifactory.mime.DockerNaming.MANIFEST_LIST_FILENAME;

/**
 * A utility class to perform several common searches.
 *
 * @author Yossi Shaul
 */
@Service
public class NodeSearcher {
    private static final Logger log = LoggerFactory.getLogger(NodeSearcher.class);
    private static final String CACHE_KEY_SHA_256 = "FindFilesBySha256:";
    private static final String CACHE_KEY_PATH_AND_NAME = "FindFilesByPathAndName:";
    private static final Cache<String, Boolean> SHA256_PROBE_LIMIT_REACHED_CACHE = Caffeine.newBuilder()
            .maximumSize(ConstantValues.findFilesBySha256ShaProbeLimitReachedCacheSize.getLong())
            .expireAfterWrite(ConstantValues.findFilesBySha256ShaProbeLimitReachedCacheExpMs.getLong(),
                    TimeUnit.MILLISECONDS)
            .build();
    private final AqlService aqlService;

    @Inject
    public NodeSearcher(AqlService aqlService) {
        this.aqlService = aqlService;
    }

    /**
     * Find files with specific name under the specified repo and (optional) path, including sub directories. This
     * method filters directories and returns only files. <p/>
     * Do not use this method to search only for files directly under the provided paths. Use other methods
     * (such as RepositoryService#getChildrenNames(RepoPath). <p/>
     * None of the input params should contain wildcard characters!
     *
     * @param repo The repository to search under
     * @param path The path to search under - can be empty
     * @param name The name of the files to look for
     * @return Files matching the search criteria
     */
    @Nonnull
    public Set<FileInfo> findFiles(String repo, @Nullable String path, String name) {
        validateInput(repo, name);
        AqlApiItem fallbackQuery = buildFindFilesByNameQuery(trim(repo), trimToNull(path), trim(name));
        AqlApiItem getWithLimitQuery = buildFindFilesByNameQuery(null, trimToNull(path), trim(name));
        return executeQueryWithCache(repo, path, name, fallbackQuery, getWithLimitQuery, CACHE_KEY_PATH_AND_NAME);
    }

    @Nonnull
    public Set<FileInfo> findManifests(String repo, @Nullable String path, boolean isExactPathMatch,
            boolean includeManifest, boolean includeManifestList) {
        AqlApiItem query = AqlApiItem.create();
        AqlBase.CriteriaClause<AqlApiItem> matchesPath = isExactPathMatch ?
                AqlApiItem.path().equal(path) : AqlApiItem.path().matches(path + "/*");
        AqlBase.AndClause<AqlApiItem> and = and();
        AqlBase.OrClause<AqlApiItem> orManifests = or();
        if (includeManifest) {
            orManifests.append(AqlApiItem.name().equal(MANIFEST_FILENAME));
        }
        if (includeManifestList) {
            orManifests.append(AqlApiItem.name().equal(MANIFEST_LIST_FILENAME));
        }
        and.append(orManifests);
        and.append(matchesPath);
        and.append(AqlApiItem.repo().equal(repo));
        query.filter(and).distinct(false)
                .include(AqlApiItem.repo(), AqlApiItem.itemId(), AqlApiItem.path(), AqlApiItem.name());

        AqlEagerResult<AqlItem> results = aqlService.executeQueryEager(query);
        return filterResults(results);
    }

    /**
     * Find files with specific sha256 checksum under the specified repo and (optional) path, including sub directories.
     * None of the input params should contain wildcard characters!
     *
     * @param repo   The repository to search under
     * @param path   The path to search under - can be empty
     * @param sha256 The name of the files to look for
     * @return Files matching the search criteria
     */
    public Set<FileInfo> findFilesBySha256(String repo, @Nullable String path, String sha256) {
        validateInput(repo, sha256);
        String trimmedRepo = trim(repo);
        String trimmedPath = trimToNull(path);
        String trimmedSha = trim(sha256);
        String pathKey = path != null ? path : "";
        String key = CACHE_KEY_SHA_256 + pathKey + "-" + trimmedSha;
        long maxResultSizeLimit = ConstantValues.repoItemsCacheQueryLimit.getLong();
        List<AqlItem> results = ReposItemsThreadLocalCacheService.getAll(key, trimmedRepo,
                limit -> findFilesBySha256Internally(null, trimmedPath, trimmedSha, limit),
                maxResultSizeLimit,
                () -> findFilesBySha256Internally(trimmedRepo, trimmedPath, trimmedSha, -1));
        return filterResults(results);
    }

    /**
     * One fetch for {@link #findFilesBySha256}. Sha-first
     * ({@link ConstantValues#findFilesBySha256ShaFirstProbeEnabled}): checksum-only probe uses
     * {@link ConstantValues#findFilesBySha256ShaFirstProbeLimit} (default {@code 50}) only (RTDEV-83944), not
     * {@code requestedLimit}, so incomplete vs full probe batch correctly detects ambiguity. Incomplete probes filter
     * probe rows in memory by repo/path; legacy fallback uses normal AQL repo/path filters. Digest ambiguity cache;
     * {@code repo == null} for the limited supplier so thread-local cache can group by repo. Legacy: repo-less
     * limited AQL when {@code repo} is {@code null} and {@code requestedLimit >= 0}; repo-scoped unlimited AQL when
     * {@code requestedLimit == -1}.
     *
     * @param requestedLimit {@code >= 0} thread-local row cap; {@code -1} unlimited (cache-miss fallback and
     *                       sha-first fallback queries apply no row limit).
     */
    private List<AqlItem> findFilesBySha256Internally(@Nullable String repo, @Nullable String path, String sha256,
            long requestedLimit) {
        boolean shaFirstProbe = ConstantValues.findFilesBySha256ShaFirstProbeEnabled.getBoolean();
        if (shaFirstProbe && !isSha256ProbeLimitReachedCached(sha256)) {
            long fetchLimit = shaFirstProbeFetchLimit();
            AqlApiItem shaOnlyProbe = buildFindFilesBySha256Aql(null, null, sha256);
            List<AqlItem> probeResults =
                    aqlService.executeQueryEager(shaOnlyProbe.limit(fetchLimit)).getResults();
            if (probeResults.size() < fetchLimit) {
                return probeResults.stream()
                        .filter(item -> itemMatchesRepoAndPathFilters(item, repo, path))
                        .collect(Collectors.toList());
            }
            rememberSha256ProbeLimitReached(sha256);
            log.debug("SHA256 checksum probe used full batch limit {}; running path-scoped fallback for sha256 {}",
                    fetchLimit, sha256);
            return executeLegacyFindFilesBySha256Query(repo, path, sha256, requestedLimit);
        }

        return executeLegacyFindFilesBySha256Query(repo, path, sha256, requestedLimit);
    }

    /**
     * Row cap for the checksum-only probe ({@link ConstantValues#findFilesBySha256ShaFirstProbeLimit}), safe for
     * {@link AqlApiItem#limit(long)}.
     */
    private static long shaFirstProbeFetchLimit() {
        long configured = ConstantValues.findFilesBySha256ShaFirstProbeLimit.getLong();
        long atLeastOne = Math.max(configured, 1L);
        return Math.min(atLeastOne, Integer.MAX_VALUE);
    }

    /**
     * Thread-local/cache path used after a saturated SHA-only probe or when sha-first is off: same AQL shape as before
     * RTDEV-83944 ({@code repo} may be null for the limited cache batch; path is still applied in AQL when non-null).
     */
    private List<AqlItem> executeLegacyFindFilesBySha256Query(@Nullable String repo, @Nullable String path,
            String sha256, long requestedLimit) {
        AqlApiItem query = buildFindFilesBySha256Aql(repo, path, sha256);
        if (requestedLimit >= 0) {
            int lim = (int) Math.min(requestedLimit, Integer.MAX_VALUE);
            query = query.limit(lim);
        }
        return aqlService.executeQueryEager(query).getResults();
    }

    private static boolean isSha256ProbeLimitReachedCached(String sha256) {
        return SHA256_PROBE_LIMIT_REACHED_CACHE.getIfPresent(sha256) != null;
    }

    private static void rememberSha256ProbeLimitReached(String sha256) {
        SHA256_PROBE_LIMIT_REACHED_CACHE.put(sha256, Boolean.TRUE);
    }

    @VisibleForTesting
    public static void invalidateSha256ProbeLimitReachedCacheForTests() {
        SHA256_PROBE_LIMIT_REACHED_CACHE.invalidateAll();
    }

    /**
     * Mirrors {@link #createRepoAndPathFilters(String, String)} for in-memory filtering of sha256-only probe rows.
     */
    private boolean itemMatchesRepoAndPathFilters(AqlItem item, @Nullable String repo, @Nullable String path) {
        if (repo != null && !repo.equals(item.getRepo())) {
            return false;
        }
        if (path == null) {
            return true;
        }
        if (".".equals(path)) {
            return itemMatchesPathDotSlashStar(item);
        }
        String itemPath = normalizeNodePathForComparison(item.getPath());
        String searchPath = normalizeNodePathForComparison(path);
        if (ConstantValues.dockerSearchManifestByDigestAndTagEnabled.getBoolean()) {
            return searchPath.equals(itemPath) || itemPath.startsWith(searchPath + "/");
        }
        return itemPath.startsWith(searchPath + "/");
    }

    /**
     * Same as {@link #createRepoAndPathFilters(String, String)} when path is {@code "."}:
     * {@code path.matches("./*")}, and when {@link ConstantValues#dockerSearchManifestByDigestAndTagEnabled} is on,
     * also {@code path.equal(".")}.
     */
    private boolean itemMatchesPathDotSlashStar(AqlItem item) {
        String rawItemPath = item.getPath();
        String normalized = normalizeNodePathForComparison(rawItemPath);
        boolean matchesDotSlashStar = !normalized.isEmpty();
        if (ConstantValues.dockerSearchManifestByDigestAndTagEnabled.getBoolean()) {
            return ".".equals(rawItemPath) || matchesDotSlashStar;
        }
        return matchesDotSlashStar;
    }

    private static String normalizeNodePathForComparison(@Nullable String nodePath) {
        if (nodePath == null || ".".equals(nodePath)) {
            return "";
        }
        return nodePath;
    }

    private Set<FileInfo> executeQueryWithCache(String repo, @Nullable String path, String reference,
            AqlApiItem fallbackQuery, AqlApiItem getWithLimitQuery, String cacheKey) {
        return executeQueryWithCache(repo, path, reference, () -> fallbackQuery, () -> getWithLimitQuery, cacheKey);
    }

    private Set<FileInfo> executeQueryWithCache(String repo, @Nullable String path, String reference,
            Supplier<AqlApiItem> fallbackQuery, Supplier<AqlApiItem> getWithLimitQuery, String cacheKey) {
        long maxResultSizeLimit = ConstantValues.repoItemsCacheQueryLimit.getLong();
        path = path != null ? path : "";
        String key = cacheKey + path + "-" + reference;
        List<AqlItem> results = ReposItemsThreadLocalCacheService.getAll(key, repo,
                limit -> aqlService.executeQueryEager(getWithLimitQuery.get().limit(limit)).getResults(),
                maxResultSizeLimit,
                () -> aqlService.executeQueryEager(fallbackQuery.get()).getResults());

        return filterResults(results);
    }

    /**
     * Find files with specific property name and value under the specified repo and (optional) path,
     * including subdirectories.
     * None of the input params should contain wildcard characters!
     *
     * @param repo          The repository to search under
     * @param path          The path to search under - can be empty
     * @param propertyName  The name of the property to look for
     * @param propertyValue The value of the property to look for
     * @return Files matching the search criteria
     */
    public Set<FileInfo> findFilesByProperty(String repo, @Nullable String path, String propertyName,
            String propertyValue) {
        validateInput(repo, propertyName);
        AqlApiItem query =
                buildFindFilesByPropertyQuery(trim(repo), trimToNull(path), trim(propertyName), trim(propertyValue));
        AqlEagerResult<AqlItem> results = aqlService.executeQueryEager(query);
        return filterResults(results);
    }

    public Set<FileInfo> findFilesUnderPath(String repo, @Nullable String path) {
        AqlBase.AndClause<AqlApiItem> rootFilterClause = and();
        List<AqlSearchablePath> sourceFiles =
                getSearchablePathForCurrentFolderAndSubfolders(RepoPathFactory.create(repo, path));
        rootFilterClause.append(getSearchClauseForPaths(sourceFiles));
        rootFilterClause.append(AqlApiItem.type().equal("file"));
        AqlApiItem query = AqlApiItem.create().filter(rootFilterClause);
        return StreamSupportUtils.stream(aqlService.executeQueryEager(query).getResults())
                .map(AqlConverts.toFileInfo)
                .collect(Collectors.toSet());
    }

    private AqlApiItem buildFindFilesByNameQuery(String repo, String path, String name) {
        AqlApiItem query = AqlApiItem.create();
        AqlBase.AndClause<AqlApiItem> andFilter = createRepoAndPathFilters(repo, path);
        andFilter.append(AqlApiItem.name().equal(name));
        return query.filter(andFilter).distinct(false);
    }

    private AqlApiItem buildFindFilesBySha256Aql(@Nullable String repo, @Nullable String path, String sha256) {
        AqlApiItem query = AqlApiItem.create();
        AqlBase.AndClause<AqlApiItem> andFilter = createRepoAndPathFilters(repo, path);
        if (canFilterBySha256() && isSha256Ready()) {
            andFilter.append(AqlApiItem.sha2().equal(sha256));
        } else {
            andFilter.append(AqlApiItem.property().property("sha256", AqlComparatorEnum.equals, sha256));
        }
        return query.filter(andFilter).distinct(false);
    }

    private AqlApiItem buildFindFilesByPropertyQuery(String repo, String path, String propertyName,
            String propertyValue) {
        AqlApiItem query = AqlApiItem.create();
        AqlBase.AndClause<AqlApiItem> andFilter = createRepoAndPathFilters(repo, path);
        andFilter.append(AqlApiItem.property().property(propertyName, AqlComparatorEnum.equals, propertyValue));

        return query.filter(andFilter);
    }

    private boolean canFilterBySha256() {
        return !ConstantValues.dockerPullV1SchemaByDigestEnabled.getBoolean();
    }

    @Nonnull
    private AqlBase.AndClause<AqlApiItem> createRepoAndPathFilters(String repo, String path) {
        AqlApiItem.AndClause<AqlApiItem> and = AqlApiItem.and();
        if (repo != null) {
            and.append(AqlApiItem.repo().equal(repo));
        }
        if (path != null) {
            AqlBase.CriteriaClause<AqlApiItem> matches = AqlApiItem.path().matches(path + "/*");
            if (ConstantValues.dockerSearchManifestByDigestAndTagEnabled.getBoolean()) {
                AqlBase.CriteriaClause<AqlApiItem> equal = AqlApiItem.path().equal(path);
                AqlApiItem.OrClause<AqlApiItem> or = AqlApiItem.or();
                or.append(matches);
                or.append(equal);
                and.append(or);
            } else {
                and.append(matches);
            }
        }
        // remove the restriction for files only - this is not indexed and it's probably better to filter the results
        and.append(AqlApiItem.type().equal("any"));
        return and;
    }

    private Set<FileInfo> filterResults(AqlEagerResult<AqlItem> results) {
        return filterResults(results.getResults());
    }

    private Set<FileInfo> filterResults(List<AqlItem> results) {
        if (CollectionUtils.isNullOrEmpty(results)) {
            return Collections.emptySet();
        }
        boolean shouldLogUserInfo = ConstantValues.dockerPullV1SchemaByDigestEnabled.getBoolean();
        Predicate<AqlItem> filter = shouldLogUserInfo ? filesOnlyAndLog() : filesOnly();
        return results.stream()
                .filter(filter)
                .map(AqlConverts.toFileInfo)
                .collect(Collectors.toSet());
    }

    private Predicate<AqlItem> filesOnly() {
        return i -> i.getType().equals(AqlItemTypeEnum.file);
    }

    private Predicate<AqlItem> filesOnlyAndLog() {
        return i -> {
            boolean isFile = i.getType().equals(AqlItemTypeEnum.file);
            boolean isManifest = isFile && i.getName() != null && i.getName().equals(MANIFEST_FILENAME);
            if (isManifest && !RequestThreadLocal.getRequestPath().endsWith(i.getSha2())) {
                log.debug("Image V1 requested: {} , Client address: {} , User-Agent: {}", i.getPath(),
                        RequestThreadLocal.getClientAddress(), RequestThreadLocal.getUserAgent());
            }
            return isFile;
        };
    }

    private void validateInput(String repo, String name) {
        if (StringUtils.isBlank(repo)) {
            throw new IllegalArgumentException("Repo cannot be cannot be empty");
        }
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Name cannot be cannot be empty");
        }
    }

    private boolean isSha256Ready() {
        return ContextHelper.get().beanForType(InternalDbService.class).isSha256Ready();
    }
}
