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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.service.security.permissions.RestSecurityRequestHandlerV2;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ConflictException;
import org.artifactory.security.permissions.PermissionTargetModel;
import org.artifactory.ui.rest.model.admin.security.permissions.CombinedPermissionTargetUIModel;
import org.artifactory.util.UnsupportedByLicenseException;
import org.jfrog.common.validator.XSSValidator;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NOT_IMPLEMENTED;
import static org.artifactory.security.PermissionTargetNaming.NAMING_UI;
import static org.artifactory.ui.rest.service.admin.security.permissions.util.UIPermissionServiceUtil.backendModelFromUIModel;

/**
 * @author Dan Feldman
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreatePermissionsTargetService implements RestService<CombinedPermissionTargetUIModel> {

    private final RestSecurityRequestHandlerV2 securityRequestHandler;
    private final SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest<CombinedPermissionTargetUIModel> request, RestResponse response) {
        if (!securityService.isAdmin()) {
            response.responseCode(SC_FORBIDDEN);
            return;
        }
        PermissionTargetModel backendModel = backendModelFromUIModel(request);
        String permissionName = backendModel.getName();
        Optional<String> possibleError = XSSValidator.validate("Name", permissionName);
        if (possibleError.isPresent()) {
            response.error(possibleError.get());
            return;
        }
        try {
            //The underlying implementation protects against non-admins calling this.
            securityRequestHandler.createPermissionTarget(permissionName, backendModel, NAMING_UI);
            response.info("Successfully created permission target '" + permissionName + "'.").responseCode(SC_CREATED);
        } catch (ConflictException cfe) {
            response.error(cfe.getMessage());
            response.responseCode(SC_CONFLICT);
        } catch (BadRequestException bre) {
            response.error(bre.getMessage());
            response.responseCode(SC_BAD_REQUEST);
        } catch (UnsupportedByLicenseException ule) {
            response.error(ule.getMessage());
            response.responseCode(SC_NOT_IMPLEMENTED);
        } catch (Exception e) {
            response.error("An unexpected error has occurred please review the logs: " + e.getMessage());
            log.error("Failed to create Permission target '" + permissionName + "': ", e);
        }
    }
}
