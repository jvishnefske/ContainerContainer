/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jfrog.storage.binstore.ifc.RedirectType;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.MDC;

import java.util.regex.Pattern;

/**
 * Entry for data transfer traffic entries. Intended to be used for Traffic Log V2 and V3.
 * <p/>
 * $date|$traceId|$action|$actor|$redirectTarget|$remoteAddress|$repoPath|$size|$requestedSize|$transferredSize|
 * $repoKey|$projectKey|$username|$repoType|$packageType|
 * $originRepoKey|$originProjectKey|$originRepoType|$originPackageType
 * <p>
 * Example:
 * <pre>
 * 20200608005250|2de5e67cc72235bb|DOWNLOAD|USER|DIRECT|123.123.123.123|
 * nuget-local:Application/Seismic.LiveDoc.Engine.6.12.0.nupkg|9274323|8000000|7368479|nuget-local|proj-a|
 * anonymous|local|nuget|nuget-virtual|proj-b|virtual|nuget
 *
 * 20200608005250|2de5e67cc72235bb|REDIRECT|XRAY|S3|123.123.123.123|
 * nuget-local:Application/Seismic.LiveDoc.Engine.6.12.0.nupkg|9274323|9274323|9274323|nuget-local|default|
 * anonymous|local|nuget|nuget-virtual|default|virtual|nuget
 * </pre>
 *
 * @author Rotem Kfir
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficEntry {

    private static final DateTimeFormatter ENTRY_DATE_FORMATTER = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    private String traceId;
    private TrafficAction action;
    private TrafficActor actor;
    private TrafficTarget redirectTarget;
    private String userAddress;
    private String repoPath;
    /**
     * The full size (in bytes) of the blob
     */
    private long size;
    /**
     * The number of bytes requested, e.g. in a range request, or the full size
     */
    private long requestedSize;
    /**
     * The actual number of bytes transferred
     */
    private long transferredSize;

    @Setter
    private String repoKey;
    @Setter
    private String projectKey;
    @Setter
    private String username;
    @Setter
    private String repoType;
    @Setter
    private String packageType;
    @Setter
    private String originRepoKey;
    @Setter
    private String originProjectKey;
    @Setter
    private String originRepoType;
    @Setter
    private String originPackageType;

    private static final Pattern UNSAFE_LOG_CHARS = Pattern.compile("[\r\n]");

    private String getFormattedDate() {
        return ENTRY_DATE_FORMATTER.print(System.currentTimeMillis());
    }

    private String getTraceId() {
        if (traceId != null) {
            return traceId;
        }
        return MDC.get("uber-trace-id");
    }

    @Override
    public String toString() {
        return String.join("|",
                getFormattedDate(),
                escapeLineBreaks(getTraceId()),
                getAction().name(),
                getActor().name(),
                getRedirectTarget() == null ? "" : getRedirectTarget().name(),
                escapeLineBreaks(getUserAddress()),
                escapeLineBreaks(getRepoPath()),
                String.valueOf(getSize()),
                String.valueOf(getRequestedSize()),
                String.valueOf(getTransferredSize()),
                escapeLineBreaks(getRepoKey()),
                escapeLineBreaks(getProjectKey()),
                escapeLineBreaks(getUsername()),
                escapeLineBreaks(getRepoType()),
                escapeLineBreaks(getPackageType()),
                escapeLineBreaks(getOriginRepoKey()),
                escapeLineBreaks(getOriginProjectKey()),
                escapeLineBreaks(getOriginRepoType()),
                escapeLineBreaks(getOriginPackageType())
        );
    }

    private static String escapeLineBreaks(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf('\r') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return UNSAFE_LOG_CHARS.matcher(value).replaceAll("");
    }

    public enum TrafficAction {
        DOWNLOAD, UPLOAD, REDIRECT
    }

    /**
     * The type of the actor who initiated the request
     */
    public enum TrafficActor {
        USER,
        XRAY,
        REPLICATOR,
        PIPELINES,
        FEDERATION,
        ARTIFACTORY,
        OBSERVABILITY,
        DISTRIBUTION,
        JFCONNECT,
        JFLINK,
        XRAY_REPETITIVE,
        BA_BILLING_LOGSHIPPING,
        XRAY_CREDIT,
        ARCHIVE,
        REPLICATION,
        SMART_REMOTE
    }

    /**
     * The target type of the transfer
     */
    public enum TrafficTarget {
        DIRECT, // in case the action is not REDIRECT
        S3, // redirect to Amazon S3
        CF, // redirect to Amazon CloudFront CDN
        GCS, // redirect to Google Cloud Service
        ABS, // redirect to Azure Blob Storage
        PDN, // redirect to JFrog Private Distribution Network
        CONTROLLED_CACHE, // redirect to reverse proxy for smart caching
        ;

        public static TrafficTarget valueOf(RedirectType redirectType) {
            if (redirectType == null) {
                return DIRECT;
            }
            return switch (redirectType) {
                case S3 -> S3;
                case CLOUD_FRONT -> CF;
                case GCS -> GCS;
                case ABS -> ABS;
                case PDN -> PDN;
                default -> DIRECT;
            };
        }

        public String displayName() {
            if (this == CONTROLLED_CACHE) {
                return "cc";
            }
            return name().toLowerCase();
        }
    }
}
