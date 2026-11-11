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

package org.artifactory.ui.rest.service.setmeup;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.filteredresources.FilteredResourcesAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.maven.MavenService;
import org.artifactory.api.maven.MavenSettings;
import org.artifactory.api.maven.MavenSettingsMirror;
import org.artifactory.api.maven.MavenSettingsRepository;
import org.artifactory.api.maven.MavenSettingsServer;
import org.artifactory.api.security.SecurityService;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.md.Properties;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.service.StreamRestResponse;
import org.artifactory.ui.rest.model.setmeup.MavenSettingModel;
import org.artifactory.ui.rest.model.setmeup.ScriptDownload;
import org.artifactory.ui.utils.MultiPartUtils;
import org.artifactory.util.HttpUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;

/**
 * @author chen Keinan
 */
@Slf4j
@Component
public class GetMavenSettingSnippetService extends GetSettingSnippetService implements RestService {

    @Setter
    @Autowired
    SecurityService securityService;
    @Setter
    @Autowired
    MavenService mavenService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        boolean downloadScript = Boolean.parseBoolean(request.getQueryParamByKey("downloadScript"));
        boolean deployMavenSettings = Boolean.parseBoolean(request.getQueryParamByKey("deploy"));
        MavenSettingModel mavenSettingModel = (MavenSettingModel) request.getImodel();
        String servletContextUrl = HttpUtils.getServletContextUrlArtifactoryPreferred(request.getServletRequest());
        // generate maven settings
        String mavenSnippet = generateSettings(servletContextUrl, mavenSettingModel, response);
        if (downloadScript) {
            String mavenSnippetForDownload = prepareSnippetCredentialsContent(mavenSettingModel, mavenSnippet);
            ScriptDownload scriptDownload = new ScriptDownload();
            scriptDownload.setFileContent(mavenSnippetForDownload);
            ((StreamRestResponse) response).setDownload(true);
            ((StreamRestResponse) response).setDownloadFile("settings.xml");
            response.iModel(scriptDownload);
        } else if (deployMavenSettings) {
            String savedSnippetName = MultiPartUtils.saveSettingToTempFolder(mavenSnippet);
            MavenSettingModel mavenSettingsDeploy = new MavenSettingModel("settings.xml", savedSnippetName);
            response.iModel(mavenSettingsDeploy);
        } else {
            // update maven setting model
            mavenSnippet = prepareSnippetCredentialsContent(mavenSettingModel, mavenSnippet);
            MavenSettingModel mavenSnippetModel = new MavenSettingModel(mavenSnippet);
            mavenSnippetModel.clearProps();
            response.iModel(mavenSnippetModel);
        }
    }

    private String prepareSnippetCredentialsContent(MavenSettingModel mavenSettingModel, String mavenSnippet) {
        if (StringUtils.isNotEmpty(mavenSettingModel.getPassword())) {
            mavenSnippet = getMavenSnippetForDownload(mavenSnippet);
            return addPasswordToSnippet(mavenSnippet, mavenSettingModel.getPassword());
        }
        return createHumanReadableSnippet(mavenSnippet);
    }

    /**
     * Prepares a local maven settings model to be used by the maven service.<br>
     * Published as a protected method because a simple extension point is needed by the dashboard to append servers to
     * the model.
     *
     * @param servletContextUrl Current context URL
     * @return Local settings model
     */
    protected MavenSettings assembleLocalSettingsModel(String servletContextUrl,
            MavenSettingModel mavenSettingModel) {
        //Build settings object from the user selections in the panel
        MavenSettings mavenSettings = new MavenSettings(servletContextUrl);

        //Add release and snapshot choices
        String releases = mavenSettingModel.getRelease();
        mavenSettings.addReleaseRepository(new MavenSettingsRepository("central", releases, false));
        String snapshots = mavenSettingModel.getSnapshot();
        mavenSettings.addReleaseRepository(new MavenSettingsRepository("snapshots", snapshots, true));

        //Add plugin release and snapshot choices
        String pluginReleases = mavenSettingModel.getPluginRelease();
        mavenSettings.addPluginRepository(new MavenSettingsRepository("central", pluginReleases, false));
        String pluginSnapshots = mavenSettingModel.getPluginSnapshot();
        mavenSettings.addPluginRepository(new MavenSettingsRepository("snapshots", pluginSnapshots, true));

        //Add the "mirror any" repo, if the user has selected it
        if (isMirrorAnyRepo(mavenSettingModel)) {
            String mirror = mavenSettingModel.getMirror();
            mavenSettings.addMirrorRepository(new MavenSettingsMirror(mirror, mirror, "*"));
        }

        if (!securityService.isAnonymous() || !securityService.isAnonAccessEnabled()) {
            AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
            FilteredResourcesAddon filteredResourcesWebAddon =
                    addonsManager.addonByType(FilteredResourcesAddon.class);
            String currentUsername = filteredResourcesWebAddon.getGeneratedSettingsUsernameTemplate();
            String currentPassword = filteredResourcesWebAddon.getGeneratedSettingsUserCredentialsTemplate(true);

            mavenSettings.addServer(new MavenSettingsServer("central", currentUsername, currentPassword));
            mavenSettings.addServer(new MavenSettingsServer("snapshots", currentUsername, currentPassword));
            if (isMirrorAnyRepo(mavenSettingModel)) {
                mavenSettings.addServer(
                        new MavenSettingsServer(mavenSettingModel.getMirror(), currentUsername, currentPassword));
            }
        }
        return mavenSettings;
    }

    private boolean isMirrorAnyRepo(MavenSettingModel mavenSettingModel) {
        return StringUtils.isNotEmpty(mavenSettingModel.getMirror());
    }

    /**
     * generate maven settings
     *
     * @param servletContextUrl - servlet context
     * @param mavenSettingModel - maven settings model
     * @param restResponse      - rest response
     */
    public String generateSettings(String servletContextUrl, MavenSettingModel mavenSettingModel,
            RestResponse restResponse) {
        //Make sure URL ends with slash
        if (!servletContextUrl.endsWith("/")) {
            servletContextUrl += "/";
        }

        MavenSettings mavenSettings = assembleLocalSettingsModel(servletContextUrl, mavenSettingModel);
        try {
            //Generate XML settings content
            return mavenService.generateSettings(mavenSettings);
        } catch (IOException ioe) {
            String message = ioe.getMessage();
            log.error(message);
            restResponse.error("Maven settings could no be generated.");
        }
        return null;
    }

    /**
     * generate maven settigs for download
     *
     * @param mavenTemplateSnippet - maven template snippet
     * @return - maven settings
     */
    private String getMavenSnippetForDownload(String mavenTemplateSnippet) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        FilteredResourcesAddon filteredResourcesWebAddon = addonsManager.addonByType(
                FilteredResourcesAddon.class);
        String filtered = null;
        try {
            filtered = filteredResourcesWebAddon.filterResource(null,
                    (Properties) InfoFactoryHolder.get().createProperties(),
                    new StringReader(mavenTemplateSnippet));
        } catch (Exception e) {
            log.error("Unable to filter settings: {}", e.getMessage());

        }
        return filtered;
    }

    @Override
    protected Logger getLog() {
        return log;
    }
}