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

package org.artifactory.traffic;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.traffic.entry.RequestEntry;
import org.slf4j.MDC;

/**
 * Logger class for general web requests.
 * An example of log line:
 * <code>20140508154145|2632|REQUEST|82:12:13:14|admin|GET|/jcenter/org/iostreams-0.2.jar|HTTP/1.1|200|8296</code>
 * The columns are pipe delimited:
 * <ol>
 *  <li>Log entry date (the end of the request) in the format of YYYYMMDDHHMMSS</li>
 *  <li>Processing time in millis</li>
 *  <li>Request type ({@link org.artifactory.traffic.TrafficAction})</li>
 *  <li>IP address of the requesting user</li>
 *  <li>Username (non_authenticated_user for anonymous)</li>
 *  <li>The HTTP request method</li>
 *  <li>The requested resource path</li>
 *  <li>The HTTP protocol version</li>
 *  <li>HTTP response code</li>
 *  <li>Content length in byes of the response in case of HEAD and GET requests and of the request in case of PUT and
 *  POST requests</li>
 * </ol>
 *
 * @author Noam Tenne
 */
@Slf4j
public abstract class RequestLogger {

    private RequestLogger() {
        // utility class
    }

    public static void request(RequestEntry entry) {
        final Object[] args = entryToArgs(entry);
        // Note: ideally, we should use log.atLevel().addKeyValue("entry", entry) to pass data to logger.
        //       Unfortunately, features of Logback 2.0 are unavailable for Artifactory until we solve RTDEV-58341
        //       As a temporary solution, we will pass the dava as an extra argument.
        log.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", args);
    }

    private static Object[] entryToArgs(final RequestEntry entry) {
        return new Object[] {
                entry.getFormattedDate(),
                MDC.get("uber-trace-id"),
                entry.getUserAddress(),
                entry.getUsername(), entry.getMethod(), entry.getPath(), entry.getReturnCode(),
                entry.getReqContentLength(), entry.getResContentLength(), entry.getDuration(), entry.getUserAgent()
                // Temporarily add metadata as an extra argument
                , entry.getMetadata()
        };
    }
}