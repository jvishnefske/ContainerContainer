package org.artifactory.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlUtilsTest {

    @Test
    void softEquals_returnsFalseForNulls() {
        assertFalse(UrlUtils.softEquals(null, "http://example.com"));
        assertFalse(UrlUtils.softEquals("http://example.com", null));
        assertFalse(UrlUtils.softEquals(null, null));
    }

    @Test
    void softEquals_returnsFalseForInvalidUrls() {
        assertFalse(UrlUtils.softEquals("https://%", "http://example.com"));
        assertFalse(UrlUtils.softEquals("http://example.com", "https://%"));
        assertFalse(UrlUtils.softEquals("https://%", "https://%"));
    }

    @Test
    void softEquals_ignoresTrailingSlash() {
        assertTrue(UrlUtils.softEquals("https://example.com/path", "https://example.com/path/"));
        assertTrue(UrlUtils.softEquals("https://example.com/path/", "https://example.com/path"));
    }

    @Test
    void softEquals_caseInsensitive() {
        assertTrue(UrlUtils.softEquals("https://EXAMPLE.com/PATH", "https://example.COM/path/"));
    }

    @Test
    void softEquals_ignoresQueryAndFragment() {
        assertTrue(UrlUtils.softEquals("https://example.com/path?query=1#frag", "https://example.com/path"));
        assertTrue(UrlUtils.softEquals("https://example.com/path#frag", "https://example.com/path?other=2"));
    }

    @Test
    void softEquals_differentHostsOrPaths() {
        assertFalse(UrlUtils.softEquals("https://example.com/path", "https://other.com/path"));
        assertFalse(UrlUtils.softEquals("https://example.com/path1", "https://example.com/path2"));
    }

    @Test
    void softEquals_considersDefaultAndExplicitPort() {
        // okHttp includes port in the equality if specified, so 80 vs unspecified (https default 443) differ
        assertFalse(UrlUtils.softEquals("http://example.com:8080/path", "http://example.com/path"));
        assertTrue(UrlUtils.softEquals("https://example.com:443/path", "https://example.com/path"));
    }
}