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

package org.artifactory.rest.resource.repositories;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.rest.constant.YumRestConstants;
import org.artifactory.rest.common.util.PermissionHelper;
import org.artifactory.security.ArtifactoryPermission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import static org.artifactory.api.rest.constant.GpgRestConstanst.PASSPHRASE_HEADER_NAME;

/**
 * @author Noam Y. Tenne
 */
@Component
public class YumResource {

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private PermissionHelper permissionHelper;

    public Response calculateYumMetadata(@PathParam("repoKey") String repoKey, @QueryParam("path") String path,
            @QueryParam(YumRestConstants.PARAM_ASYNC) int async, HttpServletRequest request) {
        String passphrase = request.getHeader(PASSPHRASE_HEADER_NAME);
        if (StringUtils.isBlank(repoKey)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Target repository key cannot be blank").build();
        }
        permissionHelper.assertPermission(repoKey, ArtifactoryPermission.MANAGE);

        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        boolean isAsync = (async != 0);
        return restAddon.calculateYumMetadata(repoKey, path, isAsync, passphrase);
    }
}
