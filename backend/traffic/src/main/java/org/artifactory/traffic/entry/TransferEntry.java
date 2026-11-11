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

import org.apache.commons.lang3.StringUtils;
import org.artifactory.traffic.TrafficAction;
import org.slf4j.MDC;

/**
 * Base entry for data transfer traffic entries.
 * <p/>
 * $date|$traceId|$duration|$action|$remoteAddress|$repoPath|$contentLength
 * Example:
 * <pre>
 * 20090318162747|4b1b8a0b04e31b80|110|UPLOAD|10.0.0.10|libs-releases-local:antlr/antlr/2.7.7/antlr-2.7.7.pom|632
 * </pre>
 *
 * @author Yossi Shaul
 * @deprecated for the purpose of sunsetting the billing system that is based on Traffic Log V1
 */
@Deprecated(since = "7.120.0")
public abstract class TransferEntry extends TokenizedTrafficEntry {
    static final int COLUMNS_COUNT_SPEC = 7;

    private String traceId;
    private String repoPath;
    private String userAddress;
    private String userIdentifier = "";
    private long contentLength;

    /**
     * Parses the given textual entry and sets the object fields accordingly
     * Used for reading an entry from the log file (See TrafficResource).
     *
     * @param entry Textual entry
     */
    TransferEntry(String entry) {
        String[] tokens = parseEntry(entry);
        int entryLength = StringUtils.splitPreserveAllTokens(entry, COLUMN_SEPARATOR).length;
        int i = COLUMNS_COUNT_SPEC;
        if (entryLength != COLUMNS_COUNT_SPEC) {
            // This is to support old log format that contained no traceId. Can remove it in future version
            i--;
            traceId = "";
        } else {
            traceId = tokens[1];
        }
        contentLength = Long.parseLong(tokens[--i]);
        repoPath = tokens[--i];
        userAddress = tokens[--i];
    }

    /**
     * Sets the given entry data in the relevant fields
     *
     * @param repoPath      Requested artifact repo path
     * @param contentLength Requested artifact size
     */
    public TransferEntry(String repoPath, long contentLength, long duration, String userAddress) {
        super(duration);
        this.userAddress = userAddress;
        this.repoPath = repoPath;
        this.contentLength = contentLength;
    }

    public TransferEntry(String repoPath, long contentLength, long duration, String userAddress,
            String userIdentifier) {
        super(duration);
        this.userAddress = userAddress;
        this.repoPath = repoPath;
        this.contentLength = contentLength;
        this.userIdentifier = userIdentifier;
    }

    @Override
    protected String[] initTokens() {
        int i = 0;
        String[] tokens = new String[COLUMNS_COUNT_SPEC];
        tokens[i++] = getFormattedDate();
        tokens[i++] = getTraceId();
        tokens[i++] = getDuration() + "";
        tokens[i++] = getAction().name();
        tokens[i++] = userAddress;
        tokens[i++] = repoPath;
        tokens[i] = contentLength + "";
        return tokens;
    }


    public String getTraceId() {
        if (traceId != null) {
            return traceId;
        }
        return MDC.get("uber-trace-id");
    }

    @Override
    public TrafficAction getAction() {
        return TrafficAction.UPLOAD;
    }

    @Override
    public int getColumnsCount() {
        return COLUMNS_COUNT_SPEC;
    }

    @Override
    String getDuration(String[] tokens) {
        int i = 2;
        if (tokens.length != COLUMNS_COUNT_SPEC) { // No traceId
            i--;
        }
        return tokens[i];
    }

    /**
     * Returns the requested artifact's repo path
     *
     * @return String - Repo path of requested artifact
     */
    public String getRepoPath() {
        return repoPath;
    }

    /**
     * Returns the requested artifact's size
     *
     * @return long - Size of requested artifact
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * Returns the address of the client's machine
     *
     * @return String - Address of client machine
     */
    public String getUserAddress() {
        return userAddress;
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }
}
