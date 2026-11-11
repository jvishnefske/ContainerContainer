package org.artifactory.maven.curation;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.ph.acl.exception.PackageCurationException;
import com.jfrog.ph.acl.model.curation.CurationPackageForRequest;
import com.jfrog.ph.acl.model.curation.CurationPackageForResponse;
import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import com.jfrog.ph.acl.model.curation.CurationVersionForRequest;
import com.jfrog.ph.acl.service.PackageArtifactService;
import com.jfrog.ph.acl.service.PackageSecurityService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.artifactory.addon.curation.packages.PackageVersionCurationController;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.repo.PackageType;
import org.artifactory.maven.MavenModelUtils;
import org.artifactory.maven.MavenMetadataReleaseDatesFetcher;
import org.artifactory.mime.MavenNaming;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller for handling Maven package version curation operations.
 * This class implements the PackageVersionCurationController interface to provide
 * Maven-specific curation functionality including metadata processing, version filtering,
 * and release date fetching.
 *
 * @author Ron Malka
 */

@Slf4j
public class GAVCPackageVersionCurationController
        implements PackageVersionCurationController<GAVCPackagesCurationMetadataWrapper> {

    private final PackageSecurityService packageSecurityService;
    private final MavenMetadataReleaseDatesFetcher mavenMetadataReleaseDatesFetcher;
    private final PackageType curationPackageType;

    public GAVCPackageVersionCurationController(PackageSecurityService packageSecurityService,
            PackageArtifactService packageArtifactService, PackageType curationPackageType) {
        this.packageSecurityService = packageSecurityService;
        this.mavenMetadataReleaseDatesFetcher = new MavenMetadataReleaseDatesFetcher(packageArtifactService);
        this.curationPackageType = curationPackageType;
    }

    /**
     * Creates curation request from Maven metadata.
     *
     * @param metadataWrapper Maven metadata wrapper
     * @param repoKey         repository key
     * @param remoteUrl       remote URL
     * @return curation request
     * @throws PackageCurationException if processing fails
     */
    @Override
    public CurationRequest createMetadataCurationRequest(GAVCPackagesCurationMetadataWrapper metadataWrapper,
            String repoKey, String remoteUrl) throws PackageCurationException {
        Metadata metadata = metadataWrapper.getMetadata();
        verifyMetadata(metadata);
        String packageName = metadata.getGroupId() + ":" + metadata.getArtifactId();
        Set<String> versionsSet = new HashSet<>(metadata.getVersioning().getVersions());
        Map<String, Instant> versionsDatesMap = getMavenMetadataDatesInfo(repoKey, remoteUrl, versionsSet);
        Map<String, MavenVersionDates> versionsDates = getMavenVersionDates(metadata, versionsDatesMap, packageName);
        List<MavenVersionDates> versionsDatesList = new ArrayList<>(versionsDates.values());
        versionsDatesList.sort(new MavenPackageVersionCurationComparator());
        String url = MavenModelUtils.removeMetadataNameFromPath(remoteUrl);

        List<CurationVersionForRequest> curatedVersions = shouldHandleLatestOrReleaseTags(metadata)
                ? handleLatestAndReleaseTags(metadata, versionsDatesList, versionsDates, url)
                : handleVersionTags(metadata, versionsDatesList, versionsDates, url);

        return new CurationRequest(
                repoKey,
                curationPackageType,
                Collections.singletonList(new CurationPackageForRequest(packageName, curatedVersions)),
                packageSecurityService.getCurrentUserName());
    }

    private List<CurationVersionForRequest> handleVersionTags(Metadata metadata,
            List<MavenVersionDates> versionsDatesList, Map<String, MavenVersionDates> versionsDates, String url) {
        String versionName = metadata.getVersion();
        List<CurationVersionForRequest> curatedVersions = versionsDatesList.stream()
                .filter(versionDate -> !versionDate.version().equals(versionName))
                .map(versionDate -> new CurationVersionForRequest(
                        versionDate.version(),
                        MavenModelUtils.getDownloadUrlForPomFile(metadata.getArtifactId(), versionDate.version(), url),
                        versionDate.releaseDate()))
                .collect(Collectors.toCollection(LinkedList::new));

        // Add the latest version to the start of the list
        curatedVersions.addFirst(new CurationVersionForRequest(
                versionName,
                MavenModelUtils.getDownloadUrlForPomFile(metadata.getArtifactId(), versionName, url),
                versionsDates.get(versionName).releaseDate()));

        return curatedVersions;
    }

    private List<CurationVersionForRequest> handleLatestAndReleaseTags(Metadata metadata,
            List<MavenVersionDates> versionsDatesList, Map<String, MavenVersionDates> versionsDates, String url) {
        String latestVersion = metadata.getVersioning().getLatest();
        String releaseVersion = metadata.getVersioning().getRelease();
        List<CurationVersionForRequest> curatedVersions = versionsDatesList.stream()
                .filter(versionDate -> !isLatestOrReleaseVersion(versionDate, latestVersion, releaseVersion))
                .map(versionDate -> new CurationVersionForRequest(
                        versionDate.version(),
                        MavenModelUtils.getDownloadUrlForPomFile(metadata.getArtifactId(), versionDate.version(), url),
                        versionDate.releaseDate()))
                .collect(Collectors.toCollection(LinkedList::new));

        if (!latestVersion.equals(releaseVersion)) {
            // Add the release version if it's different from latest
            curatedVersions.addFirst(new CurationVersionForRequest(
                    releaseVersion,
                    MavenModelUtils.getDownloadUrlForPomFile(metadata.getArtifactId(), releaseVersion, url),
                    versionsDates.get(releaseVersion).releaseDate()));
        }

        // Add the latest version to the start of the list
        curatedVersions.addFirst(new CurationVersionForRequest(
                latestVersion,
                MavenModelUtils.getDownloadUrlForPomFile(metadata.getArtifactId(), latestVersion, url),
                versionsDates.get(latestVersion).releaseDate()));

        return curatedVersions;
    }

    private boolean shouldHandleLatestOrReleaseTags(Metadata metadata) {
        String latestVersion = metadata.getVersioning().getLatest();
        String releaseVersion = metadata.getVersioning().getRelease();
        return StringUtils.isNotBlank(latestVersion) || StringUtils.isNotBlank(releaseVersion);
    }

    private boolean isLatestOrReleaseVersion(MavenVersionDates versionDate, String latestVersion,
            String releaseVersion) {
        return versionDate.version().equals(latestVersion) || versionDate.version().equals(releaseVersion);
    }

    /**
     * Returns filtered metadata with blocked versions removed.
     *
     * @param curationResponse      curation response
     * @param originMetadataWrapper original metadata
     * @return filtered metadata wrapper
     * @throws PackageCurationException if the curation response or metadata are invalid
     */
    @Override
    public GAVCPackagesCurationMetadataWrapper getCuratedMetadataModel(CurationResponse curationResponse,
            GAVCPackagesCurationMetadataWrapper originMetadataWrapper) throws PackageCurationException {
        Metadata originMetadata = originMetadataWrapper.getMetadata();
        verifyMetadata(originMetadata);
        CurationPackageForResponse matchingPackage = getMatchingCurationPackage(curationResponse, originMetadata);
        List<String> blockedVersions = matchingPackage.versions();
        // We use LinkedHashSet to maintain the order of versions from the original metadata
        Set<String> originalVersions = new LinkedHashSet<>(originMetadata.getVersioning().getVersions());
        originalVersions.removeIf(blockedVersions::contains);
        if (originalVersions.isEmpty()) {
            log.debug("All versions are blocked for package: {}, setting versioning to the original versions",
                    matchingPackage.packageName());
        } else {
            List<String> filteredVersions = new ArrayList<>(originalVersions);
            Versioning versioning = getCuratedVersioning(originMetadata, filteredVersions);
            originMetadata.setVersioning(versioning);
            // If latest or release tags are not present, we should update the version tag
            if (!shouldHandleLatestOrReleaseTags(originMetadata)) {
                logIfVersionTagChanged(originMetadata, filteredVersions);
                originMetadata.setVersion(filteredVersions.getLast());
            }
        }
        return new GAVCPackagesCurationMetadataWrapper(originMetadata);
    }


    /**
     * Parses Maven metadata from input stream.
     *
     * @param originMetadataStream metadata input stream
     * @return metadata wrapper
     * @throws PackageCurationException if parsing fails
     */
    @Override
    public GAVCPackagesCurationMetadataWrapper getMetadataModelOrigin(InputStream originMetadataStream)
            throws PackageCurationException {
        try {
            Metadata metadata = MavenModelUtils.toMavenMetadata(originMetadataStream);
            return new GAVCPackagesCurationMetadataWrapper(metadata);
        } catch (IOException e) {
            throw new PackageCurationException("Failed to read Maven metadata from the provided InputStream");
        }
    }

    /**
     * Converts metadata wrapper to input stream.
     *
     * @param metadataWrapper metadata wrapper
     * @return input stream
     * @throws PackageCurationException if conversion fails
     */
    @Override
    public InputStream toInputStream(GAVCPackagesCurationMetadataWrapper metadataWrapper)
            throws PackageCurationException {
        try {
            Metadata metadata = metadataWrapper.getMetadata();
            return new ByteArrayInputStream(MavenModelUtils.mavenMetadataToString(metadata).getBytes());
        } catch (Exception e) {
            log.debug("Error converting metadata to InputStream", e);
            throw new PackageCurationException("Failed to convert Maven metadata to InputStream");
        }
    }

    /**
     * Checks if path should be curated by checking if it's Maven metadata and if the feature is enabled.
     * See {@link #isCurationComplaintVersionEnabledForPackageType()} for how flags map to {@link PackageType}.
     *
     * @param path file path
     * @return true if should be curated
     */
    @Override
    public boolean shouldCheckForCuration(String path) {
        return MavenNaming.isMavenMetadata(path) && isCurationComplaintVersionEnabledForPackageType();
    }

    /**
     * Whether compliant-version curation is enabled for this controller's {@link PackageType} (fixed at
     * construction), via
     * Artifactory system properties on {@link ConstantValues} (property keys use the historical {@code complaint}
     * spelling).
     * <p>
     * Mapping:
     * <ul>
     *   <li>{@link PackageType#MAVEN} &rarr; {@link ConstantValues#mavenCurationComplaintVersionEnabled}
     *       ({@code maven.curation.complaint.version.enabled})</li>
     *   <li>{@link PackageType#GRADLE} &rarr; {@link ConstantValues#gradleCurationComplaintVersionEnabled}
     *       ({@code gradle.curation.complaint.version.enabled})</li>
     *   <li>{@link PackageType#SBT} &rarr; {@link ConstantValues#sbtCurationComplaintVersionEnabled}
     *       ({@code sbt.curation.complaint.version.enabled})</li>
     *   <li>any other {@link PackageType} &rarr; {@code false} (no dedicated flag branch; extend the
     *       {@code switch} if a new layout needs its own toggle)</li>
     * </ul>
     * Used only after {@link #shouldCheckForCuration(String)} has already required a {@code maven-metadata.xml} path
     * ({@link MavenNaming#isMavenMetadata(String)}).
     */
    private boolean isCurationComplaintVersionEnabledForPackageType() {
        return switch (curationPackageType) {
            case MAVEN -> ConstantValues.mavenCurationComplaintVersionEnabled.getBoolean();
            case GRADLE -> ConstantValues.gradleCurationComplaintVersionEnabled.getBoolean();
            case SBT -> ConstantValues.sbtCurationComplaintVersionEnabled.getBoolean();
            default -> false;
        };
    }

    @VisibleForTesting
    void verifyMetadata(Metadata metadata) throws PackageCurationException {
        try {
            MavenModelUtils.verifyMetadata(metadata);
        } catch (IllegalArgumentException e) {
            throw new PackageCurationException("Invalid Maven metadata: " + e.getMessage());
        }
    }

    private Map<String, MavenVersionDates> getMavenVersionDates(Metadata metadata,
            Map<String, Instant> versionsDatesMap, String packageName) {
        Map<String, MavenVersionDates> versionsDates = new HashMap<>();
        for (String version : metadata.getVersioning().getVersions()) {
            Instant releaseDate = versionsDatesMap.get(version);
            if (releaseDate != null) {
                versionsDates.put(version, new MavenVersionDates(version, releaseDate));
            } else {
                log.debug("No release date found for version: {} or package: {}, putting 0 as default",
                        version, packageName);
                versionsDates.put(version, new MavenVersionDates(version, Instant.EPOCH));
            }
        }
        return versionsDates;
    }

    @VisibleForTesting
    Map<String, Instant> getMavenMetadataDatesInfo(String repoKey, String remoteUrl, Set<String> versionsSet) {
        if (!isMavenReleaseDatesParserForCurationEnabled()) {
            return Collections.emptyMap();
        }
        String url = MavenModelUtils.removeMetadataNameFromPath(remoteUrl);
        return mavenMetadataReleaseDatesFetcher.fetchReleaseDates(url, repoKey, versionsSet);
    }

    private CurationPackageForResponse getMatchingCurationPackage(CurationResponse curationResponse,
            Metadata originMetadata) throws PackageCurationException {
        String originPackageName = originMetadata.getGroupId() + ":" + originMetadata.getArtifactId();
        return curationResponse.blockedPackages().stream().filter(pkg -> originPackageName.equals(pkg.packageName()))
                .findFirst()
                .orElseThrow(() -> new PackageCurationException(
                        "Curation response does not contain a package matching the origin metadata: " +
                                originPackageName));
    }

    private Versioning getCuratedVersioning(Metadata originMetadata, List<String> filteredVersions) {
        Versioning versioning = new Versioning();
        versioning.setLastUpdated(originMetadata.getVersioning().getLastUpdated());
        versioning.setVersions(filteredVersions);

        if (shouldHandleLatestOrReleaseTags(originMetadata)) {
            getCuratedVersioningForLatestOrReleaseTags(originMetadata, filteredVersions, versioning);
        }

        return versioning;
    }

    private void logIfVersionTagChanged(Metadata originMetadata, List<String> filteredVersions) {
        String newLatestVersion = filteredVersions.getLast();
        String currentLatest = originMetadata.getVersion();

        if (!currentLatest.equals(newLatestVersion)) {
            log.info("Version tag changed from {} to {}", currentLatest, newLatestVersion);
        }
    }

    private void getCuratedVersioningForLatestOrReleaseTags(Metadata originMetadata, List<String> filteredVersions,
            Versioning versioning) {
        logIfLatestOrReleaseChanged(originMetadata, filteredVersions);
        versioning.setLatest(filteredVersions.getLast());
        versioning.setRelease(filteredVersions.getLast());
    }

    private void logIfLatestOrReleaseChanged(Metadata originMetadata, List<String> filteredVersions) {
        String newLatestVersion = filteredVersions.getLast();
        String currentLatest = originMetadata.getVersioning().getLatest();
        String currentRelease = originMetadata.getVersioning().getRelease();

        if (!currentLatest.equals(newLatestVersion)) {
            log.info("Latest version changed from {} to {}", currentLatest, newLatestVersion);
        } else if (!currentRelease.equals(newLatestVersion)) {
            log.info("Release version changed from {} to {}", currentRelease, newLatestVersion);
        }
    }

    private static boolean isMavenReleaseDatesParserForCurationEnabled() {
        return ConstantValues.mavenReleaseDatesParserForCurationEnabled.getBoolean();
    }


}
