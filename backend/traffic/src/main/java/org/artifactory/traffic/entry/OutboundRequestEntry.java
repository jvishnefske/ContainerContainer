package org.artifactory.traffic.entry;

import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;

import static org.artifactory.traffic.entry.TokenizedTrafficEntry.COLUMN_SEPARATOR;

/**
 * Entry for outbound requests made by Remote Repositories.
 * <p/>
 * <pre>
 * $date|$traceId|$remoteRepoName|$username|$httpMethod|$reqUrl|$returnStatus|$reqContentLength|$resContentLength
 * |$reqDuration
 * </pre>
 * Example:
 * <pre>
 * 2021-05-26T08:38:23.043Z|80ed836c8b9f1bfa|cool-remote|andreyt|GET
 * |https://entplus.jfrog.io/artifactory/qa-generic-packages/jdbc-drivers/mysql-connector-java-8.0.9-rc.jar
 * |200|0|2018353|79
 * </pre>
 *
 * @author Andrey Tashlitsky
 */
@Builder
@Getter
public class OutboundRequestEntry {

    private final String repoName;
    private final String userName;
    private final String method;
    private final String url;
    private final int statusCode;
    private final long requestContentLength;
    private final long responseContentLength;
    private final long duration;
    private final Map<String, Object> metadata;
    private final long currentTime = System.currentTimeMillis();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZoneUTC();

    public String getFormattedDate() {
        return dateTimeFormatter.print(currentTime);
    }

    public String getTraceId() {
        return Optional.ofNullable(MDC.get("uber-trace-id")).orElse(StringUtils.EMPTY);
    }

    public String getUserName() {
        return Optional.ofNullable(userName).orElse(StringUtils.EMPTY);
    }

    @Override
    public String toString() {
        return String.join(COLUMN_SEPARATOR, getFormattedDate(), getTraceId(), repoName, getUserName(), method, url,
                String.valueOf(statusCode), String.valueOf(requestContentLength), String.valueOf(responseContentLength),
                String.valueOf(duration));
    }
}
