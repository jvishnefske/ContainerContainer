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

package org.artifactory.api.repo.exception;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;

/**
 * A rejection exception thrown when attempting to import to a blacked out repository
 *
 * @author Noam Y. Tenne
 */
@Slf4j
public class BlackedOutException extends RejectedArtifactException {

    /**
     * Main constructor
     *
     * @param rejectingRepo    The descriptor of the rejecting repo
     * @param rejectedArtifact The repo path of the rejected artifact
     */
    public BlackedOutException(RepoConfig rejectingRepo, RepoPath rejectedArtifact) {
        super(rejectingRepo, rejectedArtifact);
        log.debug("Blacked out exception thrown for repo: '{}' and artifact: '{}'",
                rejectingRepo, rejectedArtifact, this);
    }

    @Override
    public String getMessage() {
        return "The repository '" + rejectingRepo.getKey() +
                "' is blacked out and cannot serve artifact '" +
                rejectedArtifact + "'.";
    }
}