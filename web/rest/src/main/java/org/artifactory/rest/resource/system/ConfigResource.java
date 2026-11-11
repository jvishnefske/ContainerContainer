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

package org.artifactory.rest.resource.system;


import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.BaseUrlModel;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.MailServerModel;
import org.artifactory.api.config.ProxiesListModel;
import org.artifactory.api.config.ProxyModel;
import org.artifactory.api.config.SecurityPlatformModel;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.ConfigRestConstants;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.exception.DocumentBuildException;
import org.artifactory.platform.PlatformConfigService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.artifactory.rest.resource.BaseResource;
import org.artifactory.rest.util.DataDiffsToRepoConfigChangesYamlParser;
import org.artifactory.rest.util.PATCH;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.utils.AuthenticationUtils;
import org.artifactory.util.AolUtils;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.UrlValidator;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.JsonSensitiveDataException;
import org.jfrog.common.YamlUtils;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.common.config.diff.MapToDiffConverter;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.api.common.RepoRemovalStatusHolder.RemoveRepositoryReport;
import static org.artifactory.api.rest.constant.ConfigRestConstants.SERVICE_KEY;
import static org.artifactory.rest.util.DataDiffsToRepoConfigChangesYamlParser.RepoConfigChanges;
import static org.artifactory.rest.util.DataDiffsToRepoConfigChangesYamlParser.extractRepoConfigs;
import static org.artifactory.security.permissionscope.PermissionScopeToken.SYSTEM_PLATFORM_CONFIG_READ;

/**
 * @author freds
 */
@Slf4j
@RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class ConfigResource {

    private final CentralConfigService centralConfigService;
    private final HttpServletRequest request;
    private final UrlValidator urlValidator;
    private final AolService aolService;
    private final AddonsManager addonsManager;
    private final RepositoryService repositoryService;
    private final AccessService accessService;
    private final PlatformConfigService platformConfigService;

    public ConfigResource(CentralConfigService centralConfigService, HttpServletRequest httpServletRequest,
            AolService aolService, AddonsManager addonsManager, AccessService accessService,
            RepositoryService repositoryService, PlatformConfigService platformConfigService) {
        this.centralConfigService = centralConfigService;
        this.request = httpServletRequest;
        this.aolService = aolService;
        this.addonsManager = addonsManager;
        this.repositoryService = repositoryService;
        this.urlValidator = new UrlValidator("http", "https");
        this.accessService = accessService;
        this.platformConfigService = platformConfigService;
    }

    @GET
    @Produces(MediaType.APPLICATION_XML)
    public CentralConfigDescriptor getConfig() {
        AolUtils.assertDashboardUser();
        CentralConfigDescriptor descriptor = centralConfigService.getDescriptor();
        if (AuthenticationUtils.isAccessTokenAuthentication()) {
            return descriptor;
        } else if (descriptor.getMailServer() != null || !descriptor.getProxies().isEmpty()) {
            descriptor = centralConfigService.getMutableDescriptor();
            descriptor.getProxies().forEach(proxyDescriptor -> proxyDescriptor.setPassword(null));
            if (descriptor.getMailServer() != null) {
                descriptor.getMailServer().setPassword(null);
            }
        }
        return descriptor;
    }

    @GET
    @AllowedPermissionScope(SYSTEM_PLATFORM_CONFIG_READ)
    @RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    @Path(ConfigRestConstants.PLATFORM_FEDERATED_URL_PATH)
    public BaseUrlModel getFederatedUrl() {
        String url = centralConfigService.getDescriptor().getFederatedRepoUrlBaseOrDefault();
        if (StringUtils.isBlank(url)) {
            return new BaseUrlModel(null);
        }
        url = PathUtils.trimTrailingSlashes(url);
        return url.endsWith("/artifactory") ? new BaseUrlModel(url) : new BaseUrlModel(url + "/artifactory");
    }

    @GET
    @AllowedPermissionScope(SYSTEM_PLATFORM_CONFIG_READ)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(ConfigRestConstants.PLATFORM_URL_BASE_PATH)
    public BaseUrlModel getPlatformBaseUrl() {
        return centralConfigService.getPlatformBaseUrl();
    }

    @GET
    @AllowedPermissionScope(SYSTEM_PLATFORM_CONFIG_READ)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(ConfigRestConstants.PLATFORM_SAAS_PATH)
    public SaasResponse isSaas() {
        SaasResponse response = new SaasResponse();
        response.setSaas(aolService.isAol());
        return response;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(ConfigRestConstants.MAIL_PATH)
    public MailServerModel getMailServer() {
        MailServerModel mailServerModel = new MailServerModel(centralConfigService.getDescriptor().getMailServer());
        if (AolUtils.isAol() && !AuthenticationUtils.isXrayTokenAuthentication(
                SecurityContextHolder.getContext().getAuthentication())) {
            mailServerModel.setHost(null);
        }
        if (!AuthenticationUtils.isAccessTokenAuthentication()) {
            mailServerModel.setPassword(null);
        }
        return mailServerModel;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path(ConfigRestConstants.PROXIES_PATH)
    public ProxiesListModel getPlatformProxies() {
        CentralConfigDescriptor descriptor = centralConfigService.getDescriptor();
        ProxiesListModel proxiesModel = new ProxiesListModel(descriptor.getProxies());
        if (!AuthenticationUtils.isAccessTokenAuthentication()) {
            proxiesModel.getProxies().forEach(proxyDescriptor -> proxyDescriptor.setPassword(null));
        }
        return proxiesModel;
    }

    @GET
    @AllowedPermissionScope(SYSTEM_PLATFORM_CONFIG_READ)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(ConfigRestConstants.PROXY_PATH)
    public ProxyModel getServiceProxy(@PathParam(SERVICE_KEY) String serviceKey) {
        ProxyDescriptor proxyDescriptor = centralConfigService.getServiceProxyByKey(serviceKey);
        if (proxyDescriptor == null) {
            throw new NotFoundException("Proxy configuration for specified service '" + serviceKey + "' not found");
        }
        ProxyModel proxyModel = new ProxyModel(proxyDescriptor);
        if (!AuthenticationUtils.isAccessTokenAuthentication()) {
            proxyModel.setPassword(null);
        }
        return proxyModel;
    }

    @GET
    @AllowedPermissionScope(SYSTEM_PLATFORM_CONFIG_READ)
    @Produces(MediaType.APPLICATION_JSON)
    @Path(ConfigRestConstants.PLATFORM_SECURITY)
    public SecurityPlatformModel getPlatformSecurity() {
        return new SecurityPlatformModel(
                centralConfigService.getDescriptor().getSecurity().isHideUnauthorizedResources(),
                accessService.isAnonymousAccessEnabled());
    }

    @POST
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.TEXT_PLAIN)
    public String setConfig(String xmlContent) {
        isOperationAllowedForSassOrHa();
        log.debug("Received new configuration data.");
        try {
            centralConfigService.setConfigXml(xmlContent, true);
            return "Reload of new configuration succeeded";
        } catch (DocumentBuildException e) {
            // re-thrown exception not logged. Logging it by purpose.
            log.warn(e.getMessage(), e);
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path(ConfigRestConstants.LOGO_URL_PATH)
    public String logoUrl() {
        String logo = ContextHelper.get().beanForType(PlatformConfigService.class).getPlatformGeneralConfig().getLogo();
        if (StringUtils.isNotBlank(logo)) {
            return logo;
        }

        File logoFile = new File(ContextHelper.get().getArtifactoryHome().getLogoDir(), "logo");
        if (logoFile.exists()) {
            return HttpUtils.getServletContextUrl(request) + "/webapp/logo?" + logoFile.lastModified();
        }

        return null;
    }

    @PUT
    @Consumes({MediaType.TEXT_PLAIN})
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @Path(ConfigRestConstants.URL_BASE_PATH)
    public Response setUrlBase(String urlBase) {
        if (!ConstantValues.allowAolBaseUrlChange.getBoolean()) {
            AolUtils.assertApiAllowedInternally(
                    "Changing Custom URL Base is not supported for SaaS instances");
        }
        log.debug("Updating URL base: {}", urlBase);
        final String messageFailed = "Updating URL base has failed.\n";
        final String messageOk = "URL base has been successfully updated to \"%s\".\n";

        validateUrl(urlBase);
        persistUrl(urlBase);

        if (!centralConfigService.getMutableDescriptor().getUrlBase().equals(urlBase)) {
            return Response.serverError().entity(messageFailed).build();
        }
        return Response.ok().entity(String.format(messageOk, urlBase)).build();
    }

    @PATCH
    @Consumes({BaseResource.MEDIA_TYPE_APPLICATION_YAML})
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    public Response patchConfig(String patchYaml) {
        try {
            log.debug("Patch on current config with: {}", patchYaml);
            Map<String, Object> hashMap = YamlUtils.getInstance().readValue(patchYaml, HashMap.class);
            int platformConfigResources = platformConfigService.movePlatformResources(hashMap);
            int accessConfigResources = accessService.moveYamlConfigResourcesToAccess(hashMap);
            int dataDiffsSize = 0;
            int numRepoDeleteFailures = 0;
            if (!hashMap.isEmpty()) {
                Set<DataDiff<?>> newDataDiffs = MapToDiffConverter.mapToListOfChanges(hashMap);
                dataDiffsSize = newDataDiffs.size();
                Set<DataDiff<?>> repoConfigDataDiffs = extractRepoConfigs(newDataDiffs);
                if (!newDataDiffs.isEmpty()) {
                    centralConfigService.mergeAndSaveNewData(newDataDiffs, true);
                }
                RepoConfigChanges repoConfigChanges = DataDiffsToRepoConfigChangesYamlParser.parse(repoConfigDataDiffs);
                repositoryService.bulkUpsertRepoConfigs(repoConfigChanges.repoConfigsToUpsert(), true);
                numRepoDeleteFailures = bulkDeleteRepoConfigs(repoConfigChanges.repoConfigsToDelete());
            }
            return Response.ok()
                    .entity(dataDiffsSize - numRepoDeleteFailures + platformConfigResources + accessConfigResources +
                            " changes to config merged successfully")
                    .build();
        } catch (IllegalArgumentException | IllegalStateException | JsonSensitiveDataException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /**
     * Bulk delete repositories
     * @return number of repositories failed to be deleted
     */
    private int bulkDeleteRepoConfigs(List<String> repoKeysToDelete) {
        if (CollectionUtils.isNullOrEmpty(repoKeysToDelete)) {
            return 0;
        }
        if (isRepoDeleteFlowV2Enabled()) {
            Set<RemoveRepositoryReport> reports =
                    repositoryService.removeRepositories(new LinkedHashSet<>(repoKeysToDelete)).getReports();
            return extractNumberOfFailures(reports);
        } else {
            repositoryService.bulkDeleteRepoConfigs(repoKeysToDelete, true, true);
            return 0;
        }
    }

    private int extractNumberOfFailures(Set<RemoveRepositoryReport> statusHolders) {
        return (int) statusHolders.stream().filter(report -> !report.isSuccess()).count();
    }

    private boolean isRepoDeleteFlowV2Enabled() {
        return ConstantValues.removeRepositoryFlowV2Enabled.getBoolean();
    }

    /**
     * Saves new urlBase in configuration.
     *
     * @param urlBase url to save.
     */
    private void persistUrl(String urlBase) {
        MutableCentralConfigDescriptor descriptor = centralConfigService.getMutableDescriptor();
        descriptor.setUrlBase(urlBase);
        centralConfigService.saveEditedDescriptorAndReload(descriptor);
    }

    /**
     * Validates URL using UrlValidator.
     *
     * @param urlBase url to validate.
     * @throws BadRequestException if URL is in illegal form.
     * @see org.artifactory.util.UrlValidator
     */
    private void validateUrl(String urlBase) {
        if (!StringUtils.isBlank(urlBase)) { // we allow removing custom urlBase
            try {
                urlValidator.validate(urlBase);
            } catch (UrlValidator.UrlValidationException ex) {
                throw new BadRequestException(ex.getMessage());
            }
        }
    }

    private void isOperationAllowedForSassOrHa() {
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        if (aolService.isAol() && !(aolService.isDashboardUser() || haAddon.isHaAuthentication() ||
                ConstantValues.allowAolConfigOverrideChanges.getBoolean())) {
            throw new BadRequestException(AolUtils.NOT_SUPPORTED_FOR_CLOUD);
        }
    }
}