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

package org.artifactory.ui.rest.service.admin.services.backups;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.HttpStatus;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.util.AolUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author Chen Keinan
 */
@Component
public class CreateBackupService implements RestService {

    @Autowired
    private CentralConfigService centralConfigService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        AolUtils.assertNotAol("CreateBackup");
        BackupDescriptor backupDescriptor = (BackupDescriptor) request.getImodel();
        Optional<String> validationError = BackupDescriptorValidator.validate(backupDescriptor);
        if (validationError.isPresent()) {
            response.responseCode(HttpStatus.SC_BAD_REQUEST).error(validationError.get());
            return;
        }
        createBackup(backupDescriptor);
        updateResponseData(response, backupDescriptor);
    }

    /**
     * update feedback msg and responseCode
     */
    private void updateResponseData(RestResponse artifactoryResponse, BackupDescriptor backupDescriptor) {
        artifactoryResponse.info("Backup " + backupDescriptor.getKey() + " successfully created.");
        artifactoryResponse.responseCode(HttpServletResponse.SC_CREATED);
    }

    /**
     * create new backup to config descriptor
     *
     * @param backupDescriptor - new backup descriptor
     */
    private void createBackup(BackupDescriptor backupDescriptor) {
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        mutableDescriptor.addBackup(backupDescriptor);
        centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor, backupDescriptor);
    }
}
