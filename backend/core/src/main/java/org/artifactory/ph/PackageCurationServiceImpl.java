package org.artifactory.ph;

import com.jfrog.ph.acl.model.curation.CurationPackageForRequest;
import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import com.jfrog.ph.acl.model.curation.CurationVersionForRequest;
import com.jfrog.ph.acl.service.PackageCurationService;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.curation.CurationService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.repo.PackageType;
import org.artifactory.model.RemoteRepoConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author avivbr
 */
@Slf4j
public class PackageCurationServiceImpl implements PackageCurationService {

    private final CurationService curationService;
    private final RepositoryService repositoryService;

    public PackageCurationServiceImpl(CurationService curationService, RepositoryService repositoryService) {
        this.curationService = curationService;
        this.repositoryService = repositoryService;
    }

    @Override
    public boolean canCallFilterBlockedVersions(@Nonnull String repoName) {
        if (!curationService.isEnabled()) {
            return false;
        }
        RemoteRepoConfig remoteRepoConfig = repositoryService.getRemoteRepoConfigByKey(repoName);
        if (remoteRepoConfig == null) {
            return false;
        }
        PackageType packageType = PackageRepoTypeMapper.packageType(remoteRepoConfig);
        // Excluded repo types (e.g. Distribution) have no PackageType mapping; CVS filtering does not apply.
        if (packageType == null) {
            return false;
        }
        if (!curationService.isCurationEnabledForRepo(remoteRepoConfig)) {
            return false;
        }
        return curationService.isIndexVersionsCompliantEnabled()
                || curationService.getForceCVSPackages().contains(packageType);
    }

    @Override
    @Nullable
    public CurationResponse filterBlockedVersions(@Nonnull CurationRequest curationRequest,
            @Nonnull Comparator<CurationVersionForRequest> versionComparator) {
        return filterBlockedVersions(sortRequestVersions(curationRequest, versionComparator));
    }

    @Override
    @Nullable
    public CurationResponse filterBlockedVersions(@Nonnull CurationRequest curationRequest) {
        CurationResponse response = curationService.filterBlockedVersions(curationRequest);
        if (log.isDebugEnabled()) {
            log.debug("Blocked packages/versions: {}", toDebugDescription(response));
        }
        return response;
    }

    private static CurationRequest sortRequestVersions(CurationRequest curationRequest,
            Comparator<CurationVersionForRequest> versionComparator) {
        List<CurationPackageForRequest> packages = curationRequest.packages();
        if (packages == null || packages.isEmpty()) {
            return curationRequest;
        }
        List<CurationPackageForRequest> sortedPackages = packages.stream()
                .map(pkg -> sortPackageVersions(pkg, versionComparator))
                .toList();
        return new CurationRequest(curationRequest.repoName(), curationRequest.packageType(), sortedPackages,
                curationRequest.user(), curationRequest.passThrough());
    }

    private static CurationPackageForRequest sortPackageVersions(CurationPackageForRequest pkg,
            Comparator<CurationVersionForRequest> versionComparator) {
        List<CurationVersionForRequest> versions = pkg.versions();
        if (versions == null || versions.isEmpty()) {
            return pkg;
        }
        List<CurationVersionForRequest> sortedVersions = new ArrayList<>(versions);
        sortVersions(sortedVersions, versionComparator);
        return new CurationPackageForRequest(pkg.packageName(), sortedVersions, pkg.tag(), pkg.acceptedContentTypes(),
                pkg.contentType());
    }

    private static void sortVersions(List<CurationVersionForRequest> versions,
            Comparator<CurationVersionForRequest> versionComparator) {
        if (areThereNoNullReleaseDates(versions)) {
            versions.sort((v1, v2) -> v2.releaseDate().compareTo(v1.releaseDate()));
        } else {
            versions.sort(versionComparator);
        }
    }

    private static String toDebugDescription(CurationResponse response) {
        return response == null ? "none" : response.debugDescription();
    }

    private static boolean areThereNoNullReleaseDates(List<CurationVersionForRequest> versions) {
        return versions.stream().noneMatch(versionInfo -> versionInfo.releaseDate() == null);
    }
}
