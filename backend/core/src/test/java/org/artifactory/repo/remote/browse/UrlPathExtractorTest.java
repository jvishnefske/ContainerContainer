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

package org.artifactory.repo.remote.browse;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test {@link UrlPathExtractor} — its path output must match {@code new URL(href).getPath()},
 * returning null wherever that constructor would throw.
 */
class UrlPathExtractorTest {

    @Test
    void extractPathParityWithUrlGetPath() {
        String[] validUrls = {
                "http://example.com/repo/file.jar",
                "http://example.com/repo/subdir/",
                "https://host:8080/a/b/c",
                "http://example.com/path?query=1",
                "http://example.com/path#fragment",
                "http://example.com/path?q=1#frag",
                "http://host/",
                "http://[::1]/ipv6/path",
                "http://[2001:db8::1]:8080/ipv6/with/port",
                "http://user:pass@host/userinfo/path",
        };
        for (String url : validUrls) {
            String expected = URI.create(url).getPath();
            String actual = UrlPathExtractor.extractPath(url);
            assertEquals(expected, actual, "extractPath parity failed for: " + url);
        }
    }

    @Test
    void extractPathRejectsInvalidAuthority() {
        String[] invalidUrls = {
                "http://bad host/repo/file.jar",           // space in authority
                "http://host:abc/repo/file.jar",           // non-numeric port
                "http://host:80abc/repo/file.jar",         // mixed port (digits then alpha)
                "http://host:abc80/repo/file.jar",         // mixed port (alpha then digits)
                "http://host:-5/repo/file.jar",            // negative port — new URL() rejects it
                "http://host|name/repo/file.jar",          // pipe in authority
                "http://host\\name/repo/file.jar",         // backslash in authority
                "http://host^name/repo/file.jar",          // caret in authority
                "http://host{name}/repo/file.jar",         // braces in authority
                "http://h<ost/repo/file.jar",              // '<' in authority — new URL() rejects it
                "http://h>ost/repo/file.jar",              // '>' in authority — new URL() rejects it
                "relative/path/only",                      // no scheme separator — no ://
        };
        for (String url : invalidUrls) {
            assertNull(UrlPathExtractor.extractPath(url), "Expected null for: " + url);
        }
    }

    /**
     * extractPath must reproduce {@code new URL(href).getPath()} exactly — returning null where
     * that constructor throws — including the JDK's quirky port acceptance (it parses the port as
     * an int and rejects only values below -1, so ":-0"/":-00"/":-01" are accepted but ":-5" is
     * not). new URL() is the real oracle here: URI.create() disagrees on these inputs.
     */
    @Test
    void extractPathPortAndPathlessParityWithNewUrl() {
        String[] ports = {"", "8080", "0", "00", "-0", "-00", "-01", "-1", "-5", "+5", "+0",
                "abc", "80abc", "8.0", "2147483647", "2147483648", "99999999999", "-2147483648"};
        for (String port : ports) {
            assertPathMatchesNewUrl("http://host:" + port + "/repo/file.jar");
            assertPathMatchesNewUrl("http://host:" + port);
        }
        // path-less authorities: new URL().getPath() is "" (not "/")
        for (String url : new String[] {"http://host", "http://host:8080", "https://a.b.c",
                "http://user@host", "http://[::1]", "http://[2001:db8::1]:443"}) {
            assertPathMatchesNewUrl(url);
        }
        // userinfo: java.net.URL is lenient about chars before '@', so extractPath defers to it.
        // These (space, '|', '\\', multiple '@') are accepted by new URL() and must match.
        for (String url : new String[] {"http://user@host/p", "http://user:pass@host/p",
                "http://us er@host/p", "http://a|b@host/p", "http://a\\b@host/p",
                "http://a@b@host/p", "http://user@h ost/p"}) {
            assertPathMatchesNewUrl(url);
        }
    }

    /**
     * Scheme-relative forms (scheme present, no "://"), IPv6 literals, and malformed authorities that
     * the fast scan does not model — all must still equal {@code new URL(href).getPath()} because
     * extractPath defers them to the real parser. Guards the RTDEV-89382 review regressions.
     */
    @Test
    void extractPathDeferredFormsParityWithNewUrl() {
        String[] urls = {
                // scheme-relative: new URL() parses these; the fast scan has no "://" to anchor on
                "http:/repo/file.jar", "http:foo", "https:/a/b/c", "http:./bar",
                // IPv6 literals — valid (deferred and resolved) ...
                "http://[::1]/p", "http://[2001:db8::1]:8080/x",
                // ... and malformed (new URL() throws → extractPath must return null)
                "http://[::1]extra/p", "http://[g::1]/p", "http://[::1:/p", "http://a]b/p",
                // multi-colon authority — malformed, new URL() throws
                "http://host:80:90/p",
        };
        for (String url : urls) {
            assertPathMatchesNewUrl(url);
        }
    }

    /**
     * Differential parity sweep over the domain the fast scan actually parses itself — a plain
     * {@code host[:port]} authority with a path. Across the cartesian product of host / port / path,
     * extractPath must equal {@code new URL(href).getPath()} (or null where new URL() throws) for
     * every combination. These are generated rather than hand-picked, so coverage isn't limited to
     * the cases the author thought of — this is what catches scan-vs-oracle divergences like the
     * malformed-port and host-char regressions found reviewing RTDEV-89382. Deliberately excludes
     * userinfo / IPv6 / multi-colon forms: those are deferred to new URL() (so parity is trivially
     * true) and are exercised by {@link #extractPathDeferredFormsParityWithNewUrl} instead.
     */
    @Test
    void extractPathDifferentialParitySweep() {
        String[] hosts = {"example.com", "host", "h", "1.2.3.4", "my-repo.jfrog.io",
                "sub.domain.org", "a-b1.c", "xn--80ak6aa92e.com", "ex" + (char) 0xE4 + "mple.com"};
        String[] ports = {"", ":8080", ":443", ":0", ":65535", ":1",
                ":abc", ":8a", ":-5", ":-0", ":+5", ":99999999999"};
        String[] paths = {"", "/", "/repo/a-1.0.jar", "/sub/", "/a/b/c", "/p?q=1", "/p#f",
                "/p?q=1#f", "/a%20b.jar", "/a+b.jar"};
        for (String host : hosts) {
            for (String port : ports) {
                for (String path : paths) {
                    assertPathMatchesNewUrl("http://" + host + port + path);
                }
            }
        }
    }

    private static void assertPathMatchesNewUrl(String url) {
        String expected;
        try {
            expected = new URL(url).getPath();
        } catch (MalformedURLException e) {
            expected = null;  // a throw means parseHtml skipped the href — extractPath returns null
        }
        assertEquals(expected, UrlPathExtractor.extractPath(url), "parity with new URL() for: " + url);
    }
}
