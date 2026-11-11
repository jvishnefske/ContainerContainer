package org.artifactory.curation.gems;

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
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.curation.packages.PackageVersionCurationController;
import org.artifactory.addon.gems.util.GemVersionComparator;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.repo.PackageType;
import org.artifactory.util.CollectionUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling Gems package version curation operations.
 * <p>
 * Responsibilities:
 * - Parse the compact index (info/gem) after loading it into memory
 * - Extract version identifiers for curation requests
 * - Sort curated versions by release date when available, otherwise by RubyGems version semantics
 * - Provide access to the original compact index content from memory
 */
@Slf4j
public class GemsPackageVersionCurationController
        implements PackageVersionCurationController<GemsPackagesCurationMetadataWrapper> {

    private static final String INFO_PATH_PREFIX = "info/";
    private static final String GEMS_DOWNLOAD_FOLDER = "gems";
    private static final String GEM_EXTENSION = ".gem";
    private static final String HEADER_SEPARATOR = "---";
    private static final char SPACE_CHAR = ' ';
    private final PackageSecurityService packageSecurityService;
    private final GemsVersionUploadInfoFetcher versionUploadInfoFetcher;

    /**
     * Constructs a controller for Gems curation operations.
     */
    public GemsPackageVersionCurationController(PackageSecurityService packageSecurityService,
            PackageArtifactService packageArtifactService) {
        this.packageSecurityService = packageSecurityService;
        this.versionUploadInfoFetcher = new GemsVersionUploadInfoFetcher(packageArtifactService);
    }

    @VisibleForTesting
    GemsPackageVersionCurationController(PackageSecurityService packageSecurityService,
            GemsVersionUploadInfoFetcher versionUploadInfoFetcher) {
        this.packageSecurityService = packageSecurityService;
        this.versionUploadInfoFetcher = versionUploadInfoFetcher;
    }

    @Override
    public CurationRequest createMetadataCurationRequest(GemsPackagesCurationMetadataWrapper metadataWrapper,
            String repoKey, String remoteUrl) throws PackageCurationException {
        String packageName = extractPackageName(remoteUrl);
        metadataWrapper.setPackageName(packageName);
        String baseUrl = extractBaseUrl(remoteUrl);

        Map<String, Instant> versionInfoMap =
                versionUploadInfoFetcher.fetchVersions(repoKey, baseUrl, packageName);
        List<CurationVersionForRequest> versions = buildCurationVersions(
                parseVersionsFromLines(metadataWrapper.getMetadataLines()),
                versionInfoMap,
                packageName,
                baseUrl);
        sortCurationVersions(versions);

        return new CurationRequest(
                repoKey,
                PackageType.GEMS,
                Collections.singletonList(new CurationPackageForRequest(packageName, versions)),
                packageSecurityService.getCurrentUserName()
        );
    }

    @Override
    public GemsPackagesCurationMetadataWrapper getCuratedMetadataModel(CurationResponse curationResponse,
            GemsPackagesCurationMetadataWrapper originMetadataWrapper) {
        CurationPackageForResponse curatedPackage =
                verifyCurationResponse(curationResponse, originMetadataWrapper.getPackageName());
        if (curatedPackage == null) {
            return originMetadataWrapper;
        }
        List<String> originLines = originMetadataWrapper.getMetadataLines();
        List<String> filteredLines = new ArrayList<>();

        for (String line : originLines) {
            String version = parseVersionFromLine(line);
            if (!curatedPackage.versions().contains(version)) {
                filteredLines.add(line);
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Package name: {}, Filtered lines: {}", originMetadataWrapper.getPackageName(),
                    String.join(System.lineSeparator(), filteredLines));
        }
        GemsPackagesCurationMetadataWrapper filteredWrapper =
                new GemsPackagesCurationMetadataWrapper(filteredLines);
        filteredWrapper.setPackageName(originMetadataWrapper.getPackageName());
        return filteredWrapper;
    }

    @Override
    public GemsPackagesCurationMetadataWrapper getMetadataModelOrigin(InputStream originMetadataStream)
            throws PackageCurationException {
        try {
            byte[] content = readAllBytesWithLimit(originMetadataStream);
            String contentAsString = new String(content, StandardCharsets.UTF_8);
            List<String> originLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new StringReader(contentAsString))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (StringUtils.isBlank(line) || HEADER_SEPARATOR.equals(line)) {
                        continue;
                    }
                    originLines.add(line);
                }
            }

            return new GemsPackagesCurationMetadataWrapper(originLines);
        } catch (IOException e) {
            String msg = "Failed to read Gems compact index from the provided InputStream";
            log.warn(msg, e);
            throw new PackageCurationException(msg);
        }
    }

    @Override
    public InputStream toInputStream(GemsPackagesCurationMetadataWrapper metadataWrapper)
            throws PackageCurationException {
        StringBuilder builder = new StringBuilder();
        builder.append(HEADER_SEPARATOR).append('\n');
        for (String line : metadataWrapper.getMetadataLines()) {
            builder.append(line).append('\n');
        }
        return new ByteArrayInputStream(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean shouldCheckForCuration(String path) {
        log.debug("Gems CVS flow path to check is {}", path);
        String normalizedPath = StringUtils.removeStart(path, '/');
        if (!isGemsCurationComplaintVersionEnabled() || !normalizedPath.startsWith(INFO_PATH_PREFIX)) {
            return false;
        }
        String packageName = StringUtils.substringAfter(normalizedPath, INFO_PATH_PREFIX);
        log.debug("Gems CVS flow package name to check is {}", packageName);
        return StringUtils.isNotBlank(packageName) && !packageName.contains("/");
    }

    private List<CurationVersionForRequest> buildCurationVersions(List<String> versions,
            Map<String, Instant> versionInfoMap,
            String packageName, String baseUrl) {
        List<CurationVersionForRequest> curationVersions = new ArrayList<>();
        for (String version : versions) {
            if (StringUtils.isBlank(version)) {
                continue;
            }
            Instant createdAt = versionInfoMap.get(version);
            if (createdAt == null) {
                log.info("Missing upload time for gem {} version {} in compact index", packageName, version);
            }
            curationVersions.add(new CurationVersionForRequest(
                    version,
                    buildDownloadUrl(baseUrl, packageName, version),
                    createdAt));
        }
        return curationVersions;
    }

    private void sortCurationVersions(List<CurationVersionForRequest> versions) {
        boolean allHaveDates = versions.stream().allMatch(version -> version.releaseDate() != null);
        if (allHaveDates) {
            versions.sort((first, second) ->
                    second.releaseDate().compareTo(first.releaseDate()));
        } else {
            versions.sort((first, second) ->
                    GemVersionComparator.compareVersions(second.version(), first.version()));
        }
    }

    private String buildDownloadUrl(String baseUrl, String packageName, String version) {
        return baseUrl + "/" + GEMS_DOWNLOAD_FOLDER + "/" + packageName + "-" + version + GEM_EXTENSION;
    }

    private String extractPackageName(String remoteUrl) throws PackageCurationException {
        try {
            String path = URI.create(remoteUrl).getPath();
            int infoIndex = path.lastIndexOf("/" + INFO_PATH_PREFIX);
            if (infoIndex < 0) {
                throw new PackageCurationException(
                        "Gems compact index URL does not contain info path: " + remoteUrl);
            }
            String rawName = path.substring(infoIndex + INFO_PATH_PREFIX.length() + 1);
            String packageName = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
            if (StringUtils.isBlank(packageName)) {
                throw new PackageCurationException(
                        "Failed to extract Gems package name from URL: " + remoteUrl);
            }
            return packageName;
        } catch (PackageCurationException e) {
            throw e; // used to avoid the general catch block below
        } catch (Exception e) {
            throw new PackageCurationException("Invalid Gems compact index URL: " + remoteUrl);
        }
    }

    private String extractBaseUrl(String remoteUrl) {
        int infoIndex = remoteUrl.lastIndexOf("/" + INFO_PATH_PREFIX);
        if (infoIndex >= 0) {
            return remoteUrl.substring(0, infoIndex);
        }
        return remoteUrl;
    }

    @VisibleForTesting
    static boolean isGemsCurationComplaintVersionEnabled() {
        return ConstantValues.gemsCurationComplaintVersionEnabled.getBoolean();
    }

    /**
     * Reads the stream into memory while enforcing {@link ConstantValues#gemsInfoFileMaxSizeBytes}.
     * The read is bounded to {@code maxBytes + 1}, so at most that many bytes
     * are ever held in memory. If the limit is exceeded, an {@link IOException} is thrown.
     */
    private byte[] readAllBytesWithLimit(InputStream inputStream) throws IOException {
        long maxBytes = ConstantValues.gemsInfoFileMaxSizeBytes.getLong();
        try (BoundedInputStream boundedInputStream = BoundedInputStream.builder()
                .setInputStream(inputStream)
                .setMaxCount(maxBytes + 1)
                .get()) {
            byte[] content = boundedInputStream.readAllBytes();
            if (content.length > maxBytes) {
                throw new IOException("Gems compact index exceeds maximum allowed size");
            }
            return content;
        }
    }

    private CurationPackageForResponse verifyCurationResponse(CurationResponse curationResponse, String packageName) {
        if (curationResponse == null || CollectionUtils.isNullOrEmpty(curationResponse.blockedPackages())) {
            log.info("CurationResponse is null or empty of packages for gems package {}", packageName);
            return null;
        }
        if (CollectionUtils.isNullOrEmpty(curationResponse.blockedPackages().getFirst().versions())) {
            log.debug("CurationResponse content for gems package {} contains no blocked versions", packageName);
            return null;
        }
        CurationPackageForResponse responsePackage = curationResponse.blockedPackages().getFirst();
        if (!packageName.equalsIgnoreCase(responsePackage.packageName())) {
            log.warn("CurationResponse: expected package name {} does not match received package name {}", packageName,
                    responsePackage);
            return null;
        }
        return responsePackage;
    }

    private List<String> parseVersionsFromLines(List<String> lines) {
        List<String> versions = new ArrayList<>();
        for (String line : lines) {
            String version = parseVersionFromLine(line);
            if (StringUtils.isNotBlank(version)) {
                versions.add(version);
            }
        }
        return versions;
    }

    private String parseVersionFromLine(String line) {
        if (StringUtils.isBlank(line)) {
            return "";
        }
        int spaceIndex = line.indexOf(SPACE_CHAR);
        return spaceIndex >= 0 ? line.substring(0, spaceIndex) : line;
    }
}
