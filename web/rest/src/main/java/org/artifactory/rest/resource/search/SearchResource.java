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

package org.artifactory.rest.resource.search;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.rest.constant.SearchRestConstants;
import org.artifactory.api.search.SearchService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.resource.license.LicenseResource;
import org.artifactory.rest.resource.search.types.AnyDateInRangeResource;
import org.artifactory.rest.resource.search.types.ArtifactLatestVersionSearchResource;
import org.artifactory.rest.resource.search.types.ArtifactSearchResource;
import org.artifactory.rest.resource.search.types.ArtifactVersionsSearchResource;
import org.artifactory.rest.resource.search.types.BadChecksumSearchResource;
import org.artifactory.rest.resource.search.types.BuildArtifactsSearchResource;
import org.artifactory.rest.resource.search.types.ChecksumSearchResource;
import org.artifactory.rest.resource.search.types.CreatedInRangeResource;
import org.artifactory.rest.resource.search.types.DependencySearchResource;
import org.artifactory.rest.resource.search.types.GavcSearchResource;
import org.artifactory.rest.resource.search.types.PatternSearchResource;
import org.artifactory.rest.resource.search.types.PropertySearchResource;
import org.artifactory.rest.resource.search.types.UsageSinceResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Resource class that handles search actions
 *
 * @author Yoav Landman
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(SearchRestConstants.PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
@ActivityDetails(group = RestApiGroup.SEARCH_API)
public class SearchResource {

    @Context
    private HttpServletRequest request;
    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private SearchService searchService;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private PropertiesService propertiesService;
    @Autowired
    private RepositoryBrowsingService repoBrowsingService;
    @Autowired
    private AddonsManager addonsManager;

    /**
     * Delegates the request to the artifact search resource
     *
     * @return Artifact search resource
     */
    @Path(SearchRestConstants.PATH_ARTIFACT)
    public ArtifactSearchResource artifactQuery() {
        return new ArtifactSearchResource(securityService, searchService, repositoryService, propertiesService,
                request, repoBrowsingService);
    }

    /**
     * Delegates the request to the GAVC search resource
     *
     * @return GAVC search resource
     */
    @Path(SearchRestConstants.PATH_GAVC)
    public GavcSearchResource gavcQuery() {
        return new GavcSearchResource(securityService, searchService, repositoryService, propertiesService,
                repoBrowsingService, request);
    }

    /**
     * Delegates the request to the property search resource
     *
     * @return property search resource
     */
    @Path(SearchRestConstants.PATH_PROPERTY)
    public PropertySearchResource propertyQuery() {
        return new PropertySearchResource(authorizationService, securityService, searchService, repositoryService,
                propertiesService, repoBrowsingService, request);
    }

    /**
     * Delegates the request to the usage since search resource
     *
     * @return Usage since resource
     */
    @Path(SearchRestConstants.PATH_USAGE_SINCE)
    public UsageSinceResource notDownloadedSinceQuery() {
        return new UsageSinceResource(addonsManager, securityService, request);
    }

    /**
     * Delegates the request to the in date range search resource
     *
     * @return create in range resource
     */
    @Path(SearchRestConstants.PATH_CREATED_IN_RANGE)
    public CreatedInRangeResource createdInDateRangeQuery() {
        return new CreatedInRangeResource(securityService, searchService, request);
    }

    @Path(SearchRestConstants.PATH_DATES_IN_RANGE)
    public AnyDateInRangeResource datesInRangeQuery() {
        return new AnyDateInRangeResource(securityService, searchService, request, repositoryService);
    }

    @Path(SearchRestConstants.PATH_PATTERN)
    public PatternSearchResource patternSearchQuery() {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return new PatternSearchResource(securityService, repositoryService, restAddon, request);
    }

    @Path(SearchRestConstants.PATH_LICENSE)
    public LicenseResource licensesSearch() {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return new LicenseResource(restAddon, request, repositoryService);
    }

    @Path(SearchRestConstants.PATH_CHECKSUM)
    public ChecksumSearchResource checksumSearch() {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return new ChecksumSearchResource(securityService, restAddon, repositoryService, propertiesService,
                repoBrowsingService, request);
    }

    @Path(SearchRestConstants.PATH_BAD_CHECKSUM)
    public BadChecksumSearchResource badChecksumSearch() {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return new BadChecksumSearchResource(securityService, restAddon, request);
    }

    @Path(SearchRestConstants.PATH_DEPENDENCY)
    public DependencySearchResource dependencySearch() {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return new DependencySearchResource(restAddon, request);
    }

    @Path(SearchRestConstants.PATH_VERSIONS)
    public ArtifactVersionsSearchResource versionsSearch() {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return new ArtifactVersionsSearchResource(restAddon);
    }

    @Path(SearchRestConstants.PATH_LATEST_VERSION)
    public ArtifactLatestVersionSearchResource latestVersionSearch() {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return new ArtifactLatestVersionSearchResource(restAddon);
    }

    @Path(SearchRestConstants.PATH_BUILD_ARTIFACTS)
    public BuildArtifactsSearchResource buildArtifactsSearch() {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return new BuildArtifactsSearchResource(restAddon, securityService, request);
    }
}
