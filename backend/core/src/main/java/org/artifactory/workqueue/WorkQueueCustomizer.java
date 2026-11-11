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
package org.artifactory.workqueue;

/**
 * The purpose of this interface is to provide an additional logic for the given workqueue.
 * For example reporting to metrics,new filters before execution of a task, etc..
 */
public interface WorkQueueCustomizer<T extends WorkItem> {

    /**
     * Returning customize work queue
     *
     * @param workQueue - work queue to customize
     */
    WorkQueue<T> customizeWorkQueue(WorkQueue<T> workQueue);
}
