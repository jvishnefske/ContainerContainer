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

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.filteredresources.FilteredResourcesAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.rest.common.service.RestResponse;
import org.slf4j.Logger;

import java.io.StringReader;

/**
 * @author Rotem Kfir
 */
public abstract class GetSettingSnippetService {

    protected static final String READABLE_ARTIFACTORY_USER_NAME_TEMPLATE = "${ARTIFACTORY_USER}";
    protected static final String READABLE_ARTIFACTORY_PASSWORD_TEMPLATE = "${ARTIFACTORY_PASSWORD}";
    protected static final String READABLE_ARTIFACTORY_ESCAPED_PASSWORD_TEMPLATE = "${ARTIFACTORY_ESCAPED_PASSWORD}";

    protected abstract Logger getLog();

    /**
     * Replace place holders with values. If we couldn't retrieve the user's password we use the credentials that the
     * user has manually inserted
     */
    String filterResource(RestResponse restResponse, FilteredResourcesAddon filteredResourcesWebAddon, String snippet,
            String password) {
        try {
            String filtered = filteredResourcesWebAddon.filterResource(null,
                    (org.artifactory.md.Properties) InfoFactoryHolder.get().createProperties(),
                    new StringReader(snippet));
            return addPasswordToSnippet(filtered, password);
        } catch (Exception e) {
            getLog().error("Unable to filter file: " + e.getMessage());
            restResponse.error(e.getMessage());
        }
        return snippet;
    }

    /**
     * If we couldn't retrieve the user's password we use the credentials that the user has manually inserted
     */
    String addPasswordToSnippet(String snippet, String password) {
        if (StringUtils.isBlank(password)) {
            return snippet;
        }
        return snippet.replace("*** Insert encrypted password here ***", password);
    }

    protected String createHumanReadableSnippet(String snippetContent) {

        FilteredResourcesAddon filteredResourcesWebAddon =
                ContextHelper.get().beanForType(AddonsManager.class).addonByType(FilteredResourcesAddon.class);

        String usernameTemplate = filteredResourcesWebAddon.getGeneratedSettingsUsernameTemplate();
        String credentialsTemplate = filteredResourcesWebAddon.getGeneratedSettingsUserCredentialsTemplate(false);
        String escapedCredentialsTemplate = filteredResourcesWebAddon.getGeneratedSettingsUserCredentialsTemplate(true);

        String result = snippetContent;
        if (StringUtils.isNotEmpty(usernameTemplate)) {
            result = result.replace(usernameTemplate, READABLE_ARTIFACTORY_USER_NAME_TEMPLATE);
        }
        if (StringUtils.isNotEmpty(credentialsTemplate)) {
            result = result.replace(credentialsTemplate, READABLE_ARTIFACTORY_PASSWORD_TEMPLATE);
        }
        if (StringUtils.isNotEmpty(escapedCredentialsTemplate)) {
            result = result.replace(escapedCredentialsTemplate, READABLE_ARTIFACTORY_ESCAPED_PASSWORD_TEMPLATE);
        }
        return result;
    }
}
