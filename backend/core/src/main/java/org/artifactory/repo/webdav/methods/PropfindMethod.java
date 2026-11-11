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

package org.artifactory.repo.webdav.methods;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.BrowsableItem;
import org.artifactory.api.repo.BrowsableItemCriteria;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.exception.FolderExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.mime.MimeType;
import org.artifactory.mime.NamingUtils;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.repo.webdav.WebdavStatus;
import org.artifactory.repo.webdav.XmlWriter;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.security.InternalSecurityService;
import org.artifactory.util.BasicHttpUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import jakarta.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.artifactory.repo.webdav.WebdavServiceImpl.APACHE_NAMESPACE;
import static org.artifactory.repo.webdav.WebdavServiceImpl.DEFAULT_NAMESPACE;
import static org.artifactory.repo.webdav.WebdavServiceImpl.DEFAULT_NS_ABBRV;
import static org.artifactory.repo.webdav.WebdavServiceImpl.PROP_NS_ABBRV1;
import static org.artifactory.repo.webdav.WebdavServiceImpl.PROP_NS_ABBRV2;

@Slf4j
@Component
public class PropfindMethod extends AbstractWebdavMethod {

    @Autowired
    private InternalRepositoryService repoService;

    @Autowired
    private RepositoryBrowsingService repoBrowsing;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private InternalSecurityService securityService;

    @Autowired
    private BasicAuthenticationEntryPoint authenticationEntryPoint;

    /**
     * Simple date format for the creation date ISO representation (partial).
     */
    protected static final DateTimeFormatter CREATION_DATE_FORMAT =
            DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(DateTimeZone.forID("GMT"))
                    .withLocale(Locale.ENGLISH);

    /**
     * RFC 1123 date format to use in "getlastmodified" attribute
     * http://tools.ietf.org/html/rfc2068#section-3.3.1
     */
    protected static final DateTimeFormatter LAST_MODIFIED_DATE_FORMAT =
            DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'").withZone(DateTimeZone.forID("GMT"))
                    .withLocale(Locale.ENGLISH);

    /**
     * PROPFIND options
     */
    private enum PropfindType {
        FIND_ALL_PROP("allprop"),
        FIND_BY_PROPERTY("prop"),
        FIND_PROPERTY_NAMES("propname");
        private final String detectString;

        PropfindType(String detectString) {
            this.detectString = detectString;
        }

        @Nullable
        public static PropfindType fromString(String s) {
            for (PropfindType type : PropfindType.values()) {
                if (s.endsWith(type.detectString)) {
                    return type;
                }
            }
            return null;
        }
    }

    @Override
    public boolean canHandle(String method) {
        return getName().equalsIgnoreCase(method);
    }

    @Override
    public void handle(ArtifactoryRequest request, ArtifactoryResponse response) throws IOException {
        // Retrieve the resources
        log.debug("Handling {}", getName());
        RepoPath repoPath = request.getRepoPath();
        BrowsableItem rootItem =
                repoService.exists(repoPath) ? repoBrowsing.getLocalRepoBrowsableItem(repoPath) : null;
        if (!hasReadAccess(repoPath, rootItem)) {
            sendReadDenied(repoPath, response);
            return;
        }
        int depth = findDepth(request);
        Node[] propNodeHolder = new Node[1];
        PropfindType propertyFindType = resolvePropfindType(request, propNodeHolder);
        List<String> properties = null;
        if (propertyFindType == PropfindType.FIND_BY_PROPERTY) {
            properties = getPropertiesFromXml(propNodeHolder[0]);
        }

        response.setStatus(HttpStatus.SC_MULTI_STATUS);
        response.setContentType("text/xml; charset=UTF-8");

        // Create multistatus object
        Writer writer = response.getWriter();
        if (log.isDebugEnabled()) {
            writer = new StringWriter(); // write to memory so we'll be able to log the result as string
        }
        XmlWriter generatedXml = new XmlWriter(writer);
        generatedXml.writeXMLHeader();
        generatedXml.writeElement(DEFAULT_NS_ABBRV, "multistatus", XmlWriter.OPENING,
                DEFAULT_NS_ABBRV, DEFAULT_NAMESPACE, "ns0",
                DEFAULT_NAMESPACE);

        if (rootItem != null) {
            recursiveParseProperties(request, generatedXml, rootItem, propertyFindType, properties,
                    depth);
        } else {
            log.warn("Item '" + request.getRepoPath() + "' not found.");
        }
        generatedXml.writeElement(DEFAULT_NS_ABBRV, "multistatus", XmlWriter.CLOSING);
        generatedXml.sendData();
        if (log.isDebugEnabled()) {
            log.debug("Webdav response:\n" + writer.toString());
            //response.setContentLength(writer.toString().getBytes().length);
            response.getWriter().append(writer.toString());
        }
        response.flush();
    }

    /**
     * Parses the PROPFIND body (when present) and returns the requested property-find type.
     * When the type is {@link PropfindType#FIND_BY_PROPERTY}, {@code propNodeOut[0]} holds the prop element.
     */
    private PropfindType resolvePropfindType(ArtifactoryRequest request, Node[] propNodeOut)
            throws IOException {
        PropfindType propertyFindType = PropfindType.FIND_ALL_PROP;
        if (request.getContentLength() > 0) {
            DocumentBuilder documentBuilder = getDocumentBuilder();
            try {
                Document document = documentBuilder.parse(
                        new InputSource(request.getInputStream()));
                logWebdavRequest(document);
                Element rootElement = document.getDocumentElement();
                NodeList childList = rootElement.getChildNodes();

                for (int i = 0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    if (currentNode.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    propertyFindType = PropfindType.fromString(currentNode.getNodeName());
                    if (propertyFindType == PropfindType.FIND_BY_PROPERTY) {
                        propNodeOut[0] = currentNode;
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("Webdav propfind failed.", e);
            }
        }
        return propertyFindType;
    }

    @Override
    public String getName() {
        return "propfind";
    }

    /**
     * A caller lacking read permission on the requested path still gets access when something under it is visible to
     * them. This keeps partially-permitted listings (read granted on some children only) returning their permitted
     * subset, which is how the native browsing path behaves for the same paths.
     */
    @VisibleForTesting
    boolean hasReadAccess(RepoPath repoPath, BrowsableItem rootItem) {
        return hasReadPermission(repoPath) || hasReadableChildren(repoPath, rootItem);
    }

    /**
     * No permission target names a virtual repository key, so {@code canRead} on a virtual path is always false.
     * Resolve it through the aggregated repositories the way the native browsing path does, otherwise a caller who
     * can read a member repository would be denied a listing they are allowed to see.
     */
    private boolean hasReadPermission(RepoPath repoPath) {
        if (authorizationService.canRead(repoPath)) {
            return true;
        }
        VirtualRepo virtualRepo = repoService.virtualRepositoryByKey(repoPath.getRepoKey());
        if (virtualRepo == null) {
            return false;
        }
        return Stream.of(virtualRepo.getResolvedLocalAndFederatedRepos(), virtualRepo.getResolvedRemoteRepos())
                .flatMap(Collection::stream)
                .anyMatch(repo -> authorizationService
                        .canRead(RepoPathFactory.create(repo.getKey(), repoPath.getPath())));
    }

    private boolean hasReadableChildren(RepoPath repoPath, BrowsableItem rootItem) {
        if (rootItem == null || !rootItem.isFolder()) {
            return false;
        }
        BrowsableItemCriteria criteria = new BrowsableItemCriteria.Builder(repoPath).build();
        try (Stream<BaseBrowsableItem> children =
                repoBrowsing.getLocalRepoBrowsableChildrenStream(criteria, false, null)) {
            return children.findAny().isPresent();
        } catch (ItemNotFoundRuntimeException | FolderExpectedException | IllegalArgumentException e) {
            log.debug("Cannot list children of '{}' while resolving read access", repoPath, e);
            return false;
        } catch (RuntimeException e) {
            // Deny rather than list on an unexpected failure, but make it visible: a storage fault here would
            // otherwise surface to callers as a permission problem with nothing above debug in the log.
            log.warn("Failed listing children of '{}' while resolving read access, denying the request", repoPath, e);
            return false;
        }
    }

    /**
     * Responds like the download path does for an unreadable path, so WebDAV clients get the same challenge/error
     * they already get from GET instead of a multi-status listing.
     */
    private void sendReadDenied(RepoPath repoPath, ArtifactoryResponse response) throws IOException {
        if (shouldHideUnauthorizedResources(repoPath)) {
            response.sendError(HttpStatus.SC_NOT_FOUND, "Resource not found; Path: '" + repoPath + "'", log);
            return;
        }
        if (securityService.isAnonymous()) {
            // Transform the forbidden into a challenge so the client knows it has to authenticate and retry
            response.sendAuthorizationRequired("Propfind request for repo:path '" + repoPath + "' requires " +
                    "authentication.", authenticationEntryPoint.getRealmName());
            return;
        }
        response.sendError(HttpStatus.SC_FORBIDDEN, "Propfind request for repo:path '" + repoPath +
                "' is forbidden for user '" + securityService.currentUsername() + "'.", log);
    }

    /**
     * Honours the global setting and, for virtual repositories, the per-repository one, so an unreadable path is
     * hidden behind a 404 on exactly the same repositories where the download path already hides it.
     */
    private boolean shouldHideUnauthorizedResources(RepoPath repoPath) {
        if (securityService.shouldHideUnauthorizedResources()) {
            return true;
        }
        VirtualRepoDescriptor virtualRepo = repoService.virtualRepoDescriptorByKey(repoPath.getRepoKey());
        return virtualRepo != null && virtualRepo.isHideUnauthorizedResources();
    }

    @SuppressWarnings({"OverlyComplexMethod"})
    private void parseProperties(ArtifactoryRequest request, XmlWriter xmlResponse,
            BaseBrowsableItem item, PropfindType type, List<String> propertiesList) throws IOException {
        // Last modified should be returned as RFC 1123 format.
        // See https://tools.ietf.org/html/rfc4918#section-8
        RepoPath repoPath = item.getRepoPath();
        String creationDate = CREATION_DATE_FORMAT.print(item.getCreated());
        boolean isFolder = item.isFolder();
        String rfcDate = LAST_MODIFIED_DATE_FORMAT.print(item.getLastModified());
        String resourceLength = item.getSize() + "";

        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "response", XmlWriter.OPENING, PROP_NS_ABBRV2, APACHE_NAMESPACE,
                PROP_NS_ABBRV1, DEFAULT_NAMESPACE);

        //Generating href element
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "href", XmlWriter.OPENING);
        String origPath = request.getPath();
        String uri = request.getUri(); //TODO [by shayb]: Consider delete the dot segments
        String hrefBase = uri;
        origPath = BasicHttpUtils.encodeQuery(origPath);
        if (origPath.length() > 0) {
            int idx = uri.lastIndexOf(origPath);
            if (idx > 0) {
                //When called recursively avoid concatenating the original path on top of itself
                hrefBase = uri.substring(0, idx);
            }
        }
        String path = repoPath.getPath();
        if (StringUtils.isNotBlank(path) && !hrefBase.endsWith("/")) {
            hrefBase += "/";
        }

        // Encode only the path since the base is already encoded
        String href = hrefBase + BasicHttpUtils.encodeQuery(path);

        xmlResponse.writeText(href);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "href", XmlWriter.CLOSING);

        String resourceName = path;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1) {
            resourceName = resourceName.substring(lastSlash + 1);
        }

        String status = "HTTP/1.1 " + WebdavStatus.SC_OK + " " +
                WebdavStatus.getStatusText(WebdavStatus.SC_OK);
        switch (type) {
            case FIND_ALL_PROP:
                handleFindAllPropsResponse(xmlResponse, creationDate, isFolder, rfcDate, resourceLength, status,
                        path, resourceName);
                break;
            case FIND_PROPERTY_NAMES:
                handleFindPropNamesResponse(xmlResponse, isFolder, status);
                break;
            case FIND_BY_PROPERTY:
                handleFindByPropResponse(xmlResponse, propertiesList, creationDate, isFolder, rfcDate,
                        resourceLength, status, path, resourceName);
                break;

        }
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "response", XmlWriter.CLOSING);
    }

    private void handleFindByPropResponse(XmlWriter xmlResponse, List<String> propertiesList, String creationDate,
            boolean isFolder, String lastModified, String resourceLength, String status, String path,
            String resourceName) throws IOException {
        //noinspection MismatchedQueryAndUpdateOfCollection
        List<String> propertiesNotFound = new ArrayList<>();
        // Parse the list of properties
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "propstat", XmlWriter.OPENING);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "prop", XmlWriter.OPENING);
        for (String property : propertiesList) {
            switch (property) {
                case "creationdate":
                    xmlResponse.writeProperty(PROP_NS_ABBRV1, "creationdate", creationDate);
                    break;
                case "displayname":
                    xmlResponse.writeElement(DEFAULT_NS_ABBRV, "displayname", XmlWriter.OPENING);
                    xmlResponse.writeData(resourceName);
                    xmlResponse.writeElement(DEFAULT_NS_ABBRV, "displayname", XmlWriter.CLOSING);
                    break;
                case "getcontentlanguage":
                    if (isFolder) {
                        propertiesNotFound.add(property);
                    } else {
                        xmlResponse.writeElement(
                                null, "getcontentlanguage", XmlWriter.NO_CONTENT);
                    }
                    break;
                case "getcontentlength":
                    if (isFolder) {
                        propertiesNotFound.add(property);
                    } else {
                        xmlResponse.writeProperty(PROP_NS_ABBRV1, "getcontentlength", resourceLength);
                    }
                    break;
                case "getcontenttype":
                    if (isFolder) {
                        propertiesNotFound.add(property);
                    } else {
                        xmlResponse.writeProperty(DEFAULT_NS_ABBRV, "getcontenttype",
                                NamingUtils.getMimeTypeByPathAsString(path));
                    }
                    break;
                case "getetag":
                    xmlResponse.writeProperty(PROP_NS_ABBRV1, "getetag",
                            getEtag(resourceLength, lastModified));
                    break;
                case "getlastmodified":
                    xmlResponse.writeProperty(PROP_NS_ABBRV1, "getlastmodified", lastModified);
                    break;
                case "source":
                    xmlResponse.writeProperty(DEFAULT_NS_ABBRV, "source", "");
                    break;
                default:
                    propertiesNotFound.add(property);
                    break;
            }
        }
        //Always include resource type
        if (isFolder) {
            xmlResponse.writeElement(PROP_NS_ABBRV1, "resourcetype", XmlWriter.OPENING);
            xmlResponse.writeElement(DEFAULT_NS_ABBRV, "collection", XmlWriter.NO_CONTENT);
            xmlResponse.writeElement(PROP_NS_ABBRV1, "resourcetype", XmlWriter.CLOSING);
        } else {
            xmlResponse.writeElement(PROP_NS_ABBRV1, "resourcetype", XmlWriter.NO_CONTENT);
        }

        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "prop", XmlWriter.CLOSING);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "status", XmlWriter.OPENING);
        xmlResponse.writeText(status);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "status", XmlWriter.CLOSING);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "propstat", XmlWriter.CLOSING);
    }

    private void handleFindPropNamesResponse(XmlWriter xmlResponse, boolean isFolder, String status) {
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "propstat", XmlWriter.OPENING);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "prop", XmlWriter.OPENING);
        xmlResponse.writeElement(PROP_NS_ABBRV1, "creationdate", XmlWriter.NO_CONTENT);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "displayname", XmlWriter.NO_CONTENT);
        if (!isFolder) {
            xmlResponse.writeElement(PROP_NS_ABBRV1, "getcontentlanguage", XmlWriter.NO_CONTENT);
            xmlResponse.writeElement(PROP_NS_ABBRV1, "getcontentlength", XmlWriter.NO_CONTENT);
            xmlResponse.writeElement(PROP_NS_ABBRV1, "getcontenttype", XmlWriter.NO_CONTENT);
            xmlResponse.writeElement(PROP_NS_ABBRV1, "getetag", XmlWriter.NO_CONTENT);
            xmlResponse.writeElement(PROP_NS_ABBRV1, "getlastmodified", XmlWriter.NO_CONTENT);
        }
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "resourcetype", XmlWriter.NO_CONTENT);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "source", XmlWriter.NO_CONTENT);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "lockdiscovery", XmlWriter.NO_CONTENT);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "prop", XmlWriter.CLOSING);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "status", XmlWriter.OPENING);
        xmlResponse.writeText(status);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "status", XmlWriter.CLOSING);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "propstat", XmlWriter.CLOSING);
    }

    private void handleFindAllPropsResponse(XmlWriter xmlResponse, String creationDate, boolean isFolder,
            String lastModified,
            String resourceLength, String status, String path, String resourceName) throws IOException {
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "propstat", XmlWriter.OPENING);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "prop", XmlWriter.OPENING);
        if (!isFolder) {
            xmlResponse.writeProperty(PROP_NS_ABBRV1, "getcontentlength", resourceLength);

            MimeType ct = NamingUtils.getMimeType(path);
            xmlResponse.writeProperty(PROP_NS_ABBRV1, "getcontenttype", ct.getType());
            xmlResponse.writeElement(PROP_NS_ABBRV1, "resourcetype", XmlWriter.NO_CONTENT);
        } else {
            xmlResponse.writeElement(PROP_NS_ABBRV1, "resourcetype", XmlWriter.OPENING);
            xmlResponse.writeElement(DEFAULT_NS_ABBRV, "collection", XmlWriter.NO_CONTENT);
            xmlResponse.writeElement(PROP_NS_ABBRV1, "resourcetype", XmlWriter.CLOSING);
        }
        xmlResponse.writeProperty(PROP_NS_ABBRV1, "getlastmodified", lastModified);
        xmlResponse.writeProperty(PROP_NS_ABBRV1, "getetag", getEtag(resourceLength, lastModified));
        xmlResponse.writeProperty(PROP_NS_ABBRV1, "creationdate", creationDate);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "displayname", XmlWriter.OPENING);
        xmlResponse.writeData(resourceName);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "displayname", XmlWriter.CLOSING);
        xmlResponse.writeProperty(DEFAULT_NS_ABBRV, "source", "");
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "prop", XmlWriter.CLOSING);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "status", XmlWriter.OPENING);
        xmlResponse.writeText(status);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "status", XmlWriter.CLOSING);
        xmlResponse.writeElement(DEFAULT_NS_ABBRV, "propstat", XmlWriter.CLOSING);
    }

    /**
     * goes recursive through all folders. used by propfind
     */
    private void recursiveParseProperties(ArtifactoryRequest request,
            XmlWriter generatedXml, BaseBrowsableItem currentItem, PropfindType propertyFindType,
            List<String> properties,
            int depth)
            throws IOException {

        parseProperties(request, generatedXml, currentItem, propertyFindType, properties);

        if (depth <= 0) {
            return;
        }

        if (currentItem.isFolder()) {
            BrowsableItemCriteria criteria = new BrowsableItemCriteria.Builder(currentItem.getRepoPath()).build();
            List<BaseBrowsableItem> browsableChildren = repoBrowsing.getLocalRepoBrowsableChildren(criteria);
            for (BaseBrowsableItem child : browsableChildren) {
                recursiveParseProperties(request, generatedXml, child,
                        propertyFindType, properties, depth - 1);
            }
        }
    }

    private List<String> getPropertiesFromXml(Node propNode) {
        List<String> properties;
        properties = new ArrayList<>();
        NodeList childList = propNode.getChildNodes();
        for (int i = 0; i < childList.getLength(); i++) {
            Node currentNode = childList.item(i);
            switch (currentNode.getNodeType()) {
                case Node.TEXT_NODE:
                    break;
                case Node.ELEMENT_NODE:
                    String nodeName = currentNode.getNodeName();
                    String propertyName;
                    if (nodeName.indexOf(':') != -1) {
                        propertyName = nodeName.substring(nodeName.indexOf(':') + 1);
                    } else {
                        propertyName = nodeName;
                    }
                    // href is a live property which is handled differently
                    properties.add(propertyName);
                    break;
            }
        }
        return properties;
    }

}