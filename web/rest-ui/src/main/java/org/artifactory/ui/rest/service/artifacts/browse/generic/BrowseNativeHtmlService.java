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

package org.artifactory.ui.rest.service.artifacts.browse.generic;

import org.apache.commons.compress.utils.CharsetNames;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.RootNodesFilterResult;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.jfrog.storage.common.StorageUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.NotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.rightPad;

/**
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class BrowseNativeHtmlService extends BaseBrowseNativeService {

    private static final Logger log = LoggerFactory.getLogger(BrowseNativeHtmlService.class);

    @Override
    public void execute(ArtifactoryRestRequest<String> request, RestResponse response) {
        try {
            HttpServletRequest servletRequest = request.getServletRequest();
            RepoPath repoPath = (RepoPath) servletRequest.getAttribute(ATTR_ARTIFACTORY_REPOSITORY_PATH);
            if (repoPath == null || StringUtils.isEmpty(repoPath.getRepoKey())) {
                throw new NotFoundException("Repository Path Not Found");
            }
            Properties requestProps = (Properties) servletRequest.getAttribute(ATTR_ARTIFACTORY_REQUEST_PROPERTIES);
            RootNodesFilterResult rootNodesFilterResult = new RootNodesFilterResult();
            final List<? extends BaseBrowsableItem> items = getItems(repoPath, requestProps, rootNodesFilterResult);
            boolean isChallengeSet =
                    setResponseToHttpChallenge(repoPath, rootNodesFilterResult, items, response, false);
            if (isChallengeSet) {
                return;
            }

            // get Html native browsing page
            String page = buildPage(request, repoPath, items);
            // update response
            response.iModel(page);

        } catch (Exception e) {
            response.error(e.getMessage()).responseCode(HttpStatus.SC_NOT_FOUND);
            log.debug(e.getMessage() + " - " + HttpStatus.SC_NOT_FOUND);
        }
    }

    /**
     * build browsing native page with data
     *
     * @param artifactoryRequest - encapsulate data related to request
     * @return - native browsing html page
     */
    private String buildPage(ArtifactoryRestRequest artifactoryRequest, RepoPath repoPath,
            List<? extends BaseBrowsableItem> items) {
        StringBuilder page = new StringBuilder();
        updatePageData(repoPath, items);
        // fetch page title
        String title = getPageTitle(repoPath);
        // fetch page body
        String body = getBody();
        // fetch page footer
        String addressFooter = getAddressFooter();
        // create html page
        createHtmlPage(page, title, body, addressFooter);

        return page.toString();
    }

    /**
     * create html page and related tags
     *
     * @param page          - page string builder
     * @param title         - page title
     * @param body          - page body
     * @param addressFooter - page footer
     */
    private void createHtmlPage(StringBuilder page, String title, String body, String addressFooter) {
        page.append("<!DOCTYPE html>\n");
        page.append("<html>\n");
        page.append("<head>");
        page.append("<meta name=\"robots\" content=\"noindex\" />\n");
        page.append("<title>").append(title).append("</title>\n");
        page.append("</head>\n");
        page.append("<body>\n");
        page.append("<h1>").append(title).append("</h1>\n");
        page.append("<pre>").append(head).append("</pre>");
        page.append("<hr/>\n" + "<pre>");
        page.append(body);
        page.append("</pre>\n" + "<hr/>");
        page.append("<address style=\"font-size:small;\">").append(addressFooter).append("</address>");
        page.append("</body>");
        page.append("</html>");
    }


    /**
     * get path title
     *
     * @param repoPath - repository path
     * @return - native browsing page title
     */
    private String getPageTitle(RepoPath repoPath) {
        StringBuilder titleBuilder = new StringBuilder(String.format("Index of %s/%s", repoPath.getRepoKey(),
                StringEscapeUtils.escapeXml(repoPath.getPath())));
        return titleBuilder.toString();
    }

    /**
     * update page related data
     *
     * @param repoPath - repository path
     * @param items    - repository items
     */
    private void updatePageData(final RepoPath repoPath, List<? extends BaseBrowsableItem> items) {
        // get max name length
        int maxLength = 4;
        for (BaseBrowsableItem baseBrowsableItem : items) {
            maxLength = Math.max(baseBrowsableItem.getName().length(), maxLength);
        }
        // set table head
        setTableHead(maxLength);
        // items
        this.printParent = StringUtils.isNotEmpty(repoPath.getPath());
        this.items = items;
        this.columnSize = maxLength + 2;
    }

    /**
     * get page head data
     *
     * @param maxLength - max node name length
     * @return - page head
     */
    private void setTableHead(int maxLength) {
        final StringBuilder head = new StringBuilder();
        head.append(rightPad("Name", maxLength + 2));
        head.append("Last modified      Size");
        this.head = head.toString();
    }

    /**
     * get html page nodes body
     *
     * @return - html body
     */
    public String getBody() {
        StringBuilder bodyBuilder = new StringBuilder();
        if (printParent) {
            bodyBuilder.append("<a href=\"../\">../</a>\n");

        } else if (items.isEmpty()) {
            bodyBuilder.append("No items found.\n");
            return bodyBuilder.toString();
        }

        for (BaseBrowsableItem item : items) {
            String name = item.getName();
            bodyBuilder.append("<a href=\"");
            String encodedName = encodeUrl(name);
            bodyBuilder.append(encodedName);
            if (item.isFolder()) {
                bodyBuilder.append("/");
            }
            bodyBuilder.append("\">");
            bodyBuilder.append(StringEscapeUtils.escapeXml(name));
            if (item.isFolder()) {
                bodyBuilder.append("/");
            }
            bodyBuilder.append("</a>");
            if (item.isRemote()) {
                bodyBuilder.append("->");
            }
            bodyBuilder.append(StringUtils.repeat(" ", columnSize - name.length()));
            if (!item.isFolder()) {
                bodyBuilder.append(" ");
            }
            if (item.getLastModified() > 0) {
                bodyBuilder.append(DATE_FORMAT.print(item.getLastModified()));
            } else {
                bodyBuilder.append("  -");
            }
            bodyBuilder.append("  ");

            long size = item.getSize();
            if (item.isFolder() || size <= 0) {
                bodyBuilder.append("  -");
            } else {
                bodyBuilder.append(StorageUnit.toReadableString(size));
            }
            bodyBuilder.append("\n");
        }
        return bodyBuilder.toString();
    }

    /**
     * Special case for Ivy (+) sign - RTFACT-15708, need to have "+" chars as real "+" and not encoded.
     * The results of URLEncode.encode has all " " space chars transformed to "+" char. We replace them to be "%20".
     * The results of URLEncode.encode has all "+" space chars transformed to "%2B" char. We replace them to be "+".
     */
    private static String encodeUrl(String string) {
        try {
            return URLEncoder.encode(string, CharsetNames.UTF_8)
                    .replace("+", "%20")
                    .replace("%2B", "+");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Could not encode string " + string, e);
        }
    }
}
