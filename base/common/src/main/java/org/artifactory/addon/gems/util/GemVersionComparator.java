package org.artifactory.addon.gems.util;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RubyGems-aligned version comparison utilities.
 */
@UtilityClass
public class GemVersionComparator {

    private static final Pattern VERSION_SEGMENT_PATTERN = Pattern.compile("\\d+|[a-zA-Z]+");

    public static int compareVersions(String first, String second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return -1;
        }
        if (second == null) {
            return 1;
        }
        return compareSegments(parseVersion(first), parseVersion(second));
    }

    @SuppressWarnings("java:S1452") // segments may be numeric or alpha; Comparable<?> preserves type safety
    public static List<Comparable<?>> parseVersion(String version) {
        List<Comparable<?>> segments = new ArrayList<>();
        Matcher matcher = VERSION_SEGMENT_PATTERN.matcher(version);
        while (matcher.find()) {
            String segment = matcher.group();
            if (segment.matches("\\d+")) {
                segments.add(Long.parseLong(segment));
            } else {
                segments.add(segment);
            }
        }
        return segments;
    }

    /**
     * Based on rubygems version comparison, we are splitting each version to segments, e.g. 1.1.0.rc1 -> [1, 1,
     * 0, rc1], and then we are comparing segment by segment, if both are strings or ints, we use the regular
     * compare, otherwise we consider string as a pre-release which is lower than a number
     * e.g. 1.1.0.rc1 < 1.1.0.1
     **/
    public static int compareSegments(@NonNull List<Comparable<?>> leftSegments,
            @NonNull List<Comparable<?>> rightSegments) {
        int limit = Math.max(leftSegments.size(), rightSegments.size());
        for (int i = 0; i < limit; i++) {
            Comparable<?> lhs = getValueFromSegment(i, leftSegments);
            Comparable<?> rhs = getValueFromSegment(i, rightSegments);

            if (lhs.equals(rhs)) {continue;}

            if ((lhs instanceof Number) && rhs instanceof String) {return 1;}
            if (lhs instanceof String && (rhs instanceof Number)) {return -1;}

            int result;
            if (lhs instanceof Number && rhs instanceof Number) {
                // If both are Numbers
                Long lhsLong = Long.parseLong(lhs.toString());
                Long rhsLong = Long.parseLong(rhs.toString());
                result = (lhsLong).compareTo(rhsLong);
            } else {
                // else both are Strings
                result = lhs.toString().compareTo(rhs.toString());
            }

            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private Comparable<?> getValueFromSegment(int index, List<Comparable<?>> segments) {
        return index < segments.size() ? segments.get(index) : 0;
    }
}
