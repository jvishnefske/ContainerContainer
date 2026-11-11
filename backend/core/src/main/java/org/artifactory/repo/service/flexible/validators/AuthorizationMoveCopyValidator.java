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

package org.artifactory.repo.service.flexible.validators;

import org.apache.http.HttpStatus;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gidis
 */
public class AuthorizationMoveCopyValidator implements MoveCopyValidator {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationMoveCopyValidator.class);
    private final AuthorizationService authService;

    public AuthorizationMoveCopyValidator(AuthorizationService authService) {
        this.authService = authService;
    }

    @Override
    public boolean validate(MoveCopyItemInfo element, MoveMultiStatusHolder status, MoveCopyContext context) {
        try {
            status.setActivateLogging(false);
            return validateInternal(element, status);
        } finally {
            status.setActivateLogging(true);
        }
    }

    private boolean validateInternal(MoveCopyItemInfo element, MoveMultiStatusHolder status) {
        RepoPath sourceRepoPath = element.getSourceRepoPath();
        if (!authService.canRead(sourceRepoPath)) {
            status.error("User doesn't have permissions to read '" + sourceRepoPath + "'. " +
                    "Needs read permissions.", HttpStatus.SC_FORBIDDEN, log);
            return false;
        }
        RepoPath targetRepoPath = element.getTargetRepoPath();
        if (element.getTargetItem() != null) {
            if (!authService.canDelete(targetRepoPath)) {
                status.error("User doesn't have permissions to override '" + targetRepoPath + "'. " +
                        "Needs delete permissions.", HttpStatus.SC_UNAUTHORIZED, log);
                return false;
            }
        } else if (!authService.canDeploy(targetRepoPath)) {
            status.error("User doesn't have permissions to create '" + targetRepoPath + "'. " +
                    "Needs write permissions.", HttpStatus.SC_FORBIDDEN, log);
            return false;
        }
        return true;
    }

    @Override
    public boolean isInterested(MoveCopyItemInfo itemInfo, MoveCopyContext context) {
        return !context.isSkipPermissions();
    }
}
