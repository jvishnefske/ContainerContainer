package org.artifactory.rest.resource.mfe.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.rest.resource.mfe.MfeName;
import org.artifactory.rest.resource.mfe.MfeProjectConfig;
import org.artifactory.rest.resource.mfe.MfeProjectRegistry;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.ConditionalOnProperty;
import org.artifactory.util.HttpClientConfigurator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * This class is responsible for serving MFE files from an external server over HTTP.
 * The initial purpose is to serve the MFE files from CDN. However users can be very creative in determining the source
 * as long as it is HTTP.
 * Supports multiple MFE projects configured via MfeProjectResolver.
 * Each project can have its own external base URL, or use the default.
 *
 * @author nirt
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "artifactory.core.mfe.external.enabled", havingValue = "true")
public class ExternalMFE implements MFEFileServer {

    private static final String EXTERNAL_MFE_MINOR_TEMPLATE = "${base}/${minor}";
    private static final String EXTERNAL_MFE_BASEURL_TEMPLATE = EXTERNAL_MFE_MINOR_TEMPLATE + "/${patch}";
    private static final String VERSIONS_MANIFEST_URL_TEMPLATE =
            EXTERNAL_MFE_MINOR_TEMPLATE + "/versions.manifest.json";
    public static final String FAILED_TO_SERVE_MFE_FILE = "Failed to serve MFE file: ";
    CloseableHttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // Per-project version tracking
    private final Map<MfeName, String> projectVersions = new ConcurrentHashMap<>();
    private final Map<MfeName, Gauge> projectGauges = new ConcurrentHashMap<>();
    // Cache for classpath versions (retrieved once at startup, doesn't change during runtime)
    // Uses MfeName enum as key to ensure type safety - only valid MFE names can be used
    private final Map<MfeName, String> classpathVersionCache = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final TaskService taskService;
    private final MfeProjectRegistry mfeProjectRegistry;

    public ExternalMFE(TaskService taskService, MeterRegistry meterRegistry, MfeProjectRegistry mfeProjectRegistry) {
        this.taskService = taskService;
        this.meterRegistry = meterRegistry;
        this.mfeProjectRegistry = mfeProjectRegistry;
    }

    @PostConstruct
    public void init() {
        if (client == null) {
            client = new HttpClientConfigurator()
                    .socketTimeout(15000)
                    .connectionTimeout(15000)
                    .retry(0, false)
                    .build();
        }
        // Initialize versions for all projects
        refreshMFEVersionForProjects();
        registerRefreshTasks();
    }

    /**
     * Gets the version from classpath for a project.
     * Retrieves from ArtifactoryHome and caches it for subsequent calls.
     * The version is cached because it doesn't change during runtime.
     * @param project The project configuration
     * @return The version from classpath
     */
    private String getVersionFromClasspathForProject(MfeProjectConfig project) {
        MfeName mfeName = project.getMfeName();
        
        // Check if already cached
        String cachedVersion = classpathVersionCache.get(mfeName);
        if (cachedVersion != null) {
            return cachedVersion;
        }
        
        // Retrieve from ArtifactoryHome and cache it
        String version = null;
        try {
            version = ArtifactoryHome.get()
                    .getRunningArtifactoryServiceVersion()
                    .getMfeVersion(mfeName.getValue());
        } catch (Exception e) {
            log.warn("Failed to get MFE version from classpath for project {}: {}", mfeName, e.getMessage());
        }
        
        if (version != null) {
            classpathVersionCache.put(mfeName, version);
        }
        return version;
    }

    private void registerRefreshTasks() {
        long interval = ConstantValues.generalMfeExternalRefreshVersionIntervalMs.getLong();
        long initialDelay = 0;

        // Create task using TaskUtils (proper public API)
        try {
            Task task = TaskUtils.createRepeatingTask(
                    RefreshMFEVersionJob.class,
                    interval,
                    initialDelay,
                    "Refresh MFE versions for all projects"
            );
            taskService.startTask(task, false);
        } catch (Exception e) {
            log.error("Failed to create refresh task for MFE projects", e);
        }
    }

    /**
     * Refreshes MFE versions for all projects.
     * Loops through all configured projects and updates their versions.
     */
    synchronized void refreshMFEVersionForProjects() {
        for (MfeProjectConfig project : mfeProjectRegistry.getAllProjects()) {
            refreshMFEVersionForProject(project);
        }
    }

    /**
     * Refreshes MFE version for a specific project.
     * @param project The base path of the project (e.g., "" for core, "/rtui" for rtui)
     */
    synchronized void refreshMFEVersionForProject(MfeProjectConfig project) {
        if (project == null) {
            log.warn("Project not found");
            return;
        }

        String newVersion = getExternalMFEVersionForProject(project);
        if (newVersion == null) {
            log.warn("Failed to get version for project for mfe: [{}]. Skipping refresh.", project.getMfeName());
            return;
        }
        String currentVersion = projectVersions.get(project.getMfeName());
        if (newVersion.equals(currentVersion)) {
            return;
        }

        projectVersions.put(project.getMfeName(), newVersion);

        // Remove old gauge if exists
        Gauge oldGauge = projectGauges.remove(project.getMfeName());
        if (oldGauge != null) {
            meterRegistry.remove(oldGauge);
        }

        // Register new gauge with project-specific metric name
        String metricName = project.getMetricName();
        String baseUrlForProject = createExternalMFEBaseUrlForProject(newVersion, project);
        log.info("External MFE refreshed for project mfeName:{}. Version: {}, base URL: {}",
                project.getMfeName(), newVersion, baseUrlForProject);
        Gauge gauge = Gauge.builder(metricName, () -> 1)
                .description("The version of the external MFE").tag("version", newVersion)
                .register(meterRegistry);
        if (gauge != null) {
            projectGauges.put(project.getMfeName(), gauge);
        }
    }


    public String getVersionFromVersionsManifest(String mfeVersionFromClasspath, MfeProjectConfig project) {
        String url = getManifestUrl(mfeVersionFromClasspath, project);
        log.debug("Getting versions manifest from {}", url);
        HttpGet getRequest = new HttpGet(url);
        try (CloseableHttpResponse getResponse = client.execute(getRequest)) {
            int statusCode = getResponse.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                log.error("Failed to get versions manifest from {} error code {}", url, statusCode);
                return null;
            }
            return objectMapper.readTree(getResponse.getEntity().getContent()).path("default").asText();
        } catch (IOException e) {
            log.error("Failed to get versions manifest from {}", url, e);
            return null;
        }
    }

    private static String getManifestUrl(String mfeVersionFromClasspath, MfeProjectConfig project) {
        ConstantValues pathBase = project.getExternalPathBase();
        String base = pathBase.getString();
        String minorVersion = extractMinorVersion(mfeVersionFromClasspath);
        Map<String, String> valuesMap =
                Map.of("base", base,
                        "minor", minorVersion);
        StringSubstitutor stringSubstitutor = new StringSubstitutor(valuesMap);
        return stringSubstitutor.replace(VERSIONS_MANIFEST_URL_TEMPLATE);
    }


    private String getExternalMFEVersionForProject(MfeProjectConfig project) {
        ConstantValues overrideVersion = project.getExternalOverrideExternalVersion();
        String externalVersionOverride = overrideVersion.getString();
        ConstantValues version = ConstantValues.coreMfeExternalOverrideExternalVersion;
        String ver = version.getString();
        log.debug("External mfe version override detected, using version {} -- {}",
                externalVersionOverride, ver);
        if (!StringUtils.isBlank(externalVersionOverride)) {
            return externalVersionOverride;
        }
        String versionFromClasspath = getVersionFromClasspathForProject(project);
        if (versionFromClasspath == null) {
            log.warn("No version found in classpath for project: {}", project.getMfeName());
            return null;
        }
        String versionFromVersionsManifest = getVersionFromVersionsManifest(versionFromClasspath, project);
        if (versionFromVersionsManifest == null) {
            log.info("Failed to get version from versions manifest, using version from classpath {}",
                    versionFromClasspath);
            return versionFromClasspath;
        }
        log.debug("External mfe version detected, using version {}", versionFromVersionsManifest);
        return versionFromVersionsManifest;
    }

    private String createExternalMFEBaseUrlForProject(String version, MfeProjectConfig project) {
        ConstantValues pathBase = project.getExternalPathBase();
        String base = pathBase.getString();
        String minorVersion = extractMinorVersion(version);
        Map<String, String> valuesMap =
                Map.of("base", base,
                        "minor", minorVersion,
                        "patch", version);
        StringSubstitutor substitutor = new StringSubstitutor(valuesMap);
        return substitutor.replace(EXTERNAL_MFE_BASEURL_TEMPLATE);
    }

    /**
     * Gets the base URL for a given resource path, using project-specific path base if available.
     * @param resourcePath The resource path to determine the project
     * @return The base URL to use for this resource
     */
    private String getBaseUrlForResource(String resourcePath) {
        MfeProjectConfig project = mfeProjectRegistry.findProjectForResource(resourcePath);
        if (project == null) {
            log.warn("No project found for resource path: {}", resourcePath);
            return "";
        }
        // Use project-specific path base and version
        ConstantValues pathBase = project.getExternalPathBase();
        String projectVersion = projectVersions.get(project.getMfeName());
        if (projectVersion == null) {
            log.warn("No version found for project: {}", project.getMfeName());
            return "";
        }
        return createExternalMFEBaseUrlFromPathBase(pathBase.getString(), projectVersion);
    }

    /**
     * Creates an external MFE base URL from a path base and version.
     * Similar to createExternalMFEBaseUrl but uses a custom path base.
     * @param pathBase The path base (e.g., "https://bucket.com/folder")
     * @param version The version (e.g., "1.2.3")
     * @return The complete base URL (e.g., "https://bucket.com/folder/1.2/1.2.3")
     */
    private static String createExternalMFEBaseUrlFromPathBase(String pathBase, String version) {
        String minorVersion = extractMinorVersion(version);
        Map<String, String> valuesMap =
                Map.of("base", pathBase,
                        "minor", minorVersion,
                        "patch", version);
        StringSubstitutor substitutor = new StringSubstitutor(valuesMap);
        return substitutor.replace(EXTERNAL_MFE_BASEURL_TEMPLATE);
    }

    /**
     * Extracts the minor version (major.minor) from a semantic version string.
     * @param version The version string (e.g., "1.2.3")
     * @return The minor version string (e.g., "1.2")
     */
    private static String extractMinorVersion(String version) {
        // Version format is "major.minor.patch" (e.g., "1.2.3")
        int firstDot = version.indexOf('.');
        if (firstDot == -1) {
            // If no dot, assume it's just major version
            return version;
        }
        int secondDot = version.indexOf('.', firstDot + 1);
        if (secondDot == -1) {
            // If only one dot, return "major.minor"
            return version;
        }
        // Extract "major.minor" from "major.minor.patch"
        return version.substring(0, secondDot);
    }

    @Override
    public void serveFile(HttpServletResponse httpResponse, String resourcePath, String contentType)
            throws NotFoundException {
        MfeProjectConfig project = mfeProjectRegistry.findProjectForResource(resourcePath);
        String projectBaseUrl = getBaseUrlForResource(resourcePath);
        if (projectBaseUrl.isEmpty()) {
            String projectName = project != null ? project.getMfeName().toString() : "unknown";
            throw new NotFoundException("MFE version not available for project: " + projectName + 
                    ". The project may not be in production yet.");
        }
        String url = projectBaseUrl + resourcePath;
        String projectInfo = project != null ? " (project: " + project.getBasePath() + ")" : "";
        log.debug("Serving MFE file from {}{}", url, projectInfo);
        HttpGet getRequest = new HttpGet(url);
        try (CloseableHttpResponse getResponse = client.execute(getRequest)) {
            if (getResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new NotFoundException(FAILED_TO_SERVE_MFE_FILE + url);
            }
            httpResponse.setContentType(contentType);
            httpResponse.setStatus(HttpStatus.SC_OK);
            IOUtils.copy(getResponse.getEntity().getContent(), httpResponse.getOutputStream());
            log.debug("Served MFE file from {}{}", url, projectInfo);
        } catch (IOException e) {
            throw new NotFoundException(FAILED_TO_SERVE_MFE_FILE + url, e);
        }
    }

    @Override
    public String readFileContent(String resourcePath) throws NotFoundException {
        MfeProjectConfig project = mfeProjectRegistry.findProjectForResource(resourcePath);
        String projectBaseUrl = getBaseUrlForResource(resourcePath);
        if (projectBaseUrl.isEmpty()) {
            String projectName = project != null ? project.getMfeName().toString() : "unknown";
            throw new NotFoundException("MFE version not available for project: " + projectName + 
                    ". The project may not be in production yet.");
        }
        String url = projectBaseUrl + resourcePath;
        String projectInfo = project != null ? " (project: " + project.getBasePath() + ")" : "";
        HttpGet getRequest = new HttpGet(url);
        log.debug("Reading MFE file from {}{}", url, projectInfo);
        try (CloseableHttpResponse getResponse = client.execute(getRequest)) {
            if (getResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new NotFoundException(FAILED_TO_SERVE_MFE_FILE + url);
            }
            return IOUtils.toString(getResponse.getEntity().getContent(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NotFoundException(FAILED_TO_SERVE_MFE_FILE + url, e);
        }
    }

    @Override
    public String getMfeVersion(String mfeName) throws NotFoundException {
        MfeName mfeNameEnum = MfeName.fromValue(mfeName);
        if (mfeNameEnum == null) {
            throw new NotFoundException("Invalid MFE name: " + mfeName);
        }
        MfeProjectConfig project = mfeProjectRegistry.findProjectByMfeName(mfeNameEnum);
        if (project == null) {
            throw new NotFoundException("MFE project not found: " + mfeName);
        }
        String version = projectVersions.get(project.getMfeName());
        if (version == null) {
            throw new NotFoundException("Version not found for MFE: " + mfeName);
        }
        return version;
    }
}
