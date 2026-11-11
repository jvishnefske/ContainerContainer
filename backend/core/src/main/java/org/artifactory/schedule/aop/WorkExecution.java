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

package org.artifactory.schedule.aop;

import lombok.Getter;

/**
 * @author gidis
 */
@Getter
class WorkExecution {
    private final String workQueueName;
    private final boolean blockUntilFinished; //Signifies a thread is polling on this work item to finish.
    private final boolean delayAfterCommit;
    private final boolean failIfNoSession;

    WorkExecution(String workQueueName, boolean blockUntilFinished, boolean delayAfterCommit, boolean failIfNoSession) {
        this.workQueueName = workQueueName;
        this.blockUntilFinished = blockUntilFinished;
        this.delayAfterCommit = delayAfterCommit;
        this.failIfNoSession = failIfNoSession;
    }
}
