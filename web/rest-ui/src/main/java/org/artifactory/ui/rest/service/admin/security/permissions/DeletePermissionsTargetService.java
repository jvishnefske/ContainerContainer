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

package org.artifactory.ui.rest.service.admin.security.permissions;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.service.security.permissions.RestSecurityRequestHandlerV2;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.ui.rest.model.admin.security.permissions.DeletePermissionTargetModel;
import org.artifactory.util.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;

/**
 * @author Dan Feldman
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeletePermissionsTargetService implements RestService<DeletePermissionTargetModel> {

    private final RestSecurityRequestHandlerV2 securityRequestHandler;
    private final SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest<DeletePermissionTargetModel> request, RestResponse response) {
        if (!securityService.isAdmin()) {
            response.responseCode(SC_FORBIDDEN);
            return;
        }
        DeletePermissionTargetModel deleteRequest = request.getImodel();
        List<String> errors = Lists.newArrayList();
        deleteRequest.getPermissionTargetNames()
                .stream()
                .filter(StringUtils::isNotBlank)
                .forEach(permissionName -> deleteAcl(permissionName, response, errors));
        prepareResponse(deleteRequest, response, errors);
    }

    private void prepareResponse(DeletePermissionTargetModel deleteRequest, RestResponse response,
            List<String> errors) {
        if (CollectionUtils.notNullOrEmpty(errors)) {
            response.errors(errors);
            if (errors.size() > 1) {
                //consolidate all error codes
                response.responseCode(SC_BAD_REQUEST);
            }
        } else if (deleteRequest.getPermissionTargetNames().size() > 1) {
            response.info(
                    "Successfully removed " + deleteRequest.getPermissionTargetNames().size() + " permission targets");
        } else if (deleteRequest.getPermissionTargetNames().size() == 1) {
            response.info(
                    "Successfully removed permission target '" + deleteRequest.getPermissionTargetNames().get(0) + "'");
        }
    }

    private void deleteAcl(String permissionName, RestResponse response, List<String> errors) {
        try {
            securityRequestHandler.deletePermissionTarget(permissionName);
        } catch (NotFoundException nfe) {
            errors.add(nfe.getMessage());
            response.responseCode(SC_NOT_FOUND);
            log.debug("", nfe);
        } catch (BadRequestException bre) {
            errors.add(bre.getMessage());
            response.responseCode(SC_BAD_REQUEST);
            log.debug("", bre);
        } catch (Exception e) {
            errors.add("Unexpected error deleting permission target '" + permissionName + "' : " + e.getMessage());
            log.debug("", e);
        }
    }
}
