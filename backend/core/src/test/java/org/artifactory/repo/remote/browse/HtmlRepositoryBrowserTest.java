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

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.jfrog.common.ResourceUtils.getResourceAsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test {@link HtmlRepositoryBrowser}.
 *
 * @author Tomer Cohen
 */
class HtmlRepositoryBrowserTest extends ArtifactoryHomeBoundJUnitTest {

    private HtmlRepositoryBrowser urlLister;
    private String baseUrl;
    private static final String testResourcesPath = "/org/artifactory/repo/remote/browse/html";

    @BeforeEach
    void setUp() {
        final CloseableHttpClient hc = HttpClients.createDefault();
        HttpExecutor httpExecutor = hc::execute;
        urlLister = new HtmlRepositoryBrowser(httpExecutor);
        baseUrl = "http://blabla";
    }

    @Test
    void listAllInvalid() throws IOException {
        String html = getResourceAsString(testResourcesPath + "/invalidHtml.html");
        List<RemoteItem> urls = urlLister.parseHtml(html, baseUrl);
        assertEquals(0, urls.size(), "there should be no URLs in this html");
    }

    @Test
    void listAllNoHrefs() throws IOException {
        String html = getResourceAsString(testResourcesPath + "/noHrefs.html");
        List<RemoteItem> urls = urlLister.parseHtml(html, baseUrl);
        assertEquals(0, urls.size(), "there should be no URLs in this html");
    }

    @Test
    void listAll() throws IOException {
        String html = getResourceAsString(testResourcesPath + "/simple.html");
        String validHtml = createValidHtml(html);
        List<RemoteItem> urls = urlLister.parseHtml(validHtml, baseUrl);
        assertEquals(1, urls.size());
    }

    @Test
    void listAllFromArtifactorySimpleBrowsingHtml() throws IOException {
        String html = getResourceAsString(testResourcesPath + "/artifactory-simple.html");
        String absoluteBase = "http://localhost:8081/artifactory/libs-releases-local/org/jfrog/test/";
        List<RemoteItem> children = urlLister.parseHtml(html, absoluteBase);
        assertEquals(4, children.size(), "Found: " + children);
        assertEquals(new RemoteItem(absoluteBase + "multi1/", true), children.get(0));
        assertEquals(new RemoteItem(absoluteBase + "multi.pom", false), children.get(3));
    }

    @Test
    void listAllFromArtifactoryListBrowsingHtml() throws IOException {
        String html = getResourceAsString(testResourcesPath + "/artifactory-list.html");
        List<RemoteItem> children = urlLister.parseHtml(html, baseUrl);
        assertEquals(3, children.size(), "Found: " + children);
        assertEquals(baseUrl + "/multi1/", children.get(0).getUrl());
        assertEquals(baseUrl + "/multi2/", children.get(1).getUrl());
        assertEquals(baseUrl + "/multi3/", children.get(2).getUrl());
    }

    @Test
    void listAllFromArtifactoryListBrowsingHtmlWithSingleQuotes() throws IOException {
        String html = getResourceAsString(testResourcesPath + "/hrefExample.html");
        List<RemoteItem> children = urlLister.parseHtml(html, baseUrl);
        assertEquals(4, children.size(), "Found: " + children);
        assertEquals(baseUrl + "/file.jar", children.get(0).getUrl());
        assertEquals("file.jar", children.get(0).getName());
        assertEquals(baseUrl + "/file1", children.get(1).getUrl());
        assertEquals("file1", children.get(1).getName());
        assertEquals(baseUrl + "/file2", children.get(2).getUrl());
        assertEquals("file2", children.get(2).getName());
        assertEquals(baseUrl + "/index.html", children.get(3).getUrl());
        assertEquals("index.html", children.get(3).getName());
    }

    @Test
    void listAllWhenHtmlTextEndsWithDots() throws IOException {
        String html = getResourceAsString(testResourcesPath + "/htmlTextEndsWithDots.html");
        List<RemoteItem> children = urlLister.parseHtml(html, baseUrl);
        assertEquals(4, children.size(), "Found: " + children);
        assertEquals(baseUrl + "/long-test-resource-name-1.0.0-RELEASE-source.jar", children.get(0).getUrl());
        assertEquals("long-test-resource-name-1.0.0-RELEASE-source.jar", children.get(0).getName());
        assertEquals(baseUrl + "/long-test-resource-name-1.0.0-RELEASE-source.jar.asc", children.get(1).getUrl());
        assertEquals("long-test-resource-name-1.0.0-RELEASE-source.jar.asc", children.get(1).getName());
        assertEquals(baseUrl + "/long-test-resource-name-1.0.0-RELEASE-source.jar.md5", children.get(2).getUrl());
        assertEquals("long-test-resource-name-1.0.0-RELEASE-source.jar.md5", children.get(2).getName());
        assertEquals(baseUrl + "/long-test-resource-name-1.0.0-RELEASE-source.jar.sha1", children.get(3).getUrl());
        assertEquals("long-test-resource-name-1.0.0-RELEASE-source.jar.sha1", children.get(3).getName());
    }

    @Test
    void listAllWhenHtmlTextWithSpecialChars() throws IOException {
        String html = getResourceAsString(testResourcesPath + "/htmlTextWithSpecialChars.html");
        String baseUrlWithPlus = baseUrl + "+";
        List<RemoteItem> children = urlLister.parseHtml(html, baseUrl + "+");
        assertEquals(7, children.size(), "Found: " + children);
        assertEquals(baseUrlWithPlus + "/conan/", children.get(0).getUrl());
        assertEquals("conan", children.get(0).getName());
        assertEquals(baseUrlWithPlus + "/user/", children.get(1).getUrl());
        assertEquals("user", children.get(1).getName());
        assertEquals(baseUrlWithPlus + "/abc!@#$%^&().json", children.get(2).getUrl());
        assertEquals("abc!@#$%^&().json", children.get(2).getName());
        assertEquals(baseUrlWithPlus + "/ésdasdé.txt", children.get(3).getUrl());
        assertEquals("ésdasdé.txt", children.get(3).getName());
        assertEquals(baseUrlWithPlus + "/why space.json", children.get(4).getUrl());
        assertEquals("why space.json", children.get(4).getName());
        assertEquals(baseUrlWithPlus + "/bla+blo.json", children.get(5).getUrl());
        assertEquals("bla+blo.json", children.get(5).getName());
        assertEquals(baseUrlWithPlus + "/withplus+and..gt.jso..&gt;", children.get(6).getUrl());
        assertEquals("withplus+and..gt.jso..&gt;", children.get(6).getName());
    }

    @Test
    void listAbsoluteHrefsSkipsMalformedAndOutOfScope() throws IOException {
        String html = getResourceAsString(testResourcesPath + "/absoluteHrefs.html");
        String absoluteBase = "http://example.com/repo/";
        List<RemoteItem> children = urlLister.parseHtml(html, absoluteBase);
        assertEquals(4, children.size(), "Found: " + children);
        assertEquals(absoluteBase + "child1.jar", children.get(0).getUrl());
        assertEquals(absoluteBase + "subdir/", children.get(1).getUrl());
        assertEquals(absoluteBase + "artifact.jar", children.get(2).getUrl());
        assertEquals(absoluteBase + "readme.txt", children.get(3).getUrl());
    }

    private String createValidHtml(String source) throws IOException {
        File tempFile = File.createTempFile("artifactory", "html");
        return source.replace("{placeHolder}", tempFile.toURI().toURL().toExternalForm());
    }
}
