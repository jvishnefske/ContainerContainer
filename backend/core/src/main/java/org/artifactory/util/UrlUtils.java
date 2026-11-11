package org.artifactory.util;

import lombok.experimental.UtilityClass;
import okhttp3.HttpUrl;

@UtilityClass
public class UrlUtils {

    private static final boolean INVALID_URL = false;

    /**
     * Compares two URLs in a "soft" way, ignoring trailing slashes and query params
     * @return true if the URLs are considered equal, false otherwise or if either URL is invalid
     */
    public static boolean softEquals(String url1, String url2) {
        if (url1 == null || url2 == null) {
            return INVALID_URL;
        }

        url1 = url1.toLowerCase();
        url2 = url2.toLowerCase();

        // HttpUrl.parse() handles encoding, queries, etc.
        HttpUrl u1 = HttpUrl.parse(url1);
        HttpUrl u2 = HttpUrl.parse(url2);

        if (u1 == null || u2 == null) {
            return INVALID_URL;
        }

        return normalize(u1).equals(normalize(u2));
    }

    /**
     * remove query & fragment and strip trailing slash path segment
     * @return normalized url
     */
    private static HttpUrl normalize(HttpUrl url) {
        // remove query and fragment
        HttpUrl withoutQuery = url.newBuilder().query(null).fragment(null).build();
        return stripSlash(withoutQuery);
    }

    /**
     * Strip trailing slash path segment if exists
     * @param url the url to strip
     * @return url without trailing slash path segment
     */
    private static HttpUrl stripSlash(HttpUrl url) {
        if (url.pathSize() > 0 && url.pathSegments().get(url.pathSize() - 1).isEmpty()) {
            return url.newBuilder().removePathSegment(url.pathSize() - 1).build();
        }
        return url;
    }
}