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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tree;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TabsAndActions;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TreeNodeActionsPopulator;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions.TreeNodeTabsPopulator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Shay Yaakov
 */
@Slf4j
@Component
public class GetTreeNodeTabsAndActionsService implements RestService<TabsAndActions> {

    @Autowired
    private TreeNodeActionsPopulator actionsPopulator;
    @Autowired
    private TreeNodeTabsPopulator tabsPopulator;
    @Autowired
    private SecurityService securityService;

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public void setActionsPopulator(TreeNodeActionsPopulator treeNodeActionsPopulator) {
        this.actionsPopulator = treeNodeActionsPopulator;
    }

    public void setTabsPopulator(TreeNodeTabsPopulator TreeNodeTabsPopulator) {
        this.tabsPopulator = TreeNodeTabsPopulator;
    }

    @Override
    public void execute(ArtifactoryRestRequest<TabsAndActions> request, RestResponse response) {
        TabsAndActions tabsAndActions = request.getImodel();
        final String type = tabsAndActions.getType();

        if (type == null) {
            log.warn("TabsAndActions type is null, cannot populate tabs and actions.");
            response.error("TabsAndActions type is null, cannot populate tabs and actions.").responseCode(400);
            return;
        }

        switch (tabsAndActions.getType()) {
            case "repository", "virtualRemoteRepository" -> {
                if (!(tabsAndActions.getRepoType().equals("trash")
                        && securityService.isAnonymous())) {
                    actionsPopulator.populateForRepository(tabsAndActions);
                    tabsPopulator.populateForRepository(tabsAndActions);
                }
            }
            case "folder", "virtualRemoteFolder" -> {
                actionsPopulator.populateForFolder(tabsAndActions);
                tabsPopulator.populateForFolder(tabsAndActions);
            }
            case "file", "archive", "virtualRemoteFile" -> {
                actionsPopulator.populateForFile(tabsAndActions);
                tabsPopulator.populateForFile(tabsAndActions);
            }
        }
        response.iModel(tabsAndActions);
    }
}