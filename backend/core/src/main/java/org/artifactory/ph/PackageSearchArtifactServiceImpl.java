package org.artifactory.ph;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.exception.PackageInternalServerError;
import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.model.FilteringProperty;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.model.PackageSearchContext;
import com.jfrog.ph.acl.service.PackageNugetSearch;
import com.jfrog.ph.acl.service.PackageRepoService;
import com.jfrog.ph.acl.service.PackageSearchArtifactService;
import com.jfrog.ph.acl.virtual.resolution.PackageRepo;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.aql.AqlConverts;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.domain.sensitive.AqlApiProperty;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.aql.result.rows.AqlLazyObjectResultStreamer;
import org.artifactory.aql.result.rows.FileInfoWithStatisticsItemRow;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.repo.PackageType;
import org.artifactory.common.repo.RepoType;
import org.artifactory.repo.RepoPath;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.db.DbService;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;
import org.jfrog.client.util.PathUtils;
import org.jfrog.storage.DbType;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.joining;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.create;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.createWithEmptyResults;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.depth;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.path;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.property;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.statistic;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.type;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CONTENT_LENGTH;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.REPOSITORY_KEY;

@Slf4j
public class PackageSearchArtifactServiceImpl implements PackageSearchArtifactService {

    // Query template to get packageIds from MDS using GraphQL API.
    private static final String MDS_QUERY_TEMPLATE =
            "{\"query\": " +
                    "\"{ packages ( filter: {name: \\\"$packageId\\\" } ) " +
                    "{ pageInfo { hasNextPage endCursor } edges { node { name packageType } } }}\"}";

    private static final String SEARCH_CACHE_KEY = "PackageHandlerSearchCache:";
    private static final String SEARCH_REPOS_CACHE_KEY = "PackageHandlerSearchReposCache:";
    public static final String PARAMETER = "Parameter '";
    private static final int UNLIMITED = 0;
    private final AqlService aqlService;
    private final AuthorizationService authorizationService;
    private final PackageRepoService repoService;
    private final PackageNugetSearch packageNugetSearch;
    private final RepositoryConfigService repositoryConfigService;
    private final ArtifactoryBinaryService binaryService;
    private final DbService dbService;

    public PackageSearchArtifactServiceImpl(AqlService aqlService, AuthorizationService authorizationService,
            PackageRepoService repoService, PackageNugetSearch packageNugetSearch,
            RepositoryConfigService repositoryConfigService, ArtifactoryBinaryService binaryService,
            DbService dbService) {
        this.aqlService = aqlService;
        this.authorizationService = authorizationService;
        this.repoService = repoService;
        this.packageNugetSearch = packageNugetSearch;
        this.repositoryConfigService = repositoryConfigService;
        this.binaryService = binaryService;
        this.dbService = dbService;
    }

    /**
     * Reports whether the current {@link DbService#getDatabaseType()} can still perform an index seek
     * when {@code prop_key} is case-folded, i.e. whether a {@code LOWER(prop_key)} predicate remains
     * sargable.
     * <p>
     * Postgres has a partial index on {@code LOWER(prop_value)} filtered by {@code LOWER(prop_key)}
     * (see {@code node_prop_nuget_partial_idx}); Oracle has a function-based index over
     * {@code LOWER(prop_key)}/{@code LOWER(prop_value)}. Both stay sargable when {@code prop_key} is
     * folded too. MySQL, MariaDB and MSSQL only have a plain {@code (prop_key, prop_value)} index, so
     * {@code prop_key} must stay exact there to remain sargable.
     * <p>
     * Called (via {@code com.jfrog.ph.service.PackageHandlerSearchService#supportsLowerPropKeyIndexSeek}
     * in the package-handler repo) from {@code com.jfrog.ph.nuget.command.NugetHeavyQueryCacheServiceImpl
     * #getRegistration} to pick the {@code FilteringProperty.MatchMode} used for NuGet id lookups
     * against {@code node_props}: {@code CASE_INSENSITIVE} (fold both key and value) when this returns
     * {@code true}, {@code VALUE_CASE_INSENSITIVE} (keep {@code prop_key} exact) otherwise.
     *
     * @return {@code true} for Postgres and Oracle, {@code false} for MySQL, MariaDB and MSSQL.
     */
    @Override
    public boolean supportsLowerPropKeyIndexSeek() {
        DbType dbType = dbService.getDatabaseType();
        return dbType == DbType.POSTGRESQL || dbType == DbType.ORACLE;
    }

    @Override
    public Stream<PackageArtifact> findArtifactsByExtension(String repoKey, String path, String... extensions) {
        path = StringUtils.isBlank(path) ? null : path.trim();
        repoKey = assertAndGetValidRepo(repoKey);
        assertNoAstrix(path);
        assertValidExtensions(extensions);
        // Creating the query with no extra fields
        AqlApiItem query = AqlApiItem.createWithEmptyResults();
        // Adding the fields we need
        buildIncludeQuery(query);
        // Preparing the query filters
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();
        and.append(AqlApiItem.repo().equal(repoKey));
        filterByPathEqualOrGreater(and, path);
        filterByExtension(and, extensions);
        filterByDepth(and, path);
        and.append(AqlApiItem.type().equal(AqlItemTypeEnum.file.signature));
        // Adding the AND filter to the query
        query.filter(and);
        return executeAqlQueryAndMapResults(query);
    }

    @Override
    public Stream<PackageArtifact> findArtifactsChildren(String repoKey, String path) {
        return findArtifactsChildren(repoKey, path, 0, UNLIMITED);
    }

    @Override
    public Stream<PackageArtifact> findArtifactsChildren(String repoKey, String path, boolean includeFolder) {
        return findArtifactsChildren(repoKey, path, 0, UNLIMITED, includeFolder);
    }

    @Override
    public Stream<PackageArtifact> findArtifactsChildren(String repoKey, String path, int offset, int limit) {
        return findArtifactsChildren(repoKey, path, offset, limit, 0, false);
    }

    @Override
    public Stream<PackageArtifact> findArtifactsChildren(String repoKey, String path, int offset, int limit,
            boolean includeFolder) {
        return findArtifactsChildren(repoKey, path, offset, limit, 0, includeFolder);
    }

    @Override
    public Stream<PackageArtifact> findArtifactsChildren(String repoKey, String path, int offset, int limit,
            long lastUpdatedBefore) {
        return findArtifactsChildren(repoKey, path, offset, limit, lastUpdatedBefore, false);
    }

    @Override
    public Stream<PackageArtifact> findArtifactsChildren(String repoKey, String path, int offset, int limit,
            long lastUpdatedBefore, boolean includeFolder) {
        path = StringUtils.isBlank(path) ? null : path.trim();
        repoKey = assertAndGetValidRepo(repoKey);
        assertNoAstrix(path);
        // Creating the query with no extra fields
        AqlApiItem query = AqlApiItem.createWithEmptyResults();
        // Adding the fields we need
        buildIncludeQuery(query);
        // Preparing the query filters
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();
        and.append(AqlApiItem.repo().equal(repoKey));
        filterByPathEqualOrGreater(and, path);
        filterByDepth(and, path);
        filterByLastUpdatedBeforeIfNeeded(and, lastUpdatedBefore);
        and.append(AqlApiItem.type()
                .equal(includeFolder ? AqlItemTypeEnum.any.signature : AqlItemTypeEnum.file.signature));
        // Adding the AND filter to the query
        query.filter(and);
        addOffsetAndLimit(offset, limit, query);
        return executeAqlQueryAndMapResults(query);
    }

    @Override
    public Stream<PackageArtifact> findArtifactsChildrenWithName(String repoKey, String path, String name) {
        return findArtifactsChildrenWithName(repoKey, path, name, 0, UNLIMITED);
    }

    @Override
    public Stream<PackageArtifact> findArtifactsChildrenWithName(String repoKey, String path, String name, int offset,
            int limit) {
        path = StringUtils.isBlank(path) ? null : path.trim();
        name = StringUtils.isBlank(name) ? null : name.trim();
        repoKey = assertAndGetValidRepo(repoKey);
        assertNoAstrix(path);
        assertInputNotEmpty(name);
        // Creating the query with no extra fields
        AqlApiItem query = AqlApiItem.createWithEmptyResults();
        // Adding the fields we need
        buildIncludeQuery(query);
        // Preparing the query filters
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();
        and.append(AqlApiItem.repo().equal(repoKey));
        filterByPathEqualOrGreater(and, path);
        filterByName(and, name);
        filterByDepth(and, path);
        and.append(AqlApiItem.type().equal(AqlItemTypeEnum.file.signature));
        // Adding the AND filter to the query
        query.filter(and);
        addOffsetAndLimit(offset, limit, query);
        return executeAqlQueryAndMapResults(query);
    }

    @Override
    public Stream<PackageArtifact> findArtifactsByExactName(String repoKey, String name) {
        name = StringUtils.isBlank(name) ? null : name.trim();
        repoKey = assertAndGetValidRepo(repoKey);
        assertInputNotEmpty(name);
        assertNoAstrix(name);
        // Creating the query with no extra fields
        AqlApiItem query = AqlApiItem.createWithEmptyResults();
        // Adding the fields we need
        buildIncludeQuery(query);
        // Preparing the query filters
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();
        and.append(AqlApiItem.repo().equal(repoKey));
        filterByName(and, name);
        filterByDepth(and, null);
        and.append(AqlApiItem.type().equal(AqlItemTypeEnum.file.signature));
        // Adding the AND filter to the query
        query.filter(and);
        return executeAqlQueryAndMapResults(query);
    }

    @Override
    public Optional<PackageArtifact> findFirstReadableRetrievableArtifactByExactName(String name,
            @Nullable PackageType packageType, Set<String> excludePathPrefixes) throws PackageException {
        String exactName = StringUtils.isBlank(name) ? null : name.trim();
        assertInputNotEmpty(exactName);
        assertNoAstrix(exactName);

        // Restrict by package type outside the hot query path: package type is repository metadata, not an indexed
        // nodes field, so we precompute the eligible storage repo keys and filter streamed rows by membership.
        Set<String> allowedStorageRepoKeys = packageType == null ? null : collectStorageRepoKeys(packageType);
        if (allowedStorageRepoKeys != null && allowedStorageRepoKeys.isEmpty()) {
            return Optional.empty();
        }

        // Exact name match keeps the query on nodes_name_path_idx; type=file is a residual correctness filter.
        AqlApiItem query = AqlApiItem.createWithEmptyResults();
        buildIncludeQuery(query);
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();
        and.append(AqlApiItem.name().equal(exactName));
        and.append(AqlApiItem.type().equal(AqlItemTypeEnum.file.signature));
        query.filter(and);

        return findFirstReadableRetrievableMatch(query, allowedStorageRepoKeys, excludePathPrefixes);
    }

    /**
     * Resolve the set of storage repository keys (local repos plus remote-cache repos) for the given package type.
     * Remote artifacts physically live in the remote-cache repo, so the cache key -- not the remote key -- is what the
     * AQL {@code repo} field reports.
     */
    private Set<String> collectStorageRepoKeys(PackageType packageType) {
        Set<String> storageRepoKeys = new HashSet<>();
        for (PackageRepo repo : repoService.getAllReposByType(packageType, RepoType.LOCAL)) {
            storageRepoKeys.add(repo.getKey());
        }
        for (PackageRepo repo : repoService.getAllReposByType(packageType, RepoType.REMOTE)) {
            try {
                storageRepoKeys.add(repoService.getRemoteCacheRepoKey(repo.getKey()));
            } catch (PackageNotFoundException e) {
                log.debug("Remote repository '{}' has no local cache; skipping for instance-wide name lookup",
                        repo.getKey());
            }
        }
        return storageRepoKeys;
    }

    private Optional<PackageArtifact> findFirstReadableRetrievableMatch(AqlApiItem query,
            @Nullable Set<String> allowedStorageRepoKeys, Set<String> excludePathPrefixes) throws PackageException {
        FileInfoWithStatisticsItemRow row;
        try (AqlLazyResult<AqlItem> result = aqlService.executeQueryLazy(query)) {
            AqlLazyObjectResultStreamer<FileInfoWithStatisticsItemRow> streamer =
                    new AqlLazyObjectResultStreamer<>(result, FileInfoWithStatisticsItemRow.class);
            while ((row = streamer.getRow()) != null) {
                if (isRowEligible(row, allowedStorageRepoKeys, excludePathPrefixes)) {
                    return Optional.of(PackageArtifactMapper.artifact(row));
                }
            }
        } catch (Exception e) {
            log.error("Failed to execute instance-wide exact-name search: {}", e.getMessage(), e);
            throw new PackageInternalServerError("Failed to execute instance-wide exact-name search", e);
        }
        return Optional.empty();
    }

    private boolean isRowEligible(FileInfoWithStatisticsItemRow row, @Nullable Set<String> allowedStorageRepoKeys,
            Set<String> excludePathPrefixes) {
        if (allowedStorageRepoKeys != null && !allowedStorageRepoKeys.contains(row.getRepo())) {
            return false;
        }
        if (!excludePathPrefixes.isEmpty() && isExcludedPath(row, excludePathPrefixes)) {
            return false;
        }
        return authorizationService.canRead(row.getRepoPath()) && isBinaryAvailable(row);
    }

    private boolean isExcludedPath(FileInfoWithStatisticsItemRow row, Set<String> excludePathPrefixes) {
        String fullPath = row.getRepoPath().getPath();
        return excludePathPrefixes.stream().anyMatch(prefix -> fullPath.startsWith(prefix + "/"));
    }

    /**
     * For federated repositories, the node row may exist before its binary has finished replicating to this node.
     * Reusing such a row would result in a download failure on the caller. Non-federated repositories always have
     * the binary co-located with the node row, so they return {@code true} immediately.
     */
    private boolean isBinaryAvailable(FileInfoWithStatisticsItemRow row) {
        if (repositoryConfigService.getFederatedRepoConfigByKey(row.getRepo()) == null) {
            return true;
        }
        String sha2 = row.getSha2();
        if (StringUtils.isBlank(sha2)) {
            return false;
        }
        BinaryElement binaryInfo = binaryService.findBinary(ChecksumType.sha256, sha2);
        if (binaryInfo == null) {
            return false;
        }
        binaryInfo.addHeader(REPOSITORY_KEY, row.getRepo());
        binaryInfo.addHeader(CONTENT_LENGTH, String.valueOf(binaryInfo.getLength()));
        try (InputStream binary = binaryService.getBinary(binaryInfo)) {
            return binary != null;
        } catch (BinaryNotFoundException e) {
            log.debug("Binary not yet available on this node for federated artifact {}:{}/{}",
                    row.getRepo(), row.getPath(), row.getName(), e);
            return false;
        } catch (IOException e) {
            log.debug("Failed to verify binary availability for federated artifact {}:{}/{}",
                    row.getRepo(), row.getPath(), row.getName(), e);
            return false;
        }
    }

    @Override
    public Stream<PackageArtifact> findArtifactsBySha2Checksum(String repoKey, String sha2Checksum) {
        assertInputNotEmpty(sha2Checksum);
        repoKey = assertAndGetValidRepo(repoKey);
        // Creating the query with no extra fields
        AqlApiItem query = AqlApiItem.createWithEmptyResults();
        // Adding the fields we need
        buildIncludeQuery(query);
        // Preparing the query filters
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();
        and.append(AqlApiItem.repo().equal(repoKey));
        and.append(AqlApiItem.sha2().equal(sha2Checksum));
        and.append(AqlApiItem.type().equal(AqlItemTypeEnum.file.signature));
        // Adding the AND filter to the query
        query.filter(and);
        return executeAqlQueryAndMapResults(query);
    }

    @Override
    public PackageNugetSearch getNugetSearcher() {
        return packageNugetSearch;
    }

    @Override
    public Optional<PackageArtifact> findArtifactByExactPathAndName(String repoKey, String path, String name) {
        return findArtifactByExactPathAndName(repoKey, path, name, false);
    }

    @Override
    public Optional<PackageArtifact> findArtifactByExactPathAndName(String repoKey, String path, String name,
            boolean includeFolder) {
        path = StringUtils.isBlank(path) ? null : path.trim();
        name = StringUtils.isBlank(name) ? null : name.trim();
        repoKey = assertAndGetValidRepo(repoKey);
        assertInputNotEmpty(name);
        assertNoAstrix(name);
        assertNoAstrix(path);
        // Creating the query with no extra fields
        AqlApiItem query = AqlApiItem.createWithEmptyResults();
        // Adding the fields we need
        buildIncludeQuery(query);
        // Preparing the query filters
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();
        and.append(AqlApiItem.repo().equal(repoKey));
        filterByPath(and, path);
        filterByName(and, name);

        filterByDepth(and, path);
        and.append(AqlApiItem.type()
                .equal(includeFolder ? AqlItemTypeEnum.any.signature : AqlItemTypeEnum.file.signature));
        // Adding the AND filter to the query
        query.filter(and);
        return executeAqlQueryAndMapResults(query).findFirst();
    }

    @Override
    public Stream<PackageArtifact> findArtifactsByPropKeys(String repoKey, String path, String... propKeys) {
        path = StringUtils.isBlank(path) ? null : path.trim();
        repoKey = assertAndGetValidRepo(repoKey);
        assertNoAstrix(path);
        assertValidPropKeys(propKeys);
        // Creating the query with no extra fields
        AqlApiItem query = AqlApiItem.createWithEmptyResults();
        // Adding the fields we need
        buildIncludeQuery(query);
        // Preparing the query filters
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();
        and.append(AqlApiItem.repo().equal(repoKey));
        filterByPath(and, path);
        filterByDepth(and, path);
        filterByPropKeys(and, propKeys);
        and.append(AqlApiItem.type().equal(AqlItemTypeEnum.file.signature));
        // Adding the AND filter to the query
        query.filter(and);
        return executeAqlQueryAndMapResults(query);
    }

    @Override
    public Stream<PackageArtifact> findArtifactsWithPropsByPropKey(String repoKey, String path, String propKey)
            throws PackageException {
        path = StringUtils.isBlank(path) ? null : path.trim();
        repoKey = assertAndGetValidRepo(repoKey);
        assertNoAstrix(path);
        assertValidPropKeys(new String[] {propKey});
        // Creating the query with no extra fields
        AqlApiItem query = AqlApiItem.createWithEmptyResults();
        // Adding the fields we need
        buildIncludeQuery(query);
        query.include(property().key(), property().value());
        // Preparing the query filters
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();
        and.append(AqlApiItem.repo().equal(repoKey));
        filterByPathEqualOrGreater(and, path);
        filterByDepth(and, path);
        and.append(property().key().equal(propKey));
        and.append(AqlApiItem.type().equal(AqlItemTypeEnum.file.signature));
        // Adding the AND filter to the query
        query.filter(and);
        return populateRowsToSearchResult(query, repoKey).stream();
    }

    @Override
    public Stream<PackageArtifact> findArtifactsWithProps(String repoKey, String path, FilteringProperty packageName,
            List<FilteringProperty> props, boolean includeStatistic) throws PackageException {
        long wholeQueryStart = System.currentTimeMillis();
        path = StringUtils.isBlank(path) ? null : path.trim();
        repoKey = assertAndGetValidRepo(repoKey);
        assertNoAstrix(path);
        Stream<PackageArtifact> stream = findArtifactsWithPropsRT(repoKey, path, packageName, props, includeStatistic);
        log.trace("Whole query took {} ms", System.currentTimeMillis() - wholeQueryStart);
        return stream;
    }

    private Stream<PackageArtifact> findArtifactsWithPropsRT(@NonNull String repoKey, @Nullable String path,
            @Nullable FilteringProperty packageProp, @NonNull List<FilteringProperty> props, boolean includeStatistic)
            throws PackageException {
        path = path != null ? path : "";

        final List<FilteringProperty> propsWithPackageName = new ArrayList<>(props);
        if (nonNull(packageProp)) {
            propsWithPackageName.add(packageProp);
        }

        if (!isPackageExistInRepo(repoKey, propsWithPackageName, path)) {
            return Stream.empty();
        }

        AqlApiItem aqlQuery = createAqlQueryForSearchWithProps(repoKey, path, propsWithPackageName, includeStatistic);
        AqlApiItem aqlQueryWithoutRepo = createAqlQueryForSearchWithProps(null, path, propsWithPackageName,
                includeStatistic);
        String key = constructLocalCacheKey(SEARCH_CACHE_KEY, propsWithPackageName, path);
        List<PackageArtifact> results = ReposItemsThreadLocalCacheService.getAll(key, repoKey,
                limit -> populateRowsToSearchResult(aqlQueryWithoutRepo, repoKey).stream()
                        .toList(),
                ConstantValues.packageHandlerArtifactWithPropsThreadLocalCacheLimit.getInt(),
                () -> populateRowsToSearchResult(aqlQuery, repoKey).stream().toList());
        return results.stream();
    }

    /**
     * This method is used to create a query for artifacts search.
     */
    private AqlApiItem createAqlQueryForSearchWithProps(String repoKey, String path,
            List<FilteringProperty> props, boolean includeStatistic) {
        AqlApiItem aqlQuery = create().distinct(false)
                .include(property().key(), property().value());
        // Preparing the query filters
        if (includeStatistic) {
            aqlQuery.include(statistic().downloads());
        }
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();
        if (nonNull(repoKey)) {
            and.append(AqlApiItem.repo().equal(repoKey));
        }

        filterByPath(and, path);
        filterByDepth(and, path);

        for (FilteringProperty filterProperty : props) {
            and.append(createPropCriteria(filterProperty));
        }

        aqlQuery.filter(and);
        return aqlQuery;
    }

    /**
     * @return A list of AqlApiItem containing only repoKey, those are all the repos where "props" are found.
     */
    private AqlApiItem createAqlForReposContainingProps(String path,
            List<FilteringProperty> props) {
        AqlApiItem aqlQuery = createWithEmptyResults().distinct(true)
                .include(AqlApiProperty.item().repo());
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();

        filterByPath(and, path);
        filterByDepth(and, path);

        for (FilteringProperty filterProperty : props) {
            and.append(createPropCriteria(filterProperty));
        }
        aqlQuery.filter(and);
        return aqlQuery;
    }

    @NonNull
    @Override
    public List<String> findArtifactsByPattern(@NonNull String repoKey, @NonNull String filename, String pathPattern) {
        if (StringUtils.isEmpty(pathPattern) || !pathPattern.contains("*") || !pathPattern.contains("/")) {
            throw new IllegalStateException("Search path is either empty, or doesn't contain '*' or '/'");
        }
        AqlApiItem query = createWithEmptyResults().filter(
                and(
                        repo().equal(repoKey),
                        name().equal(filename),
                        path().matches(pathPattern)

                )
        ).include(name(), repo(), path(), type(), depth());

        return aqlService.executeQueryEager(query).getResults().stream()
                .map(item -> String.join("/", item.getPath(), item.getName())).toList();
    }

    @Override
    public Stream<PackageArtifact> findArtifactsByQuery(String query) {
        log.debug("Executing artifact aql query");
        AqlEagerResult<AqlBaseFullRowImpl> allBlobs = aqlService.executeQueryEager(query);
        log.debug("Artifacts aql query completed");
        return mapResultsToPackageArtifact(allBlobs);
    }

    /**
     * AllBlobs contains one row per property for an artifact.
     * We need to aggregate all properties for each artifact.
     * Since `nodeId` is unique for each artifact, we use it as the key to accumulate all properties associated
     * with that artifact.
     *
     * @param allBlobs AqlEagerResult containing all the artifacts and their properties
     * @return Stream of PackageArtifact
     */
    private Stream<PackageArtifact> mapResultsToPackageArtifact(AqlEagerResult<AqlBaseFullRowImpl> allBlobs) {
        Map<Long, PackageArtifact> searchResults = new LinkedHashMap<>();
        for (AqlBaseFullRowImpl item : allBlobs.getResults()) {
            RepoPath repoPath = AqlUtils.fromAql(item);
            if (authorizationService.canRead(repoPath)) {
                PackageArtifact packageWithProps = searchResults
                        .computeIfAbsent(item.getNodeId(), key -> PackageArtifactMapper.artifact(item));
                if (item.getKey() != null) {
                    packageWithProps.getProperties().put(item.getKey(), item.getValue());
                }
            }
        }
        return searchResults.values().stream();
    }

    @VisibleForTesting
    Collection<PackageArtifact> populateRowsToSearchResult(AqlApiItem aqlQuery, String repoKey)
            throws PackageException {
        FileInfoWithStatisticsItemRow row;
        long queryStart = System.currentTimeMillis();
        Map<Long, PackageArtifact> searchResults = new HashMap<>();
        try (AqlLazyResult<AqlItem> result = aqlService.executeQueryLazy(aqlQuery)) {
            AqlLazyObjectResultStreamer<FileInfoWithStatisticsItemRow> streamer =
                    new AqlLazyObjectResultStreamer<>(result, FileInfoWithStatisticsItemRow.class);
            while ((row = streamer.getRow()) != null) {
                accumulateRowToCollection(row, searchResults);
            }
        } catch (Exception e) {
            log.error("Failed to map AQL results for populateRowsToSearchResult: " + e.getMessage(), e);
            log.debug("Query was: {}", aqlQuery.toNative(2));
            throw new PackageInternalServerError("Failed to execute populateRowsToSearchResult", e);
        }
        long queryEnd = System.currentTimeMillis();
        log.debug("Aql query with reducing properties took: {} ms, Number of elements:{}, Repo:{}",
                (queryEnd - queryStart), searchResults.values().size(), repoKey);

        return searchResults.values();
    }

    private void accumulateRowToCollection(FileInfoWithStatisticsItemRow row,
            Map<Long, PackageArtifact> searchResults) {
        long currentPackageId = row.getItemId();
        PackageArtifact packageWithProps = searchResults
                .computeIfAbsent(currentPackageId, key -> PackageArtifactMapper.artifact(row));
        String propKey = row.getPropKey();
        if (propKey != null) {
            //Add all properties
            String propVal = row.getPropVal();
            packageWithProps.getProperties().put(propKey, propVal);
            if (log.isTraceEnabled()) {
                log.trace("Added property {} : {} to PackageArtifact for path {}", propKey, propVal,
                        packageWithProps.getPath());
            }
        }
    }

    @Override
    public Stream<PackageArtifact> findArtifactsByPropKeyAndVal(PackageSearchContext packageSearchContext,
            Map<String, String> properties) {
        return findArtifactsByPropKeyAndMultiVal(packageSearchContext, Multimaps.forMap(properties));
    }

    @Override
    public Stream<PackageArtifact> findArtifactsByPropKeyAndMultiVal(PackageSearchContext packageSearchContext,
            Multimap<String, String> properties) {

        String repoKey = packageSearchContext.getRepoKey();
        String path =
                StringUtils.isBlank(packageSearchContext.getPath()) ? null : packageSearchContext.getPath().trim();
        repoKey = assertAndGetValidRepo(repoKey);
        assertNoAstrix(path);
        assertValidProperties(properties);
        // Creating the query with no extra fields
        AqlApiItem query = AqlApiItem.createWithEmptyResults();
        // Adding the fields we need
        buildIncludeQuery(query);
        // Preparing the query filters
        AqlBase.AndClause<AqlApiItem> and = AqlBase.and();
        and.append(AqlApiItem.repo().equal(repoKey));
        if (packageSearchContext.isIncludeSubdirectories()) {
            filterByPathEqualOrGreater(and, path);
        } else {
            filterByPath(and, path);
        }
        filterByDepth(and, path);
        filterByProperties(and, properties);
        and.append(AqlApiItem.type().equal(AqlItemTypeEnum.file.signature));
        if (packageSearchContext.getLimit() > PackageSearchContext.UNLIMITED) {
            query.limit(packageSearchContext.getLimit());
        }
        query.offset(packageSearchContext.getOffset());
        // Adding the AND filter to the query
        query.filter(and);
        return executeAqlQueryAndMapResults(query);
    }

    private void filterByProperties(AqlBase.AndClause<AqlApiItem> and, Multimap<String, String> propertiesToMatchMaps) {

        for (String key : propertiesToMatchMaps.keySet()) {
            Collection<String> values =  propertiesToMatchMaps.get(key);
            handlePropertyValues(and, key, values);
        }
    }

    private void handlePropertyValues(AqlBase.AndClause<AqlApiItem> and, String key, Collection<String> values) {

        String value = null;
        if (values != null && values.size() == 1) {
            value = values.iterator().next();
        }
        if (values != null && values.size() > 1) {
            addOrClause(and, key, values);
        } else if (value == null) {
            and.append(property().property(key, AqlComparatorEnum.equals, null));
        } else if (value.equals("*")) {
            throwAstrixIllegalArgumentException();
        } else {
            and.append(property().property(key,
                    value.contains("*") ? AqlComparatorEnum.matches : AqlComparatorEnum.equals, value));
        }
    }

    private void addOrClause(AqlBase.AndClause<AqlApiItem> and, String key, Collection<String> values) {
        AqlBase.OrClause<AqlApiItem> or = AqlBase.or();
        for (String val : values) {
            if (val.equals("*")) {
                throwAstrixIllegalArgumentException();
            }
            or.append(property().property(key,
                    val.contains("*") ? AqlComparatorEnum.matches : AqlComparatorEnum.equals, val));
        }
        and.append(or);
    }

    private void buildIncludeQuery(AqlApiItem query) {
        query.include(AqlApiItem.repo(), AqlApiItem.itemId(), AqlApiItem.path(), AqlApiItem.name(), AqlApiItem.type(),
                AqlApiItem.sha1Actual(), AqlApiItem.md5Actual(), AqlApiItem.sha2(), AqlApiItem.size(),
                AqlApiItem.created(), AqlApiItem.modified(), AqlApiItem.createdBy(), AqlApiItem.modifiedBy(),
                AqlApiItem.updated());
    }

    private void assertNoAstrix(String param) {
        if (StringUtils.isNotEmpty(param) && param.contains("*")) {
            throw new IllegalArgumentException(PARAMETER + param + "' cannot include '*'");
        }
    }

    /**
     * This function throw an exception in case of invalid repoKey.
     * In case of remote repository - we need to search in the cache repo if exists
     * The function returns the valid repoKey;
     */
    private String assertAndGetValidRepo(String repoKey) {
        if (StringUtils.isEmpty(repoKey)) {
            throw new IllegalArgumentException("RepoKey cannot be empty");
        }
        if (repoService.isRemote(repoKey)) {
            try {
                return repoService.getRemoteCacheRepoKey(repoKey);
            } catch (PackageNotFoundException e) {
                log.warn("Cannot search on remote repository '{}' - cached repository not exists", repoKey);
                log.warn("Cannot search on remote repository '{}':", repoKey, e);
            }
        }
        return repoKey;
    }

    private void assertInputNotEmpty(String input) {
        if (StringUtils.isEmpty(input)) {
            throw new IllegalArgumentException("Input cannot be empty");
        }
    }

    private void assertValidPropKeys(String[] propKeys) {
        if (propKeys == null || propKeys.length == 0) {
            throw new IllegalArgumentException("No propKeys has been provided");
        }
        Arrays.stream(propKeys).iterator().forEachRemaining(extension -> {
            if (StringUtils.isEmpty(extension)) {
                throw new IllegalArgumentException("PropKey cannot be empty");
            }
            assertNoAstrix(extension);
        });
    }

    private void assertValidProperties(Multimap<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("No properties maps have been provided");
        }

        properties.asMap().forEach((k, v) -> {
            if (StringUtils.isEmpty(k)) {
                throw new IllegalArgumentException("PropKey cannot be empty");
            }
            v.forEach(value ->assertNoAstrix(k));
        });

    }

    private void assertValidExtensions(String... extensions) {
        if (extensions == null || extensions.length == 0) {
            throw new IllegalArgumentException("No extension has been provided");
        }
        Arrays.stream(extensions).iterator().forEachRemaining(extension -> {
            if (StringUtils.isEmpty(extension)) {
                throw new IllegalArgumentException("Extension cannot be empty");
            }
            assertNoAstrix(extension);
        });
    }

    private void filterByExtension(AqlBase.AndClause<AqlApiItem> and, String[] extensions) {
        AqlBase.OrClause<AqlApiItem> or = AqlBase.or();
        Arrays.stream(extensions).iterator().forEachRemaining(extension -> {
                    extension = StringUtils.removeStart(extension, ".");
                    or.append(AqlApiItem.name().matches("*." + extension));
                }
        );
        and.append(or);
    }

    /**
     * This method adds filters to the query in case filename has wildcards.
     * Take into consideration DB performance.
     *
     * @param fileName the file name to search for
     * @param and      the AND clause from the original AQL query
     */
    private void filterByName(AqlBase.AndClause<AqlApiItem> and, String fileName) {
        if (StringUtils.isNotBlank(fileName) && !fileName.equals("*")) {
            if (fileName.contains("*")) {
                and.append(AqlApiItem.name().matches(fileName));
            } else {
                and.append(AqlApiItem.name().equal(fileName));
            }
        }
    }

    private void filterByPath(AqlBase.AndClause<AqlApiItem> and, String path) {
        if (StringUtils.isNotBlank(path) && !path.equals("*")) {
            if (path.contains("*")) {
                and.append(AqlApiItem.path().matches(path));
            } else {
                and.append(AqlApiItem.path().equal(path));
            }
        }
    }

    private void filterByPathEqualOrGreater(AqlBase.AndClause<AqlApiItem> and, String path) {
        if (StringUtils.isEmpty(path) || StringUtils.equals(path, "/")) {
            return;
        }
        path = PathUtils.trimTrailingSlashes(path);
        AqlBase.OrClause<AqlApiItem> or = AqlBase.or();
        or.append(AqlApiItem.path().equal(path));
        or.append(AqlApiItem.path().matches(path + "/*"));
        and.append(or);
    }

    private void filterByPropKeys(AqlBase.AndClause<AqlApiItem> and, String... propKeys) {
        Arrays.stream(propKeys).iterator().forEachRemaining(propKey -> and.append(property().key().equal(propKey)));
    }

    /**
     * This method is adding a filter to the query for the 'nodes.depth' field.
     * We calculate the level of the file within the tree of the given path:
     * meaning for a given path we count the number of '/' in it,
     * then adding +1 in order to search only for the given folder and not its parent.
     * We search by 'depth >= level+1' in order to include any file in subdirectories as well.
     *
     * @param path the path given to search in
     * @param and  the AND clause from the original AQL query
     */
    private void filterByDepth(AqlBase.AndClause and, String path) {
        int length = getPathElements(path).length;
        and.append(AqlApiItem.depth().greaterEquals(length + 1));
        log.debug("Added AND clause to the artifact search query: AND depth>={}", length + 1);
    }

    /**
     * This method is adding a filter to the query for the 'nodes.updated' field.
     * We search by 'updated < lastUpdatesBefore' in order to include all the artifacts that was updated
     * before the provided date.
     * Upd
     */
    private void filterByLastUpdatedBeforeIfNeeded(AqlBase.AndClause<AqlApiItem> and, long lastUpdatedBefore) {
        if (lastUpdatedBefore > 0) {
            and.append(AqlApiItem.updated().lessEquals(lastUpdatedBefore));
            log.debug("Added AND clause to the artifact search query: AND lastUpdated>={}", lastUpdatedBefore);
        }
    }

    /**
     * Returns the path elements of the input path.
     * <pre>
     * getPathElements("/a/b/c") = [a, b, c]
     * getPathElements("a/b/c") = [a, b, c]
     * getPathElements("a/b/c/") = [a, b, c]
     * getPathElements("a") = [a]
     * getPathElements("") = []
     * </pre>
     *
     * @param path The path to parse (can be absolute or relative)
     * @return The path's path elements
     */
    public static String[] getPathElements(String path) {
        if (StringUtils.isEmpty(path)) {
            return new String[0];
        }
        if (path.startsWith("/")) {
            // we don't want to return empty string as a path element so remove the leading slash
            path = path.substring(1);
        }
        return path.split("/");
    }

    /**
     * Execute the query and returns a stream of PackageArtifacts, will omit any properties the artifacts holds
     */
    private Stream<PackageArtifact> executeAqlQueryAndMapResults(AqlApiItem query) {
        log.debug("Executing artifact search query");
        AqlEagerResult<AqlItem> allBlobs = aqlService.executeQueryEager(query);
        log.debug("Artifacts search completed");
        return allBlobs.getResults().stream()
                .map(AqlConverts.toItemInfo)
                .filter(file -> authorizationService.canRead(file.getRepoPath()))
                .map(PackageArtifactMapper::artifact);
    }

    private AqlBase.PropertyCriteriaClause<AqlApiItem> createPropCriteria(FilteringProperty filterProperty) {
        // Map the requested match mode to an AQL comparator. This layer is DB-agnostic — callers decide
        // the mode (e.g. NuGet picks VALUE_CASE_INSENSITIVE on non-Postgres to keep prop_key sargable).
        return switch (filterProperty.mode()) {
            case CASE_SENSITIVE -> property().property(filterProperty.propertyName(),
                    AqlComparatorEnum.equals, filterProperty.propertyValue());
            case CASE_INSENSITIVE -> property().property(filterProperty.propertyName(),
                    AqlComparatorEnum.equalsIgnoreCase, filterProperty.propertyValue().toLowerCase());
            case VALUE_CASE_INSENSITIVE -> property().property(filterProperty.propertyName(),
                    AqlComparatorEnum.equalsValueIgnoreCase, filterProperty.propertyValue().toLowerCase());
        };
    }

    /**
     * @return if a package exist in a repo, if not in ReposItemsThreadLocalCache context will always return true,
     * the results are cached to be used for all the locals of a virtual repo
     */
    private boolean isPackageExistInRepo(String repoKey, List<FilteringProperty> props, String path) {
        AqlApiItem aqlQuery = createAqlForReposContainingProps(path, props);
        String key = constructLocalCacheKey(SEARCH_REPOS_CACHE_KEY, props, path);
        return ReposItemsThreadLocalCacheService.exists(key, repoKey,
                limit -> aqlService.executeQueryEager(aqlQuery).getResults(),
                ConstantValues.packageHandlerSearchThreadLocalCacheReposLimit.getInt(),
                () -> true);
    }

    private static String constructLocalCacheKey(String baseKey, List<FilteringProperty> props, String path) {
        StringBuilder result = new StringBuilder();
        result.append(baseKey)
                .append(path)
                .append(props.stream()
                        .map(FilteringProperty::propertyValue)
                        .sorted()
                        .collect(joining()));
        return result.toString();

    }

    private static void addOffsetAndLimit(int offset, int limit, AqlApiItem query) {
        if (offset > 0) {
            query.offset(offset);
        }
        if (limit > UNLIMITED) {
            query.limit(limit);
        }
    }
    private void throwAstrixIllegalArgumentException() {
        throw new IllegalArgumentException(PARAMETER + "*' is not a legal property value");
    }
}
