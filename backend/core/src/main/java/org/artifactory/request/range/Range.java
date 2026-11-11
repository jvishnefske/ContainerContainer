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

package org.artifactory.request.range;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.artifactory.request.range.ResponseWithRangeSupportHelper.MULTIPART_BYTERANGES_BOUNDRY_SEP;

/**
 * The class represent single http range
 *
 * @author Gidi Shabat
 */
@Slf4j
public class Range implements Comparable<Range> {
    private static final String NOT_SATISFIABLE_RANGE =
            "Not satisfiable range skipping range: '{}' for range header: '{}'";
    private long start;
    private long end;
    private long entityLength;
    private String contentType;
    private String contentRange;

    Range(String contentType, long start, long end, long entityLength) {
        this.contentType = HttpHeaders.CONTENT_TYPE + ": " + contentType;
        this.start = start;
        this.end = end;
        this.entityLength = entityLength;
    }

    public static Range constructSkipRange(long start, long end) {
        return new Range(null, start, end, 0);
    }

    /**
     * Factory method to construct a byte range from a range header value.
     */
    public static List<Range> constructRange(String rangesHeader, String contentType, long entityLength,
            RangeAwareContext context, IfRangeSelector ifRangeSelector) {
        ArrayList<Range> result = Lists.newArrayList();
        if (ifRangeSelector.isIfChange()) {
            return result;
        }

        if (StringUtils.isBlank(rangesHeader)) {
            return result;
        }
        String[] rangesValues = StringUtils.split(rangesHeader, "=");
        if (rangesValues.length != 2) {
            return result;
        }
        // Transform the stringRanges into List<Ranges>
        loadRanges(rangesHeader, contentType, entityLength, context, result, rangesValues[1]);
        // Merge byte ranges if possible - IE handles this well, FireFox not so much
        mergeRanges(result);
        return result;
    }

    /**
     * Validates and create ranges according to the Range header
     */
    private static void loadRanges(String rangesHeader, String contentType, long entityLength,
            RangeAwareContext context,
            ArrayList<Range> result, String rangesValues) {
        String[] realRanges = StringUtils.split(rangesValues, ",");
        for (String range : realRanges) {
            range = range.trim();
            // strip total if present - it does not give us anything useful
            if (range.indexOf('/') != -1) {
                range = range.substring(0, range.indexOf('/'));
            }
            // find the separator
            int separator = range.indexOf('-');
            try {
                // split range and parse values
                long start = prepareStart(separator, range, entityLength);
                long end = prepareEnd(separator, range, entityLength);
                // Ignore invalid range
                if (start < 0 || end < start || Math.abs(start) >= entityLength || separator == -1
                        || StringUtils.isBlank(range)) {
                    log.warn(NOT_SATISFIABLE_RANGE, range, rangesHeader);
                    continue;
                }
                // return object to represent the byte-range
                result.add(new Range(contentType, start, end, entityLength));
            } catch (NumberFormatException err) {
                log.warn(NOT_SATISFIABLE_RANGE, range, rangesHeader);
            }
        }
        // If none of the ranges in the request's Range header are valid then return SC_REQUESTED_RANGE_NOT_SATISFIABLE
        if (realRanges.length > 0 && result.size() == 0) {
            log.warn("Returning not satisfiable range status since none of the ranges in '{}' is valid", rangesHeader);
            //throw new HTTPException(HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            context.setContentRange("bytes */" + entityLength);
            context.setStatus(HttpStatus.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        }
    }

    private static long prepareStart(int separator, String range, long entityLength) {
        if (separator > 0) {
            return Long.parseLong(range.substring(0, separator).trim());
        }
        if (separator == 0) {
            // Suffix range: -N means last N bytes, so start at entityLength - N
            return entityLength - Long.parseLong(range.substring(1).trim());
        }
        return 0L;
    }

    private static long prepareEnd(int separator, String range, long entityLength) {
        if (separator != range.length() - 1 && separator != 0) {
            return Long.parseLong(range.substring(separator + 1).trim());
        }
        return entityLength - 1;
    }

    /**
     * The class merges continues ranges
     */
    private static void mergeRanges(ArrayList<Range> result) {
        if (result.size() > 1) {
            Collections.sort(result);
            for (int i = 0; i < result.size() - 1; i++) {
                Range first = result.get(i);
                Range second = result.get(i + 1);
                if (first.getEnd() + 1 >= second.getStart()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Merging byte range: " + first + " with " + second);
                    }
                    if (first.getEnd() < second.getEnd()) {
                        // merge second range into first
                        first.setEnd(second.getEnd());
                    }
                    // else we simply discard the second range - it is contained within the first

                    // delete second range
                    result.remove(i + 1);
                    // reset loop index
                    i--;
                }
            }
        }
    }


    /**
     * Output the header bytes for a multi-part byte range header
     */
    public byte[] outputHeader(boolean firstTime) throws IOException {
        StringBuilder buffer = new StringBuilder();
        // output multi-part boundry separator
        if (!firstTime) {
            buffer.append("\r\n");
        }
        buffer.append(MULTIPART_BYTERANGES_BOUNDRY_SEP).append("\r\n");
        buffer.append(this.contentType).append("\r\n");
        buffer.append(getContentRange()).append("\r\n");
        buffer.append("\r\n");
        return buffer.toString().getBytes();
    }

    /**
     * Return the length in bytes of the byte range content including the header bytes
     */
    public int getLength() {
        // length in bytes of range plus it's header plus section marker and line feed bytes
        return MULTIPART_BYTERANGES_BOUNDRY_SEP.length() + 2 +
                this.contentType.length() + 2 +
                getContentRange().length() + 4 +
                (int) (this.end - this.start) + 2;
    }

    /**
     * Return the Content-Range header string value for this byte range
     */
    private String getContentRange() {
        if (this.contentRange == null) {
            this.contentRange = "Content-Range: bytes " + Long.toString(this.start) + "-" +
                    Long.toString(this.end) + "/" + Long.toString(this.entityLength);
        }
        return this.contentRange;
    }

    @Override
    public String toString() {
        return this.start + "-" + this.end;
    }

    @Override
    public int compareTo(Range o) {
        return this.start > o.start ? 1 : -1;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    public long getEntityLength() {
        return entityLength;
    }
}