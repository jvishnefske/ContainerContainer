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

import org.artifactory.request.range.stream.MultiRangeInputStream;
import org.artifactory.request.range.stream.SingleRangeInputStream;
import org.artifactory.request.range.stream.SingleRangeSkipInputStream;
import org.jfrog.storage.binstore.ifc.SkippableInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static jakarta.servlet.http.HttpServletResponse.SC_PARTIAL_CONTENT;
import static org.artifactory.common.ConstantValues.httpRangeSupport;
import static org.artifactory.request.range.IfRangeSelector.constructIfRange;
import static org.artifactory.request.range.Range.constructRange;

/**
 * The class creates context that contains the data needed to decorate the httpResponse in order to support HTTP ranges
 *
 * @author Gidi Shabat
 */
public class ResponseWithRangeSupportHelper {
    private static final String MULTIPART_BYTERANGES_BOUNDRY = "BCD64322345343217845286A";
    private static final String MULTIPART_BYTERANGES_HEADER =
            "multipart/byteranges; boundary=" + MULTIPART_BYTERANGES_BOUNDRY;
    static final String MULTIPART_BYTERANGES_BOUNDRY_SEP = "--" + MULTIPART_BYTERANGES_BOUNDRY;
    public static final String MULTIPART_BYTERANGES_BOUNDRY_END = MULTIPART_BYTERANGES_BOUNDRY_SEP + "--";
    private static final Logger log = LoggerFactory.getLogger(ResponseWithRangeSupportHelper.class);

    /**
     * Prepares range aware context, taking in account parameters from If-Range and Range headers
     * Also creates Input stream according to required data ranges
     * If you need to know only data size please see #calculateContentLength method
     */
    public static RangeAwareContext createRangeAwareContext(InputStream in, long length, String rangesString,
            String ifRange, String mimeType, long lastModified, String sha1, String userAgent) throws IOException {
        RangeAwareContext context = new RangeAwareContext();
        // resolve the If-Range behaviour from the If-Range header
        IfRangeSelector ifRangeSelector = constructIfRange(ifRange, lastModified, sha1);
        // Resolve the ranges from the ranges header
        List<Range> ranges = constructRange(rangesString, mimeType, length, context, ifRangeSelector);
        // Do not continue if error occurred
        if (context.getStatus() > 0) {
            return context;
        }
        // Fill the result context according to the ranges.
        if (!httpRangeSupport.getBoolean() || ranges.isEmpty()) {
            handleSimpleResponse(in, length, mimeType, context);
        } else if (ranges.size() == 1) {
            handleSingleRangeResponse(in, ranges, mimeType, context);
            log.info("Single range request for {}: range of {} bytes, partial content: {}, User-Agent: {}",
                    sha1, context.getContentLength(), context.getContentRange(), userAgent == null ? "N/A" : userAgent);
        } else {
            handleMultiRangeResponse(in, ranges, context);
            log.info("Multiple range request for {}: partial content: [{}] out of {} bytes, User-Agent: {}",
                    sha1,
                    ranges.stream().map(r -> r.getStart() + "-" + r.getEnd()).collect(Collectors.joining(", ")),
                    length, userAgent == null ? "N/A" : userAgent);
        }
        return context;
    }

    /**
     * Calculates data size taking in account parameters from If-Range and Range headers
     */
    public static long calculateContentLength(long length, String rangesString,
            String ifRange, long lastModified, String sha1, boolean multiRangeSupports) {
        RangeAwareContext context = new RangeAwareContext();
        IfRangeSelector ifRangeSelector = constructIfRange(ifRange, lastModified, sha1);
        List<Range> ranges = constructRange(rangesString, "", length, context, ifRangeSelector);
        if (context.getStatus() > 0) {
            return length;
        }
        if (ranges.isEmpty()) {
            resolveSimpleRange(length, context);
        } else if (ranges.size() == 1) {
            resolveSingleRange(ranges, context);
        } else if (multiRangeSupports) {
            resolveMultiRange(ranges, context);
        } else {
            return length;
        }
        return context.getContentLength();
    }

    private static void handleSimpleResponse(InputStream in, long length, String mimeType, RangeAwareContext context) {
        log.debug("Preparing response for simple response (None range response)");
        // Update headers, content type, content length and logs
        resolveSimpleRange(length, context);
        context.setContentType(mimeType);
        context.setInputStream(in);
    }

    private static void resolveSimpleRange(long length, RangeAwareContext context) {
        context.setContentLength(length);
    }

    private static void handleSingleRangeResponse(InputStream in, List<Range> ranges, String mimeType,
            RangeAwareContext context) throws IOException {
        log.debug("Preparing response for single range response");
        // Resolve range
        Range range = resolveSingleRange(ranges, context);
        context.setContentType(mimeType);
        context.setStatus(SC_PARTIAL_CONTENT);
        if (isSkippable(in)) {
            context.setInputStream(new SingleRangeSkipInputStream(range, in));
        } else {
            context.setInputStream(new SingleRangeInputStream(range, in));
        }
    }

    private static Range resolveSingleRange(List<Range> ranges, RangeAwareContext context) {
        Range range = ranges.get(0);
        String totalLength;
        long end;
        if (range.getEntityLength() >= 0) {
            totalLength = Long.toString(range.getEntityLength());
            end = Math.min(range.getEnd(), range.getEntityLength() - 1L);
        } else {
            totalLength = "*";
            end = range.getEnd();
        }
        String contentRange = "bytes " + range.getStart() + "-" + end + "/" + totalLength;
        context.setContentRange(contentRange);
        context.setContentLength(end - range.getStart() + 1L);
        return range;
    }

    private static boolean isSkippable(InputStream inputStream) {
        return !(inputStream instanceof SkippableInputStream) || ((SkippableInputStream) inputStream).isSkippable();
    }

    private static void handleMultiRangeResponse(InputStream in, List<Range> ranges, RangeAwareContext context) {
        log.debug("Preparing response for multi range response");
        // calculate response content length
        resolveMultiRange(ranges, context);
        context.setStatus(SC_PARTIAL_CONTENT);
        context.setContentType(MULTIPART_BYTERANGES_HEADER);
        context.setInputStream(new MultiRangeInputStream(ranges, in));
    }

    private static void resolveMultiRange(List<Range> ranges, RangeAwareContext context) {
        long length = (long) MULTIPART_BYTERANGES_BOUNDRY_END.length() + 4;
        for (Range r : ranges) {
            length += r.getLength();
        }
        context.setContentLength(length);
    }
}
