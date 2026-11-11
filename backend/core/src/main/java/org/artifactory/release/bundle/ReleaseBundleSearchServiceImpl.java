package org.artifactory.release.bundle;

import com.google.common.collect.Lists;
import jakarta.ws.rs.InternalServerErrorException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.release.bundle.ReleaseBundleSearchFilter;
import org.artifactory.api.release.bundle.ReleaseBundleSearchService;
import org.artifactory.api.rest.distribution.bundle.models.BundleVersion;
import org.artifactory.api.rest.distribution.bundle.models.BundleVersionsResponse;
import org.artifactory.api.rest.distribution.bundle.models.BundlesResponse;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiReleaseBundle;
import org.artifactory.aql.api.internal.AqlApiDynamicFieldsDomains.AqlApiComparator;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.result.rows.AqlReleaseBundle;
import org.artifactory.bundle.BundleType;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao;
import org.artifactory.storage.db.bundle.model.DBArtifactsBundle;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.artifactory.aql.api.domain.sensitive.AqlApiReleaseBundle.CriteriaClause;
import static org.artifactory.aql.api.domain.sensitive.AqlApiReleaseBundle.created;
import static org.artifactory.aql.api.domain.sensitive.AqlApiReleaseBundle.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiReleaseBundle.version;

/**
 * @author Lior Gur
 */
@Slf4j
@Service
@AllArgsConstructor
public class ReleaseBundleSearchServiceImpl implements ReleaseBundleSearchService {

    private static final String FETCH_BUNDLES_ERR = "Failed to fetch bundles ";

    private final ArtifactBundlesDao artifactsBundleDao;
    private final AuthorizationService authorizationService;
    private final SecurityService securityService;
    private final AqlService aqlService;

    public BundlesResponse getFilteredBundles(ReleaseBundleSearchFilter filter) {
        List<DBArtifactsBundle> artifactsBundles = fetchDBArtifactsBundles(filter);
        log.debug("Fetched {} bundles.", artifactsBundles.size());
        BundlesResponse bundlesResponse = new BundlesResponse();
        artifactsBundles.stream()
                .limit(filter.getLimit())
                .forEach(bundle -> {
                    BundleVersion bundleVersion = populateBundleVersion(bundle);
                    bundlesResponse.add(bundle.getName(), bundleVersion);
                });
        return bundlesResponse;
    }

    private List<DBArtifactsBundle> filterByPermissions(ReleaseBundleSearchFilter filter,
            List<DBArtifactsBundle> dbFilteredBundles) {
        return dbFilteredBundles.stream()
                .filter(bundle -> authorizationService.canRead(
                        RepoPathFactory.create(bundle.getStoringRepo(), bundle.getName() + "/" + bundle.getVersion())))
                .limit(filter.getLimit())
                .collect(Collectors.toList());
    }

    private List<DBArtifactsBundle> fetchBundlesFromDb(ReleaseBundleSearchFilter filter,
            long offset) throws SQLException {
        filter.setOffset(offset);
        return artifactsBundleDao.getFilteredBundlesLastVersion(filter);
    }

    @Override
    public BundleVersionsResponse getBundleVersions(String bundleName, BundleType bundleType) {
        BundleVersionsResponse bundlesResponse = null;
        try {
            List<DBArtifactsBundle> artifactsBundles = artifactsBundleDao.getArtifactsBundles(bundleName, bundleType);
            bundlesResponse = populateBundleVersionsResponse(artifactsBundles);
        } catch (SQLException e) {
            log.error(FETCH_BUNDLES_ERR, e);
        }
        return bundlesResponse;
    }

    public BundleVersionsResponse getFilterBundleVersions(ReleaseBundleSearchFilter filter) {
        List<AqlReleaseBundle> bundleVersions = Lists.newArrayList();
        if (securityService.isAdmin()) {
            // Admin: single query, no permission filtering
            filter.setDaoLimit(filter.getLimit());
            bundleVersions = createAqlFilterBundleVersions(filter);
        } else {
            // Non-admin: paginated queries with per-item permission filtering
            long currentOffset = filter.getOffset();
            while (bundleVersions.size() < filter.getLimit()) {
                filter.setOffset(currentOffset);
                List<AqlReleaseBundle> aqlBundleVersionsBatch = createAqlFilterBundleVersions(filter);
                List<AqlReleaseBundle> permissionFilterVersions =
                        filterVersionsByPermissions(aqlBundleVersionsBatch);
                bundleVersions.addAll(permissionFilterVersions);
                if (aqlBundleVersionsBatch.size() < filter.getDaoLimit()) {
                    break;
                }
                currentOffset += filter.getDaoLimit();
            }
        }
        return populateBundleVersionsResponseAql(
                bundleVersions.stream().limit(filter.getLimit()).collect(Collectors.toList()));
    }

    private List<AqlReleaseBundle> filterVersionsByPermissions(List<AqlReleaseBundle> aqlBundleVersionsBatch) {
        return aqlBundleVersionsBatch.stream()
                .filter(bundle -> authorizationService.canRead(RepoPathFactory.create(bundle.getStoringRepo(),
                        bundle.getReleaseName() + "/" + bundle.getReleaseVersion())))
                .toList();
    }

    private List<AqlReleaseBundle> createAqlFilterBundleVersions(ReleaseBundleSearchFilter filter) {
        CriteriaClause<AqlApiReleaseBundle> nameEquals = null;
        AqlBase.OrClause<AqlApiReleaseBundle> versions = null;
        CriteriaClause<AqlApiReleaseBundle> before = null;
        CriteriaClause<AqlApiReleaseBundle> after = null;
        if (StringUtils.isNotBlank(filter.getName())) {
            nameEquals = name().equal(filter.getName());
        }
        if (filter.getVersions() != null) {
            versions = AqlBase.or();
            for (String version : filter.getVersions()) {
                versions.append(version().equal(version));
            }
        }
        if (filter.getAfter() != 0) {
            after = created().greaterEquals(filter.getAfter());
        }
        if (filter.getBefore() != 0) {
            before = created().lessEquals(filter.getBefore());
        }
        AqlApiReleaseBundle aql = AqlApiReleaseBundle.createWithEmptyResults()
                .include(name(), version(), created(), AqlApiReleaseBundle.status(),
                        AqlApiReleaseBundle.storingRepo(), AqlApiReleaseBundle.keep())
                .filter(AqlBase.and(
                        AqlApiReleaseBundle.bundleType().equal(filter.getBundleType().name()),
                        nameEquals,
                        after,
                        before,
                        versions))
                .addSortElement(sortResultsBy(filter))
                .desc()
                .limit(filter.getDaoLimit())
                .offset((int) filter.getOffset());

        if (filter.getDirection().equals("asc")) {
            aql.asc();
        }
        return aqlService.executeQueryEager(aql).getResults();
    }

    private static AqlApiComparator<AqlApiReleaseBundle> sortResultsBy(ReleaseBundleSearchFilter filter) {

        return switch (filter.getOrderBy()) {
            case "name", "version" -> version();
            default -> created();
        };
    }

    private BundleVersionsResponse populateBundleVersionsResponse(List<DBArtifactsBundle> artifactsBundles) {
        BundleVersionsResponse bundleVersionsResponse = new BundleVersionsResponse();
        List<BundleVersion> versions = artifactsBundles.stream()
                .filter(bundle ->
                        authorizationService.canRead(
                                getBundleRepoPath(bundle.getStoringRepo(), bundle.getName(), bundle.getVersion())))
                .map(this::populateBundleVersion)
                .collect(Collectors.toList());
        bundleVersionsResponse.setVersions(versions);
        return bundleVersionsResponse;
    }

    private BundleVersion populateBundleVersion(DBArtifactsBundle bundle) {
        BundleVersion bundleVersion = new BundleVersion();
        if (bundle.getStatus() != null) {
            bundleVersion.setStatus(bundle.getStatus().name());
        }
        bundleVersion.setVersion(bundle.getVersion());
        bundleVersion.setCreated(ISODateTimeFormat.dateTime().withZoneUTC().print(bundle.getDateCreated().getMillis()));
        bundleVersion.setStoringRepo(bundle.getStoringRepo());
        bundleVersion.setKeep(bundle.getKeep());
        return bundleVersion;
    }

    private BundleVersionsResponse populateBundleVersionsResponseAql(List<AqlReleaseBundle> artifactsBundles) {
        BundleVersionsResponse bundleVersionsResponse = new BundleVersionsResponse();
        artifactsBundles.forEach(bundle -> {
            BundleVersion bundleVersion = populateBundleVersionAql(bundle);
            bundleVersionsResponse.add(bundleVersion);
        });
        return bundleVersionsResponse;
    }

    private BundleVersion populateBundleVersionAql(AqlReleaseBundle bundle) {
        BundleVersion bundleVersion = new BundleVersion();
        if (bundle.getReleaseStatus() != null) {
            bundleVersion.setStatus(bundle.getReleaseStatus());
        }
        bundleVersion.setVersion(bundle.getReleaseVersion());
        bundleVersion
                .setCreated(ISODateTimeFormat.dateTime().withZoneUTC().print(bundle.getReleaseCreated().getTime()));
        bundleVersion.setStoringRepo(bundle.getStoringRepo());
        bundleVersion.setKeep(bundle.getKeep() == 1);

        return bundleVersion;
    }


    public BundlesResponse getBundles(BundleType bundleType) {
        BundlesResponse bundlesResponse = null;
        try {
            List<DBArtifactsBundle> bundles = artifactsBundleDao.getAllArtifactsBundles(bundleType);
            bundlesResponse = populateBundlesResponseAndFilterByPermissions(bundles);
        } catch (SQLException e) {
            log.error(FETCH_BUNDLES_ERR, e);
        }
        return bundlesResponse;
    }

    private BundlesResponse populateBundlesResponseAndFilterByPermissions(List<DBArtifactsBundle> artifactsBundles) {
        BundlesResponse bundlesResponse = new BundlesResponse();
        artifactsBundles.stream()
                .filter(bundle ->
                        authorizationService.canRead(
                                getBundleRepoPath(bundle.getStoringRepo(), bundle.getName(), bundle.getVersion())))
                .forEach(bundle -> {
                    BundleVersion bundleVersion = populateBundleVersion(bundle);
                    bundlesResponse.add(bundle.getName(), bundleVersion);
                });
        return bundlesResponse;
    }

    private RepoPath getBundleRepoPath(String repo, String name, String version) {
        return RepoPathFactory.create(repo, name + "/" + version);
    }

    private List<DBArtifactsBundle> fetchDBArtifactsBundles(ReleaseBundleSearchFilter filter) {
        try {
            if (securityService.isAdmin()) {
                log.debug("Fetching bundles for admin user.");
                filter.setDaoLimit(filter.getLimit());
                return artifactsBundleDao.getFilteredBundlesLastVersion(filter);
            }
            boolean isAllowedOnlyForAdmin = ConstantValues.releaseBundleReceivedAdminOnly.getBoolean();
            log.debug("user is not admin and bundles' fetching for non-admin users is {}.",
                    isAllowedOnlyForAdmin ? "disabled" : "enabled");
            return isAllowedOnlyForAdmin ? new ArrayList<>() : fetchDBArtifactsBundlesForNonAdminUser(filter);
        } catch (SQLException e) {
            log.error(FETCH_BUNDLES_ERR, e);
            throw new InternalServerErrorException(e);
        }
    }

    private List<DBArtifactsBundle> fetchDBArtifactsBundlesForNonAdminUser(ReleaseBundleSearchFilter filter)
            throws SQLException {
        log.debug("Fetching permitted bundles for non admin user.");
        List<DBArtifactsBundle> artifactsBundles = new ArrayList<>();
        long offset = filter.getOffset();
        long totalDBBundles = artifactsBundleDao.getFilteredBundlesCount(filter);
        while (artifactsBundles.size() < filter.getLimit() && (offset < totalDBBundles)) {
            List<DBArtifactsBundle> dbBundles = fetchBundlesFromDb(filter, offset);
            offset += filter.getDaoLimit();
            List<DBArtifactsBundle> permissionFilteredBundles = filterByPermissions(filter, dbBundles);
            artifactsBundles.addAll(permissionFilteredBundles);
        }
        return artifactsBundles;
    }
}
