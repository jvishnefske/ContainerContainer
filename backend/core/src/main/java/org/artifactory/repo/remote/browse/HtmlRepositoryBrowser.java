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

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringEscapeUtils;
import org.artifactory.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.artifactory.util.HttpUtils.escapePlusSymbol;

/**
 * Utility class which helps to list urls under a given url. This has been tested with Apache 1.3.33 server listing, as
 * the one used at ibiblio, and with Apache 2.0.53 server listing, as the one on mirrors.sunsite.dk.
 */
public class HtmlRepositoryBrowser extends RemoteRepositoryBrowser {
    private static final Logger log = LoggerFactory.getLogger(HtmlRepositoryBrowser.class);

    private static final Pattern PATTERN = Pattern.compile(
            "<a[^>]*href=[\"|']([^\"']*)[\"|'][^>]*>(?:<[^>]+>)*?([^<>]+?)(?:<[^>]+>)*?</a>",
            Pattern.CASE_INSENSITIVE);

    public HtmlRepositoryBrowser(HttpExecutor client) {
        super(client);
    }

    @Override
    public List<RemoteItem> listContent(String url) throws IOException {
        String result = getFileListContent(url);
        return parseHtml(result, url);
    }

    List<RemoteItem> parseHtml(String htmlText, String url) throws MalformedURLException {
        List<RemoteItem> items = Lists.newArrayList();
        Matcher matcher = PATTERN.matcher(htmlText);
        URL baseUrl = new URL(escapePlusSymbol(url));
        while (matcher.find()) {
            String href = matcher.group(1);   // the link target
            String text = matcher.group(2);   // the displayed text
            if (href == null || text == null) {
                continue;  // groups not found (shouldn't happen, really)
            }

            href = resolveHref(href, baseUrl);
            if (href == null || !hrefMatchesText(href, text.trim())) {
                continue;
            }

            String childUrl = buildChildUrl(baseUrl, href);
            items.add(new RemoteItem(childUrl, href.endsWith("/")));
            log.debug("Found remote item: {}", childUrl);
        }

        return items;
    }

    /**
     * Normalises a raw href from the listing into a path relative to the browsed directory, or null
     * if it should be skipped. Handles complete-URL listings (reduced to the part below the base
     * path), parent links ("../"), root-absolute and "./"-relative hrefs, and the Ivy '+' escape.
     */
    private static String resolveHref(String href, URL baseUrl) {
        // complete URL listing: keep only the part below the directory we are browsing
        if (href.startsWith("http:") || href.startsWith("https:")) {
            String path = UrlPathExtractor.extractPath(href);
            String basePath = baseUrl.getPath();
            if (path == null || !path.startsWith(basePath)) {
                return null;
            }
            href = path.substring(basePath.length());
        }
        // we are only interested in sub-URLs, not parent URLs
        if (href.startsWith("../")) {
            return null;
        }
        // root-absolute href: reduce to the last path segment
        if (href.startsWith("/")) {
            int slashIndex = href.substring(0, href.length() - 1).lastIndexOf('/');
            href = href.substring(slashIndex + 1);
        }
        // relative to current href: convert to a simple relative one
        if (href.startsWith("./")) {
            href = href.substring("./".length());
        }
        // Special case for Ivy (+) sign - RTFACT-15708
        return escapePlusSymbol(href);
    }

    /**
     * Whether the normalised href matches the link's displayed text. The href is never truncated,
     * but Apache may truncate the text; for truncated text (ending "..&gt;", "..>" or "...") we only
     * require a prefix match, escaping '+' in the text to compare against the already-escaped href.
     */
    private static boolean hrefMatchesText(String href, String text) {
        if (text.endsWith("..>") || text.endsWith("...")) {
            return href.startsWith(escapePlusSymbol(text).substring(0, text.length() - 3));
        }
        if (text.endsWith("..&gt;")) {
            return href.startsWith(escapePlusSymbol(text).substring(0, text.length() - 6));
        }
        // non-truncated: must match the href after stripping optional trailing slashes
        String decodedHref = HttpUtils.decodeUri(stripTrailingSlash(href));
        return decodedHref.equalsIgnoreCase(StringEscapeUtils.unescapeXml(stripTrailingSlash(text)));
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * Joins the browsed directory URL and the resolved relative href with a single '/'.
     */
    private static String buildChildUrl(URL baseUrl, String href) {
        String childUrl = baseUrl.toExternalForm();
        if (!childUrl.endsWith("/")) {
            childUrl += "/";
        }
        return childUrl + href;
    }
}
