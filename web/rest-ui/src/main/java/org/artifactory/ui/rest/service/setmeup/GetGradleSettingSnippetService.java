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

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.VersionUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.filteredresources.FilteredResourcesAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.service.StreamRestResponse;
import org.artifactory.ui.rest.model.setmeup.GradleSettingModel;
import org.artifactory.ui.rest.model.setmeup.ScriptDownload;
import org.artifactory.ui.utils.MultiPartUtils;
import org.artifactory.util.HttpUtils;
import org.artifactory.util.RepoLayoutUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * @author chen Keinan
 */
@Slf4j
@Component
public class GetGradleSettingSnippetService extends GetSettingSnippetService implements RestService {

    private static final String ARTIFACTORY_USER = "${artifactory_user}";
    private static final String ARTIFACTORY_PASSWORD = "${artifactory_password}";
    private static final String ARTIFACTORY_CONTEXT_URL = "${artifactory_contextUrl}";
    private final String JCENTER_DUMMY_REPO_KEY = "Bintray -> jcenter";

    @Autowired
    SecurityService securityService;
    @Autowired
    CentralConfigService centralConfigService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        GradleSettingModel gradleSettingModel = (GradleSettingModel) request.getImodel();
        boolean downloadScript = Boolean.parseBoolean(request.getQueryParamByKey("downloadScript"));
        boolean downloadGradleProps = Boolean.parseBoolean(request.getQueryParamByKey("gradleProps"));
        boolean deployGradleSettings = Boolean.parseBoolean(request.getQueryParamByKey("deploy"));
        String servletContextUrl = HttpUtils.getServletContextUrlArtifactoryPreferred(request.getServletRequest());
        // generate maven settings
        String gradleSnippet = generateSettings(servletContextUrl, gradleSettingModel, response);

        if (downloadGradleProps) {
            downLoadGradleProps(response, servletContextUrl, gradleSettingModel.getPassword());
            return;
        }
        if (downloadScript) {
            downloadGradleBuildFile(response, gradleSnippet);
        } else if (deployGradleSettings) {
            String savedSnippetName = MultiPartUtils.saveSettingToTempFolder(gradleSnippet);
            GradleSettingModel gradleSettingsDeploy = new GradleSettingModel("build.gradle", savedSnippetName);
            response.iModel(gradleSettingsDeploy);
        } else {
            // update maven setting model
            gradleSnippet = prepareSnippetCredentialsContent(response, gradleSnippet, servletContextUrl,
                    gradleSettingModel.getPassword());

            GradleSettingModel mavenSnippetModel = new GradleSettingModel(gradleSnippet);
            mavenSnippetModel.clearProps();
            response.iModel(mavenSnippetModel);
        }
    }

    private <T> String prepareSnippetCredentialsContent(RestResponse<T> restResponse, String snippet,
            String servletContextUrl, String password) {

        if (StringUtils.isBlank(password)) {
            return snippet.replace(ARTIFACTORY_USER, READABLE_ARTIFACTORY_USER_NAME_TEMPLATE)
                    .replace(ARTIFACTORY_PASSWORD, READABLE_ARTIFACTORY_PASSWORD_TEMPLATE);
        }

        FilteredResourcesAddon filteredResourcesWebAddon =
                ContextHelper.get().beanForType(AddonsManager.class).addonByType(FilteredResourcesAddon.class);

        snippet = snippet.replace(ARTIFACTORY_USER, filteredResourcesWebAddon.getGeneratedSettingsUsernameTemplate())
                .replace(ARTIFACTORY_PASSWORD,
                        filteredResourcesWebAddon.getGeneratedSettingsUserCredentialsTemplate(false))
                .replace(ARTIFACTORY_CONTEXT_URL, servletContextUrl);

        return filterResource(restResponse, filteredResourcesWebAddon, snippet, password);
    }

    /**
     * download Gradle build File
     *
     * @param response      - encapsulate data related to response
     * @param gradleSnippet - gradle snippet file
     */
    private void downloadGradleBuildFile(RestResponse response, String gradleSnippet) {
        //download build.gradle file
        ScriptDownload scriptDownload = new ScriptDownload();
        scriptDownload.setFileContent(gradleSnippet);
        ((StreamRestResponse) response).setDownload(true);
        ((StreamRestResponse) response).setDownloadFile("build.gradle");
        response.iModel(scriptDownload);
    }

    /**
     * download gradle build props
     *
     * @param response          - encapsulate data related to response
     * @param servletContextUrl - servlet context
     * @param password          - the credentials the user inserted
     */
    private void downLoadGradleProps(RestResponse response, String servletContextUrl, String password) {
        String gradleProperties = getGradleProperties(response, servletContextUrl, password);
        gradleProperties = prepareSnippetCredentialsContent(response, gradleProperties, servletContextUrl, password);
        ScriptDownload scriptDownload = new ScriptDownload();
        scriptDownload.setFileContent(gradleProperties);
        ((StreamRestResponse) response).setDownload(true);
        ((StreamRestResponse) response).setDownloadFile("gradle.properties");
        response.iModel(scriptDownload);
    }

    /**
     * generate gradle settings
     */
    public String generateSettings(String servletContextUrl, GradleSettingModel gradleSettingModel,
            RestResponse response) {
        Properties templateProperties = getTemplateProperties(servletContextUrl, gradleSettingModel, response);
        return replaceValuesAndGetTemplateValue("/build.gradle.template", templateProperties, response);
    }

    private Properties getTemplateProperties(String servletContextUrl, GradleSettingModel gradleSettingModel,
            RestResponse response) {
        Properties repoDetailsProperties = new Properties();

        // General
        repoDetailsProperties.setProperty("artifactory.contextUrl", servletContextUrl);
        repoDetailsProperties.setProperty("gradle.build.1tab", "    ");
        repoDetailsProperties.setProperty("gradle.build.2tabs", "        ");
        repoDetailsProperties.setProperty("gradle.build.3tabs", "            ");
        repoDetailsProperties.setProperty("gradle.build.4tabs", "                ");

        //jcenter overrides both maven and ivy
        if (gradleSettingModel.getPluginRepoKey().equals(JCENTER_DUMMY_REPO_KEY)) {
            repoDetailsProperties.setProperty("maven.repo", "jcenter()");
            repoDetailsProperties.setProperty("ivy.repo", "");
            // Plugins
        } else {
            repoDetailsProperties.setProperty("plugins.repository.url",
                    getFullRepositoryUrl(servletContextUrl, gradleSettingModel.getPluginRepoKey()));
            if (gradleSettingModel.getPluginUseMaven()) {
                repoDetailsProperties.setProperty("maven.repo",
                        replaceValuesAndGetTemplateValue("/build.gradle.maven.repo.template", repoDetailsProperties,
                                response));
            } else {
                repoDetailsProperties.setProperty("maven.repo", "");
            }

            if (gradleSettingModel.getPluginUseIvy()) {
                repoDetailsProperties.setProperty("ivy.repo",
                        replaceValuesAndGetTemplateValue("/build.gradle.ivy.repo.template", repoDetailsProperties,
                                response));
            } else {
                repoDetailsProperties.setProperty("ivy.repo", "");
            }
        }
        // Build info plugin version
        setBuildInfoPluginVersion(gradleSettingModel, repoDetailsProperties);
        // Publisher
        if (gradleSettingModel.getPublisherUseIvy()) {
            repoDetailsProperties.setProperty("ivy.publisher",
                    replaceValuesAndGetTemplateValue("/build.gradle.ivy.publisher.template", repoDetailsProperties,
                            response));
            repoDetailsProperties.setProperty("libs.publisher.ivy.pattern",
                    getDescriptorPattern(getLayout(gradleSettingModel.getLibsPublisherLayouts())));
            repoDetailsProperties.setProperty("libs.publisher.artifact.pattern",
                    getArtifactPattern(getLayout(gradleSettingModel.getLibsPublisherLayouts())));
            repoDetailsProperties.setProperty("libs.publisher.maven.compatible",
                    Boolean.toString(getFullDescriptorPattern(servletContextUrl,
                            gradleSettingModel.getLibsPublisherRepoKey(),
                            getLayout(gradleSettingModel.getLibsPublisherLayouts())).contains("pom")));
        } else {
            repoDetailsProperties.setProperty("ivy.publisher", "");
        }
        repoDetailsProperties.setProperty("libs.publisher.repoKey", gradleSettingModel.getLibsPublisherRepoKey());
        repoDetailsProperties
                .setProperty("libs.publisher.maven", Boolean.toString(gradleSettingModel.getPublisherUseMaven()));

        // Resolver
        if (gradleSettingModel.getResolverUseIvy()) {
            repoDetailsProperties.setProperty("ivy.resolver",
                    replaceValuesAndGetTemplateValue("/build.gradle.ivy.resolver.template", repoDetailsProperties,
                            response));
            repoDetailsProperties.setProperty("libs.resolver.ivy.pattern",
                    getDescriptorPattern(getLayout(gradleSettingModel.getLibsResolverLayout())));
            repoDetailsProperties.setProperty("libs.resolver.artifact.pattern",
                    getArtifactPattern(getLayout(gradleSettingModel.getLibsResolverLayout())));
            repoDetailsProperties.setProperty("libs.resolver.maven.compatible",
                    Boolean.toString(
                            getFullDescriptorPattern(servletContextUrl, gradleSettingModel.getLibsResolverRepoKey(),
                                    getLayout(gradleSettingModel.getLibsResolverLayout())).contains("pom")));
        } else {
            repoDetailsProperties.setProperty("ivy.resolver", "");
        }
        repoDetailsProperties.setProperty("libs.resolver.repoKey", gradleSettingModel.getLibsResolverRepoKey());
        repoDetailsProperties
                .setProperty("libs.resolver.maven", Boolean.toString(gradleSettingModel.getResolverUseMaven()));

        // Credentials
        Properties credentialProperties = new Properties();
        String resolveCreds = "";
        String publishCreds = "";
        String pluginsCreds = "";
        if (!securityService.isAnonymous() || !securityService.isAnonAccessEnabled()) {
            credentialProperties.setProperty("creds.line.break", "\n            ");
            resolveCreds = replaceValuesAndGetTemplateValue("/build.gradle.resolve.creds.template",
                    credentialProperties, response);
            publishCreds = replaceValuesAndGetTemplateValue("/build.gradle.publish.creds.template",
                    credentialProperties, response);
            pluginsCreds =
                    replaceValuesAndGetTemplateValue("/build.gradle.creds.template", credentialProperties, response);
        }
        repoDetailsProperties.setProperty("resolve.creds", resolveCreds);
        repoDetailsProperties.setProperty("publish.creds", publishCreds);
        repoDetailsProperties.setProperty("repo.creds", pluginsCreds);

        return repoDetailsProperties;
    }

    private void setBuildInfoPluginVersion(GradleSettingModel gradleSettingModel, Properties repoDetailsProperties) {
        if (isVersionAGreaterOrEqualToB(gradleSettingModel.getGradleVersion(), "8.0.0")) {
            repoDetailsProperties.setProperty("buildinfo.version", "6+");
        } else if (isVersionAGreaterOrEqualToB(gradleSettingModel.getGradleVersion(), "7.0.0")) {
            repoDetailsProperties.setProperty("buildinfo.version", "5+");
        } else {
            repoDetailsProperties.setProperty("buildinfo.version", "4+");
        }
    }

    private boolean isVersionAGreaterOrEqualToB(String stringVersionA, String stringVersionB) {
        try {
            if (StringUtils.isEmpty(stringVersionA)) {
                return true;
            }
            Version versionA = VersionUtil.parseVersion(stringVersionA, null, null);
            Version versionB = VersionUtil.parseVersion(stringVersionB, null, null);
            return versionA.compareTo(versionB) >= 0;
        } catch (Exception e) {
            log.warn("Invalid version format: {}", stringVersionA);
            return true;
        }
    }

    private String replaceValuesAndGetTemplateValue(String templateResourcePath,
            Properties templateProperties, RestResponse response) {
        InputStream gradleInitTemplateStream = null;
        try {
            gradleInitTemplateStream = getClass().getResourceAsStream(templateResourcePath);
            String gradleTemplate = IOUtils.toString(gradleInitTemplateStream);
            PropertyPlaceholderHelper propertyPlaceholderHelper = new PropertyPlaceholderHelper("${", "}");
            return propertyPlaceholderHelper.replacePlaceholders(gradleTemplate, templateProperties);
        } catch (IOException e) {
            String errorMessage = "An error occurred while preparing the Gradle Init Script template: ";
            response.error(errorMessage + e.getMessage());
            log.error(errorMessage, e);
        } finally {
            IOUtils.closeQuietly(gradleInitTemplateStream);
        }
        return "";
    }

    public String getFullRepositoryUrl(String servletContextUrl, String repoKey) {
        return getFullUrl(servletContextUrl, repoKey);
    }

    private String getFullUrl(String host, String path) {
        return host + "/" + path;
    }

    private RepoLayout getLayout(String layoutName) {
        RepoLayout layout = null;
        List<RepoLayout> repoLayouts = centralConfigService.getDescriptor().getRepoLayouts();
        for (RepoLayout repoLayout : repoLayouts) {
            if (repoLayout.getName().equals(layoutName)) {
                layout = repoLayout;
                break;
            }
        }
        return layout;
    }

    public String getDescriptorPattern(RepoLayout layout) {
        return RepoLayoutUtils.getDescriptorLayoutAsIvyPattern(layout);
    }


    public String getArtifactPattern(RepoLayout layout) {
        return RepoLayoutUtils.getArtifactLayoutAsIvyPattern(layout);
    }

    public String getFullDescriptorPattern(String servletContextUrl, String repoKey, RepoLayout layout) {
        return getFullUrl(getFullUrl(servletContextUrl, repoKey),
                RepoLayoutUtils.getDescriptorLayoutAsIvyPattern(layout));
    }

    private String getGradleProperties(RestResponse restResponse, String servletContextUrl, String password) {
        FilteredResourcesAddon filteredResourcesWebAddon =
                ContextHelper.get().beanForType(AddonsManager.class).addonByType(FilteredResourcesAddon.class);

        java.util.Properties credentialProperties = new java.util.Properties();
        credentialProperties.setProperty("auth.username",
                filteredResourcesWebAddon.getGeneratedSettingsUsernameTemplate());

        credentialProperties.setProperty("auth.password",
                filteredResourcesWebAddon.getGeneratedSettingsUserCredentialsTemplate(false));

        credentialProperties.setProperty("auth.contextUrl", servletContextUrl);

        String content = replaceValuesAndGetTemplateValue("/gradle.properties.template", credentialProperties,
                restResponse);

        return filterResource(restResponse, filteredResourcesWebAddon, content, password);
    }

    @Override
    protected Logger getLog() {
        return log;
    }
}
