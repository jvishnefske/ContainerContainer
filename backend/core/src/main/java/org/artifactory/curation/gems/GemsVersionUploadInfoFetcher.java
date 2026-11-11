package org.artifactory.curation.gems;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jfrog.ph.acl.service.PackageArtifactService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches gem version upload information from the RubyGems API.
 * Makes HTTP calls to /api/v1/versions/[GEM_NAME].json to get version details including upload timestamps.
 */
@Slf4j
public class GemsVersionUploadInfoFetcher {

    private static final String API_VERSIONS_PATH = "/api/v1/versions/";
    private static final String JSON_EXTENSION = ".json";
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private final PackageArtifactService packageArtifactService;

    public GemsVersionUploadInfoFetcher(PackageArtifactService packageArtifactService) {
        this.packageArtifactService = packageArtifactService;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // JavaTimeModule handles ISO 8601 flexibly (with/without milliseconds)
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Fetches version upload information for a gem from the remote repository.
     * No need to cache it as it will always expire before the curated index, and we shouldn't make it live longer than
     * the curated index, so it won't be stale compared to the original index and we might have records with no matching
     * create dates.
     *
     * @param repoKey     repository key for authentication
     * @param baseUrl     base URL of the remote repository
     * @param gemName     name of the gem to fetch versions for
     * @return map of version to upload time, empty map if fetch fails
     */
    public Map<String, Instant> fetchVersions(String repoKey, String baseUrl, String gemName) {
        List<GemVersionUploadInfo> versions = fetchFromRemote(repoKey, baseUrl, gemName);
        return toVersionMap(versions);
    }

    private List<GemVersionUploadInfo> fetchFromRemote(String repoKey, String baseUrl, String gemName) {
        String apiUrl = buildApiUrl(baseUrl, gemName);
        CloseableHttpResponse response = null;
        try {
            HttpRequestBase request = new HttpGet(apiUrl);
            response = packageArtifactService.executeRemoteHttpRequest(repoKey, request);
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                return parseVersions(response.getEntity().getContent());
            }
            log.info("Failed to fetch gem versions from {}. Status: {}", apiUrl,
                    response.getStatusLine().getStatusCode());
        } catch (Exception e) {
            log.warn("Failed to fetch gem version info for {} from repo {}", gemName, repoKey, e);
        } finally {
            closeResponse(response);
        }
        return Collections.emptyList();
    }

    private Map<String, Instant> toVersionMap(List<GemVersionUploadInfo> versions) {
        if (versions.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Instant> versionMap = new LinkedHashMap<>();
        for (GemVersionUploadInfo info : versions) {
            String version = info.getVersionForIndexFile();
            Instant createdAt = info.getCreatedAt();
            if (log.isDebugEnabled()) {
                log.debug("Upload time for version {} is {}", version, createdAt);
            }
            if (version == null || version.isBlank() || createdAt == null) {
                continue;
            }
            // The merge handles hypothetical version duplicates, assigning it with the latest registered upload time
            versionMap.merge(version, createdAt,
                    (existing, incoming) -> existing.isAfter(incoming) ? existing : incoming);
        }
        return versionMap;
    }

    private String buildApiUrl(String baseUrl, String gemName) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + API_VERSIONS_PATH + gemName + JSON_EXTENSION;
    }

    private List<GemVersionUploadInfo> parseVersions(InputStream content) throws IOException {
        return OBJECT_MAPPER.readValue(content, new TypeReference<>() {});
    }

    private void closeResponse(CloseableHttpResponse response) {
        if (response != null) {
            try {
                response.close();
            } catch (Exception e) {
                log.warn("Failed to close HTTP response: {}", e.getMessage());
                log.debug("", e);
            }
        }
    }
}
