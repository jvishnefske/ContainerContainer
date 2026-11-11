package org.artifactory.repo.multipart;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.RepoResource;
import org.artifactory.metrics.ExternalClientsServiceMetrics;
import org.artifactory.request.DownloadPerfCounters;
import org.artifactory.request.HeadersMultiMap;
import org.artifactory.request.NullRequestContext;
import org.artifactory.request.RequestContext;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.service.SmartRepoService;
import com.jfrog.storage.sonic.DownloadResult;
import com.jfrog.storage.sonic.ParallelDownloadConfig;
import com.jfrog.storage.sonic.ParallelDownloadMetrics;
import com.jfrog.storage.sonic.SonicDownloader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Manages parallel downloads with concurrency limits.
 * Thread-safe Spring-managed service shared across all HttpRepo instances.
 */
@Slf4j
@Service
public class ParallelDownloadService {

    public static final String RCD_EXPECTED_SHA1 = "ARTIFACTORY_RCD_EXPECTED_SHA1";
    public static final String RCD_SUPPORTS_RANGE = "ARTIFACTORY_RCD_SUPPORTS_RANGE";

    private SonicDownloader downloader;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Autowired
    private SmartRepoService smartRepoService;

    void setSmartRepoService(SmartRepoService smartRepoService) {
        this.smartRepoService = smartRepoService;
    }

    synchronized void setDownloader(SonicDownloader downloader) {
        this.downloader = downloader;
    }

    void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PreDestroy
    public void destroy() {
        if (downloader != null) {
            log.info("Shutting down parallel download service");
            downloader.close();
        }
    }

    private boolean meetsMinimumSize(long fileSize) {
        return fileSize >= ConstantValues.remoteConcurrentDownloadsMinFileSizeBytes.getLong();
    }

    /**
     * Checks if there's sufficient disk space for parallel download.
     */
    private boolean hasSufficientDiskSpace(long fileSize) {
        File downloadDir = getDownloadDir();

        // Use parent directory for space check
        File spaceCheckDir = downloadDir.exists() ? downloadDir : downloadDir.getParentFile();
        long usableSpace = spaceCheckDir.getUsableSpace();

        // Safety: ensure at least 2x file size available on disk
        if (usableSpace < fileSize * 2) {
            log.warn("Insufficient disk space for parallel download: need {}MB, have {}MB",
                    (fileSize * 2) / (1024 * 1024), usableSpace / (1024 * 1024));
            return false;
        }

        return true;
    }

    /**
     * Gets the parallel downloader, creating it lazily if needed.
     * Thread-safe lazy initialization using synchronized method.
     */
    private synchronized SonicDownloader getDownloader() {
        if (downloader == null) {
            downloader = createDownloader();
        }
        return downloader;
    }

    SonicDownloader createDownloader() {
        ParallelDownloadConfig config = ParallelDownloadConfig.builder()
                .threadsPerRequest(ConstantValues.remoteConcurrentDownloadsThreadsPerRequest.getInt())
                .partSizeBytes(ConstantValues.remoteConcurrentDownloadsPartSizeBytes.getLong())
                .poolSize(ConstantValues.remoteConcurrentDownloadsThreadPoolSize.getInt())
                .maxParallelDownloads(ConstantValues.remoteConcurrentDownloadsLimit.getInt())
                .partBufferSizeBytes(ConstantValues.remoteConcurrentDownloadsPartBufferSizeBytes.getInt())
                .checksumBufferSizeBytes(ConstantValues.remoteConcurrentDownloadsChecksumBufferSizeBytes.getInt())
                .downloadDir(getDownloadDir())
                .build();

        SonicDownloader service = new SonicDownloader(config);

        bindMetrics(service);
        return service;
    }

    private File getDownloadDir() {
        return new File(ArtifactoryHome.get().getDataDir(), "remote-temp");
    }

    private void bindMetrics(SonicDownloader service) {
        if (meterRegistry == null) {
            log.debug("MeterRegistry not available, skipping parallel download metrics registration");
            return;
        }

        try {
            var metrics = service.getMetrics();
            var executor = metrics.getExecutor();

            Gauge.builder("remote.concurrent.download.executor.threads.active", executor,
                    e -> e.getActiveCount()).register(meterRegistry);
            Gauge.builder("remote.concurrent.download.executor.pool.size", executor,
                    e -> e.getPoolSize()).register(meterRegistry);
            FunctionCounter.builder("remote.concurrent.download.executor.completed", executor,
                    e -> e.getCompletedTaskCount()).register(meterRegistry);

            Gauge.builder("remote.concurrent.download.in.progress", metrics,
                    m -> m.getActiveDownloads()).register(meterRegistry);
            FunctionCounter.builder("remote.concurrent.download.success", metrics,
                    m -> m.getSuccessCount()).register(meterRegistry);
            FunctionCounter.builder("remote.concurrent.download.failed", metrics,
                    m -> m.getFailedCount()).register(meterRegistry);
            FunctionCounter.builder("remote.concurrent.download.bytes", metrics,
                    m -> m.getTotalBytes()).register(meterRegistry);

            FunctionCounter.builder("remote.concurrent.download.parts.completed", metrics,
                    m -> m.getPartsForStatus(ParallelDownloadMetrics.PART_COMPLETED_KEY))
                    .description("Successful part downloads (HTTP 206)")
                    .register(meterRegistry);

            String[] retryReasons = {"408", "429", "500", "502", "503", "504"};
            for (String reason : retryReasons) {
                FunctionCounter.builder("remote.concurrent.download.parts.retries", metrics,
                        m -> m.getPartRetriesForReason(reason))
                        .tag("reason", reason)
                        .description("Part downloads retried by reason")
                        .register(meterRegistry);
            }

            Gauge.builder("remote.concurrent.download.suspended", metrics,
                    m -> m.getSuspensionSupplier().getAsBoolean() ? 1 : 0)
                    .description("1 when parallel download is suspended (backpressure), 0 otherwise")
                    .register(meterRegistry);

            String[] nonRetryableReasons = {"200", "400", "401", "403", "404", "IOException"};
            for (String reason : nonRetryableReasons) {
                FunctionCounter.builder("remote.concurrent.download.parts.nonretryable", metrics,
                        m -> m.getPartNonRetryableFailuresForReason(reason))
                        .tag("reason", reason)
                        .description("Non-retryable part failures")
                        .register(meterRegistry);
            }

            Gauge.builder("remote.concurrent.download.enabled", () ->
                    ConstantValues.remoteConcurrentDownloadsEnabled.getBoolean() ? 1 : 0)
                    .description("Sonic downloads enabled (1=enabled, 0=disabled)")
                    .register(meterRegistry);
            Gauge.builder("remote.concurrent.download.min.file.size.bytes",
                    ConstantValues.remoteConcurrentDownloadsMinFileSizeBytes::getLong)
                    .description("Minimum file size in bytes for parallel downloads")
                    .register(meterRegistry);
            Gauge.builder("remote.concurrent.download.threads.per.request",
                    ConstantValues.remoteConcurrentDownloadsThreadsPerRequest::getInt)
                    .description("Number of threads used per parallel download request")
                    .register(meterRegistry);

            log.debug("Parallel download metrics registered");
        } catch (Exception e) {
            log.warn("Failed to register parallel download metrics: {}", e.getMessage());
        }
    }

    /**
     * Checks if parallel download should be used for this request.
     * If returns true, caller MUST call downloadResourceMultipart which handles slot release.
     */
    public boolean shouldUseParallelDownload(RequestContext requestContext,
            String repoKey, RepoType repoType, Supplier<RepoResource> retrieveInfo) {
        if (requestContext instanceof NullRequestContext) {
            return false;
        }
        if (retrieveInfo == null || !ConstantValues.remoteConcurrentDownloadsEnabled.getBoolean()) {
            return false;
        }
        if (!isRepoTypeAllowed(repoType)) {
            log.debug("Parallel download skipped - repo type not allowed: {}", repoType);
            return false;
        }

        boolean isSmartRepo = smartRepoService.isSmartRepo(repoKey);
        if (ConstantValues.remoteConcurrentDownloadsSmartRepoOnly.getBoolean() && !isSmartRepo) {
            log.debug("Parallel download skipped - not a smart repo: {}", repoKey);
            return false;
        }

        long fileSize = resolveFileSize(requestContext, retrieveInfo, isSmartRepo);
        if (fileSize < 0 || !meetsMinimumSize(fileSize)) {
            return false;
        }
        if (!supportsRangeRequests(requestContext)) {
            return false;
        }
        if (!hasSufficientDiskSpace(fileSize)) {
            return false;
        }

        return true;
    }

    private long resolveFileSize(RequestContext requestContext, Supplier<RepoResource> retrieveInfo,
            boolean isSmartRepo) {
        String fileSizeStr = requestContext.getAttribute("fileSize");
        if (fileSizeStr != null) {
            try {
                return Long.parseLong(fileSizeStr);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        try {
            RepoResource info = retrieveInfo.get();
            long fileSize = info.getSize();
            requestContext.setAttribute("fileSize", String.valueOf(fileSize));

            if (isSmartRepo) {
                String expectedSha1 = info.getInfo().getSha1();
                if (StringUtils.isNotBlank(expectedSha1)) {
                    requestContext.setAttribute(RCD_EXPECTED_SHA1, expectedSha1);
                }
            }
            return fileSize;
        } catch (Exception e) {
            log.debug("Could not get resource info for parallel download", e);
            return -1;
        }
    }

    private boolean supportsRangeRequests(RequestContext requestContext) {
        String supportsRange = requestContext.getAttribute(RCD_SUPPORTS_RANGE);
        if (!Boolean.parseBoolean(supportsRange)) {
            log.debug("Parallel download skipped - server does not support Range requests");
            return false;
        }
        return true;
    }

    private boolean isRepoTypeAllowed(RepoType repoType) {
        String allowedTypes = ConstantValues.remoteConcurrentDownloadsAllowedRepoTypes.getString();
        if (StringUtils.isBlank(allowedTypes)) {
            return true; // Empty whitelist = all allowed
        }
        return Arrays.stream(allowedTypes.split(","))
                .map(String::trim)
                .map(RepoType::fromType)
                .anyMatch(allowed -> allowed == repoType);
    }

    /**
     * Performs parallel download. Caller must have checked shouldUseParallelDownload first.
     * Returns null on failure (caller should fallback).
     *
     * @param requestHeaders same headers as the sequential remote GET (whitelist, plugins, unified events); may be null
     */
    public DownloadResult downloadResourceMultipart(URI uri, RequestContext requestContext,
            CloseableHttpClient httpClient, @Nullable HeadersMultiMap requestHeaders) {
        try {
            String fileSizeStr = requestContext.getAttribute("fileSize");
            if (fileSizeStr == null) {
                log.warn("Parallel download skipped - fileSize not in context: {}", uri);
                return null;
            }
            long fileSize = Long.parseLong(fileSizeStr);

            log.debug("Starting parallel download: url={}, size={}MB", uri, fileSize / (1024 * 1024));

            long startTime = System.currentTimeMillis();
            DownloadResult result = getDownloader().download(httpClient, uri, fileSize,
                    requestHeaders == null ? null : requestHeaders.entries());
            long totalDuration = System.currentTimeMillis() - startTime;

            // Record single wall-clock duration for entire parallel download to artifactory-request.log
            // (dtime, waited.remote) - not per range request
            DownloadPerfCounters counters = RequestThreadLocal.getDownloadPerfCounters(true);
            if (counters != null) {
                counters.addRemoteDownloadTime(totalDuration);
                counters.addExternalWaitTime(ExternalClientsServiceMetrics.TARGET_REMOTE, totalDuration);
            }

            // Validate SHA1 if expected value is available (smart repos provide this)
            String expectedSha1 = requestContext.getAttribute(RCD_EXPECTED_SHA1);
            if (StringUtils.isNotBlank(expectedSha1) && !expectedSha1.equalsIgnoreCase(result.getSha1())) {
                log.warn("SHA1 mismatch for {}: expected={}, calculated={}, falling back to standard download",
                        uri, expectedSha1, result.getSha1());
                IOUtils.closeQuietly(result);
                return null;
            }

            return result;
        } catch (Exception e) {
            log.warn("Parallel download failed for {}: {}, falling back to standard download",
                    uri, e.getMessage(), e);
            return null;
        }
    }
}
