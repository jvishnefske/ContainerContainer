package org.artifactory.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.http.HttpStatus;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.api.rest.constant.RestConstants;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.repo.http.CloseableHttpExecute;
import org.artifactory.exception.http.NotFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class to get the Artifactory service version via the new API with backward compatibility for older versions.
 *
 * @author Andrey Tashlitsky
 */
@UtilityClass
@Slf4j
public class ArtifactoryVersionUtil {
    /**
     * Get Artifactory service version using the provided {@link CloseableHttpExecute} instance.
     *
     * <p>This is the base implementation that works with the {@link CloseableHttpExecute} interface.
     * For standard HTTP client usage, see {@link #getArtifactoryServiceVersion(CloseableHttpClient, String, Map)}.</p>
     *
     * @param closeableHttpExecute The {@link CloseableHttpExecute} instance to use for executing the HTTP request.
     * @param instanceUrl          The base URL of the Artifactory instance.
     * @param headers              The headers to include in the HTTP request.
     * @return The {@link CloseableHttpResponse} containing the service version information.
     * @throws NotFoundException    If the Artifactory instance is not reachable or the URL is invalid.
     * @throws UncheckedIOException If an I/O error occurs during the HTTP request.
     */
    public CloseableHttpResponse getArtifactoryServiceVersion(CloseableHttpExecute closeableHttpExecute,
            String instanceUrl, Map<String, String> headers) {
        return getArtifactoryServiceVersionFunction(httpGet -> {
            try {
                return closeableHttpExecute.executeMethod(httpGet);
            } catch (ConnectTimeoutException e) {
                throw new NotFoundException(
                        String.format("Connection timeout when accessing remote url: '%s'", instanceUrl), e);
            } catch (UnknownHostException e) {
                throw new NotFoundException(String.format("Unknown host when accessing remote url: '%s'", instanceUrl),
                        e);
            } catch (IllegalArgumentException e) {
                throw new NotFoundException(
                        String.format("Invalid URL format when accessing remote url: '%s'", instanceUrl), e);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, instanceUrl, headers);
    }

    /**
     * Get Artifactory service version using the provided {@link CloseableHttpClient} instance.
     *
     * <p>This method is functionally identical to
     * {@link #getArtifactoryServiceVersion(CloseableHttpExecute, String, Map)}
     * but works with {@link CloseableHttpClient} instead of {@link CloseableHttpExecute}. The main differences are:</p>
     * <ul>
     *   <li>Uses {@link CloseableHttpClient#execute(HttpGet)} instead of
     *   {@link CloseableHttpExecute#executeMethod(HttpGet)}</li>
     *   <li>Handles additional exception types: {@link NoHttpResponseException} and {@link HttpHostConnectException}
     *   </li>
     * </ul>
     *
     * @param closeableHttpClient The {@link CloseableHttpClient} instance to use for executing the HTTP request.
     * @param instanceUrl         The base URL of the Artifactory instance.
     * @param headers             The headers to include in the HTTP request.
     * @return The {@link CloseableHttpResponse} containing the service version information.
     * @throws NotFoundException    If the Artifactory instance is not reachable or the URL is invalid.
     * @throws UncheckedIOException If an I/O error occurs during the HTTP request.
     */
    public CloseableHttpResponse getArtifactoryServiceVersion(CloseableHttpClient closeableHttpClient,
            String instanceUrl, Map<String, String> headers) {
        return getArtifactoryServiceVersionFunction(httpGet -> {
            try {
                return closeableHttpClient.execute(httpGet);
            } catch (ConnectTimeoutException e) {
                throw new NotFoundException(
                        String.format("Connection timeout when accessing remote url: '%s'", instanceUrl), e);
            } catch (UnknownHostException e) {
                throw new NotFoundException(String.format("Unknown host when accessing remote url: '%s'", instanceUrl),
                        e);
            } catch (IllegalArgumentException e) {
                throw new NotFoundException(
                        String.format("Invalid URL format when accessing remote url: '%s'", instanceUrl), e);
            } catch (NoHttpResponseException | HttpHostConnectException e) {
                throw new NotFoundException(String.format("Resource not found on remote url: '%s'", instanceUrl), e);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, instanceUrl, headers);
    }

    private CloseableHttpResponse getArtifactoryServiceVersionFunction(
            Function<HttpGet, CloseableHttpResponse> getServiceVersion, String instanceUrl,
            Map<String, String> headers) {
        String basePath = "%instanceUrl/%api/%root/"
                .replace("%instanceUrl", instanceUrl)
                .replace("%api", RestConstants.PATH_API)
                .replace("%root", SystemRestConstants.PATH_ROOT);
        HttpGet serviceVersionHttpGet = new HttpGet(basePath + SystemRestConstants.PATH_VERSION_SERVICE);
        setHeaders(serviceVersionHttpGet, headers);
        return getServiceVersionWithBackwardCompatibility(getServiceVersion, headers, basePath, serviceVersionHttpGet);
    }

    CloseableHttpResponse getServiceVersionWithBackwardCompatibility(
            Function<HttpGet, CloseableHttpResponse> getServiceVersion, Map<String, String> headers, String basePath,
            HttpGet serviceVersionHttpGet) {
        CloseableHttpResponse versionResponse = getServiceVersion.apply(serviceVersionHttpGet);

        if (versionResponse.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
            try {
                versionResponse.close();
            } catch (IOException e) {
                log.error("Unable to close connection: {} ", e.getMessage(), e);
            }
            HttpGet oldVersionGet = new HttpGet(basePath + SystemRestConstants.PATH_VERSION);
            setHeaders(serviceVersionHttpGet, headers);
            return getServiceVersion.apply(oldVersionGet);
        }
        return versionResponse;
    }

    private void setHeaders(HttpGet httpGet, Map<String, String> headers) {
        if (MapUtils.isEmpty(headers)) {
            return;
        }
        for (Map.Entry<String, String> header : headers.entrySet()) {
            httpGet.addHeader(header.getKey(), header.getValue());
        }
    }
}
