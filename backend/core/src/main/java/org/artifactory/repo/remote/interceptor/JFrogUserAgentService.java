package org.artifactory.repo.remote.interceptor;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.artifactory.cron.CronUtils;
import org.artifactory.io.checksum.ChecksumUtils;
import org.artifactory.metrics.TimeRandomizer;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.util.BasicHttpUtils;
import org.artifactory.util.HttpClientConfigurator;
import org.jfrog.common.JsonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Service for fetching and updating the JFrog User Agent URL.
 * This service fetches the URL from a GitHub repository, validates the checksum, and updates the User Agent value.
 * the validation is done by comparing the SHA1 checksum of the fetched content with the SHA1 checksum from the
 * metadata.
 *
 * @author Hussein Alshqerat
 */
@Slf4j
@Service
public class JFrogUserAgentService implements ContextCreationListener {

    private final String defaultJFrogUserAgentURL;
    private final String jfrogUserAgentDownloadURL;
    private final String jfrogUserAgentGetMetadataURL;
    private final String jfrogUserAgentURLJobCron;
    private final boolean jfrogUserAgentEnabled;
    private final boolean jfrogUserAgentDownloadFromRemoteEnabled;
    private final AtomicReference<String> userAgent;
    private final TaskService taskService;

    public JFrogUserAgentService(
            @Value("${artifactory.jfrog.user.agent.default.url}") String defaultJFrogUserAgentURL,
            @Value("${artifactory.jfrog.user.agent.download.url}") String jfrogUserAgentDownloadURL,
            @Value("${artifactory.jfrog.user.agent.get.metadata.url}") String jfrogUserAgentGetMetadataURL,
            @Value("${artifactory.jfrog.user.agent.url.job.cron}") String jfrogUserAgentURLJobCron,
            @Value("${artifactory.jfrog.user.agent.for.remote.requests.enabled}") boolean jfrogUserAgentEnabled,
            @Value("${artifactory.jfrog.user.agent.download.from.remote.enabled}")
            boolean jfrogUserAgentDownloadFromRemoteEnabled,
            TaskService taskService) {
        this.defaultJFrogUserAgentURL = defaultJFrogUserAgentURL;
        this.jfrogUserAgentDownloadURL = jfrogUserAgentDownloadURL;
        this.jfrogUserAgentGetMetadataURL = jfrogUserAgentGetMetadataURL;
        this.jfrogUserAgentURLJobCron = jfrogUserAgentURLJobCron;
        this.jfrogUserAgentEnabled = jfrogUserAgentEnabled;
        this.jfrogUserAgentDownloadFromRemoteEnabled = jfrogUserAgentDownloadFromRemoteEnabled;
        this.taskService = taskService;
        this.userAgent = new AtomicReference<>(defaultJFrogUserAgentURL);
    }

    private static CloseableHttpClient createHttpClient() {
        return new HttpClientConfigurator()
                .socketTimeout(15000)
                .connectionTimeout(5000)
                .noRetry()
                .maxTotalConnections(1)
                .build();
    }

    public String getUserAgent() {
        return userAgent.get();
    }

    /**
     * Fetches the JFrog User Agent URL and updates the User Agent value.
     */
    public void fetchJFrogUserAgentURL() {
        try {
            String sha1FromMetadata = fetchSha1FromMetadata();
            updateUserAgent(sha1FromMetadata);
        } catch (Exception e) {
            String defaultUserAgent = defaultJFrogUserAgentURL;
            log.error("Error fetching JFrog User Agent URL, falling back to the default JFrog User Agent URL {}.",
                    defaultUserAgent, e);
            this.userAgent.set(defaultUserAgent);
        }
    }

    private void updateUserAgent(String sha1FromMetadata) throws IOException {
        String responseBody = fetchUserAgentJsonContent();
        if (!isChecksumMatching(calculateSha1FromResponseBody(responseBody), sha1FromMetadata)) {
            return;
        }
        this.userAgent.set(JsonUtils.getInstance().readValue(responseBody, UserAgent.class).getValue());
    }

    private String fetchUserAgentJsonContent() throws IOException {
        try (CloseableHttpClient client = createHttpClient()) {
            HttpResponse response = executeHttpMethod(client, jfrogUserAgentDownloadURL);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new IOException("Failed to fetch JFrog User Agent URL.");
            }
            return EntityUtils.toString(response.getEntity());
        }
    }

    private boolean isChecksumMatching(String calculatedChecksum, String expectedChecksum) {
        if (expectedChecksum != null && !expectedChecksum.equals(calculatedChecksum)) {
            log.error("Checksum mismatch between remote user agent URL and metadata. Skipping update.");
            return false;
        }
        return true;
    }

    String calculateSha1FromResponseBody(String responseBody) throws IOException {
        String blobHeader = "blob " + responseBody.getBytes(UTF_8).length + "\0";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(blobHeader.getBytes(UTF_8));
        outputStream.write(responseBody.getBytes(UTF_8));
        return ChecksumUtils.calculateSha1(new ByteArrayInputStream(outputStream.toByteArray()));
    }

    String fetchSha1FromMetadata() throws IOException {
        try (CloseableHttpClient client = createHttpClient()) {
            HttpResponse metadataResponse = executeHttpMethod(client, jfrogUserAgentGetMetadataURL);
            return extractSha1FromMetadataResponse(metadataResponse);
        } catch (IOException e) {
            log.error("Error fetching SHA1 from metadata", e);
            throw e;
        }
    }

    private String extractSha1FromMetadataResponse(HttpResponse metadataResponse) throws IOException {
        if (metadataResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            return null;
        }
        String body = EntityUtils.toString(metadataResponse.getEntity());
        JsonNode jsonNode = JsonUtils.getInstance().readTree(body);
        JsonNode shaNode = jsonNode.get("sha");
        return shaNode != null ? shaNode.asText() : null;
    }

    private HttpResponse executeHttpMethod(CloseableHttpClient client, String url) throws IOException {
        HttpGet getMethod = new HttpGet(BasicHttpUtils.encodeQuery(url));
        return client.execute(getMethod);
    }

    @Override
    public void onContextCreated() {
        if (jfrogUserAgentEnabled && jfrogUserAgentDownloadFromRemoteEnabled) {
            scheduleJFrogUserAgentURLJob();
        }
    }

    /**
     * Schedules a recurring job to fetch the JFrog User Agent URL.
     */
    private void scheduleJFrogUserAgentURLJob() {
        log.debug("Scheduling recurring job to fetch the JFrog User Agent URL");
        Task task = TaskUtils.createCronTask(
                JFrogUserAgentURLJob.class,
                buildRandomQuartzExp(),
                "Fetch JFrog User Agent URL job."
        );
        taskService.startTask(task, false);
        log.debug("Recurring job to fetch the JFrog User Agent URL scheduled");
    }

    /**
     * To reduce load on GitHub and avoid rate limiter we set random execution time
     *
     * @return Quartz scheduling expression
     */
    private String buildRandomQuartzExp() {
        String predefinedExp = jfrogUserAgentURLJobCron;
        if (StringUtils.isNotBlank(predefinedExp) && CronUtils.isValid(predefinedExp)) {
            return predefinedExp;
        }
        return String.format(
                "0 %d %d ? * *",
                TimeRandomizer.randomMinute(),
                TimeRandomizer.randomHourAtNight()
        );
    }

    @Data
    private static class UserAgent {
        private String key;
        private String value;

        @JsonAnySetter
        public void setUserAgent(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}