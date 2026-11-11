/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.traffic.entry;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.traffic.TrafficAction;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

/**
 * General web request traffic entry.
 * <p/>
 * <pre>
 * $date|$traceId|$remoteAddress|$username|$httpMethod|$reqUrl|$returnStatus|$reqContentLength|$resContentLength
 * |$reqDuration|$reqUserAgent
 * </pre>
 * Example:
 * <pre>
 * 2019-08-13T15:53:15.995Z|4b1b8a0b04e31b80|127.0.0.1|admin|GET|/repo/path/artifact|200|-1|15397|179|curl/7 .54.0
 * </pre>
 *
 * @author Noam Tenne
 */
@Getter
public class RequestEntry extends TokenizedTrafficEntry {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZoneUTC();
    private static final int COLUMNS_COUNT_SPEC = 11;

    private final String userAddress;
    private final String username;
    private final String method;
    private final String path;
    private final int returnCode;
    private final long resContentLength;
    private final long reqContentLength;
    private final String userAgent;
    @Setter
    private Map<String, Object> metadata;

    /**
     * Sets the given entry data in the relevant fields
     *
     * @param userAddress      Address of client machine
     * @param username         Client Artifactory username
     * @param method           HTTP Request method
     * @param path             Request path
     * @param returnCode       Response status code
     * @param resContentLength Response body size
     * @param reqContentLength Request body size
     * @param userAgent        Request user agent
     */
    public RequestEntry(String userAddress, String username, String method, String path,
            int returnCode, long resContentLength, long reqContentLength, long duration, String userAgent) {
        super(duration);
        setDateTimeFormatter(DATE_TIME_FORMAT);
        this.userAddress = userAddress;
        this.username = username;
        this.method = method;
        this.path = path;
        this.returnCode = returnCode;
        this.resContentLength = resContentLength;
        this.reqContentLength = reqContentLength;
        this.userAgent = userAgent;
    }

    @Override
    protected String[] initTokens() {
        String[] tokens = new String[COLUMNS_COUNT_SPEC];
        tokens[0] = getFormattedDate();
        tokens[1] = getTraceId();
        tokens[2] = userAddress;
        tokens[3] = username;
        tokens[4] = method;
        tokens[5] = path;
        tokens[6] = returnCode + "";
        tokens[7] = reqContentLength + "";
        tokens[8] = resContentLength + "";
        tokens[9] = getDuration() + "";
        tokens[10] = userAgent;
        return tokens;
    }

    private String getTraceId() {
        return MDC.get("uber-trace-id");
    }

    @Override
    public TrafficAction getAction() {
        return TrafficAction.REQUEST;
    }

    @Override
    public int getColumnsCount() {
        return COLUMNS_COUNT_SPEC;
    }

    @Override
    long parseDate(String dateToken) {
        return Instant.parse(dateToken).toEpochMilli();
    }

    @Override
    String getDuration(String[] tokens) {
        return tokens[9];
    }

    /**
     * Parses the given textual entry and sets the object fields accordingly. for testing only
     *
     * @param entry Textual entry
     */
    @SuppressWarnings("WeakerAccess") // called in org.artifactory.traffic.entry.TokenizedTrafficEntryFactory
    public RequestEntry(String entry) {
        setDateTimeFormatter(DATE_TIME_FORMAT);
        String[] tokens = parseEntry(entry);
        this.userAddress = tokens[2];
        this.username = tokens[3];
        this.method = tokens[4];
        this.path = tokens[5];
        this.returnCode = Integer.parseInt(tokens[6]);
        this.reqContentLength = Long.parseLong(tokens[7]);
        this.resContentLength = Long.parseLong(tokens[8]);
        this.duration = Long.parseLong(tokens[9]);
        this.userAgent = tokens[10];
    }
}
