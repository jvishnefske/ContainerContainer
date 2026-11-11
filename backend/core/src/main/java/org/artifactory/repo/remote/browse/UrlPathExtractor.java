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

import lombok.experimental.UtilityClass;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Extracts the path component of an absolute http(s) URL string, returning the same value as
 * {@code new URL(url).getPath()} — and null where that constructor would throw — but without the
 * per-href {@link MalformedURLException} allocation seen when parsing remote HTML directory listings
 * (RTDEV-89382). A char-table scan over the authority handles the common case; only the rare userinfo
 * authority defers to {@link URL} to stay byte-for-byte compatible.
 */
@UtilityClass
public class UrlPathExtractor {

    // Authority-scan action table: 0=boring (continue), 1=invalid (null), 2=delimiter (/,?,#),
    //   3=defer to new URL() ('@' userinfo or '[' IPv6), 6=':' (port colon).
    // Single byte read per char replaces a branch chain; boring chars exit the loop body immediately.
    // The invalid set mirrors the chars java.net.URL rejects in a host, so the fast scan skips exactly
    // the hrefs the old new URL(href) path skipped. Anything the scan does not fully model — userinfo,
    // IPv6 literals, multi-colon authorities — defers to new URL(); only actually-hit chars cost work,
    // so the common valid host runs the scan with no extra cost (see extractPath).
    private final byte[] AUTH_ACTION = new byte[128];

    static {
        for (int i = 0; i <= ' '; i++) {
            AUTH_ACTION[i] = 1;
        }
        AUTH_ACTION[127] = 1;
        AUTH_ACTION['"'] = 1;
        AUTH_ACTION['\\'] = 1;
        AUTH_ACTION['|'] = 1;
        AUTH_ACTION['^'] = 1;
        AUTH_ACTION['`'] = 1;
        AUTH_ACTION['{'] = 1;
        AUTH_ACTION['}'] = 1;
        AUTH_ACTION['<'] = 1;
        AUTH_ACTION['>'] = 1;
        AUTH_ACTION[']'] = 1;   // ']' without a matching '[' is not a valid host char
        AUTH_ACTION['/'] = 2;
        AUTH_ACTION['?'] = 2;
        AUTH_ACTION['#'] = 2;
        AUTH_ACTION['@'] = 3;   // userinfo — defer to new URL()
        AUTH_ACTION['['] = 3;   // IPv6 literal — defer to new URL()
        AUTH_ACTION[':'] = 6;
    }

    // findPathStart() sentinels (negative, so they never collide with a real path-start index).
    private final int INVALID_AUTHORITY = -1;   // host new URL() would reject → skip the href (no exception)
    private final int DELEGATE_TO_URL = -2;   // userinfo / IPv6 / odd authority → defer to new URL()

    /**
     * Extracts the path component from an absolute URL string without throwing, returning the same
     * value as {@code new URL(absoluteUrl).getPath()} — and null where that constructor would throw.
     * Returns null for URLs whose authority is structurally invalid (control chars, spaces,
     * RFC-disallowed chars, or a port java.net.URL rejects), preserving skip-on-malformed behaviour.
     */
    String extractPath(String absoluteUrl) {
        int authStart = authorityStart(absoluteUrl);
        if (authStart < 0) {
            // Scheme without "://" (e.g. "http:/path" or "http:opaque"); the fast scan assumes an
            // authority follows, so let new URL() resolve it — exactly as the original code did.
            return parseWithUrl(absoluteUrl);
        }
        int len = absoluteUrl.length();
        int pathStart = findPathStart(absoluteUrl, authStart, len);
        if (pathStart == INVALID_AUTHORITY) {
            return null;
        }
        if (pathStart == DELEGATE_TO_URL) {
            return parseWithUrl(absoluteUrl);
        }
        // pathStart is the index of the path (or len for a path-less authority, which yields "").
        return pathComponent(absoluteUrl, pathStart, len);
    }

    /**
     * Index just past the scheme separator for an http(s) URL, or -1 if there is no "://".
     * Fast-paths the only two schemes parseHtml passes us; falls back to indexOf for anything else.
     */
    private int authorityStart(String absoluteUrl) {
        if (absoluteUrl.startsWith("http://")) {
            return 7;
        }
        if (absoluteUrl.startsWith("https://")) {
            return 8;
        }
        int schemeEnd = absoluteUrl.indexOf("://");
        return schemeEnd < 0 ? -1 : schemeEnd + 3;
    }

    /**
     * Scans the authority and returns where the path begins: the index of the first '/', '?' or '#'
     * (or {@code len} when the authority runs to the end, i.e. a path-less URL), or one of the
     * {@link #INVALID_AUTHORITY} / {@link #DELEGATE_TO_URL} sentinels. The {@code AUTH_ACTION} table
     * keeps the common host char on the cheapest branch; only a plain {@code host[:port]} authority
     * is scanned here — userinfo, IPv6 literals and multi-colon authorities are deferred to new URL().
     */
    private int findPathStart(String absoluteUrl, int authStart, int len) {
        int portColon = -1;
        for (int i = authStart; i < len; i++) {
            char c = absoluteUrl.charAt(i);
            // Non-ASCII is valid in an authority (IDN) — skip all remaining checks.
            if (c >= 128) {
                continue;
            }
            int action = AUTH_ACTION[c];
            if (action == 0) {
                continue;  // boring host char: letter, digit, '-', '.', etc.
            }
            switch (action) {
                case 1:   // invalid in a host — but legal in userinfo, which java.net.URL parses
                    // loosely. If an '@' follows before the path this char is userinfo, so defer to
                    // the real parser; otherwise it is a genuinely malformed host → skip. The
                    // lookahead runs only when an invalid char is hit, never on the common path.
                    return userinfoFollows(absoluteUrl, i + 1, len) ? DELEGATE_TO_URL : INVALID_AUTHORITY;
                case 2:   // path/query/fragment delimiter
                    return portColon >= 0 && !isValidPort(absoluteUrl, portColon + 1, i)
                            ? INVALID_AUTHORITY : i;
                case 3:   // '@' (userinfo) or '[' (IPv6 literal) — rules the fast scan does not model;
                    return DELEGATE_TO_URL;   // hand the whole href to new URL() (rare, so cost is fine)
                default:  // 6: ':' — port colon; a second one is a malformed authority → defer
                    if (portColon >= 0) {
                        return DELEGATE_TO_URL;
                    }
                    portColon = i;
                    break;
            }
        }
        // Path-less authority: still validate a trailing port so "http://host:abc" is rejected like
        // new URL() rejects it. len signals "no path"; pathComponent turns that into "".
        return portColon >= 0 && !isValidPort(absoluteUrl, portColon + 1, len) ? INVALID_AUTHORITY : len;
    }

    /**
     * Returns the path slice of {@code absoluteUrl} starting at {@code pathStart}, trimmed at the
     * first '?' or '#'. A {@code pathStart} of {@code len} (path-less authority) yields "", matching
     * {@code new URL("http://host").getPath()}.
     */
    private String pathComponent(String absoluteUrl, int pathStart, int len) {
        int queryStart = absoluteUrl.indexOf('?', pathStart);
        int fragmentStart = absoluteUrl.indexOf('#', pathStart);
        int pathEnd = len;
        if (queryStart >= 0) {
            pathEnd = Math.min(pathEnd, queryStart);
        }
        if (fragmentStart >= 0) {
            pathEnd = Math.min(pathEnd, fragmentStart);
        }
        return absoluteUrl.substring(pathStart, pathEnd);
    }

    /**
     * Returns true if an '@' (userinfo separator) appears in {@code [from, len)} before the path,
     * i.e. before the first '/', '?' or '#'. Used to tell a malformed host apart from a perfectly
     * legal userinfo char; an '@' inside the path (e.g. Go module "@v/list") is correctly ignored.
     */
    private boolean userinfoFollows(String absoluteUrl, int from, int len) {
        for (int i = from; i < len; i++) {
            char c = absoluteUrl.charAt(i);
            if (c == '@') {
                return true;
            }
            if (c == '/' || c == '?' || c == '#') {
                return false;
            }
        }
        return false;
    }

    /**
     * Delegates to {@code new URL(...).getPath()}, returning null instead of throwing — used for the
     * rare userinfo authorities the fast scan does not model, so behaviour matches the old code path.
     */
    private String parseWithUrl(String absoluteUrl) {
        try {
            return new URL(absoluteUrl).getPath();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /**
     * Mirrors the port acceptance of {@code new URL(...)} for the substring {@code [start, end)}
     * without allocating or throwing. An empty range is accepted (RFC 2396 permits an empty port).
     * Otherwise the range must parse as a base-10 {@code int} (optional single leading '+'/'-',
     * one or more digits, within int range) whose value is {@code >= -1}; java.net.URL throws for
     * anything else (non-numeric, out-of-range, or a port below -1 such as ":-5").
     */
    private boolean isValidPort(String s, int start, int end) {
        if (start >= end) {
            return true;  // empty port — new URL() treats it as unspecified
        }
        int i = start;
        boolean negative = false;
        char first = s.charAt(i);
        if (first == '+' || first == '-') {
            negative = first == '-';
            i++;
            if (i >= end) {
                return false;  // sign with no digits — parseInt would throw
            }
        }
        long value = 0;
        for (; i < end; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;  // non-digit — parseInt would throw
            }
            value = value * 10 + (c - '0');
            if (value > 2147483648L) {
                return false;  // magnitude exceeds int range — parseInt would throw
            }
        }
        if (negative) {
            value = -value;
        }
        // int-range guard (positive overflow) and new URL()'s rejection of ports below -1.
        return value <= 2147483647L && value >= -1;
    }
}
