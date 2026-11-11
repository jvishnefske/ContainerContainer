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

package org.artifactory.schedule;

import com.google.common.collect.Sets;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.TaskDescriptor;
import org.artifactory.spring.InternalContextHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * User: freds
 * Date: 7/10/11
 * Time: 5:54 PM
 */
public abstract class BaseTaskServiceDescriptorHandler<T extends TaskDescriptor>
        implements TaskServiceDescriptorHandler<T> {
    private static final Logger log = LoggerFactory.getLogger(BaseTaskServiceDescriptorHandler.class);

    public void unschedule() {
        TaskService taskService = InternalContextHelper.get().getBean(TaskService.class);
        log.info("Removing all job " + jobName() + " from task service handler.");
        taskService.cancelTasks(getAllPredicate(), true);
    }

    public void reschedule() {
        TaskService taskService = InternalContextHelper.get().getBean(TaskService.class);
        List<T> newDescriptors = getNewDescriptors();
        if (newDescriptors.isEmpty()) {
            log.info("No " + jobName() + " configured. " + jobName() + " is disabled.");
            taskService.cancelTasks(getAllPredicate(), true);
            return;
        }
        Set<T> oldTasksNotToCancel = Sets.newHashSet();
        for (T newDesc : newDescriptors) {
            T oldDescriptor = findOldFromNew(newDesc);
            if (oldDescriptor != null && oldDescriptor.sameTaskDefinition(newDesc)) {
                oldTasksNotToCancel.add(oldDescriptor);
            } else {
                if (oldDescriptor != null) {
                    oldTasksNotToCancel.add(oldDescriptor);
                    taskService.cancelTasks(getPredicate(oldDescriptor), true);
                }
                activate(newDesc, false, new BasicStatusHolder());
            }
        }
        List<T> oldDescriptors = getOldDescriptors();
        if (!oldDescriptors.isEmpty()) {
            for (T oldDesc : oldDescriptors) {
                if (!oldTasksNotToCancel.contains(oldDesc)) {
                    taskService.cancelTasks(getPredicate(oldDesc), true);
                }
            }
        }
    }

    /**
     * Performs activation of a task with retry. Upon failure, will try up to 3 times to activate the task again
     * By default set to true. This can be disabled (to perform without retries) by changing
     * the constant value: {@link ConstantValues.taskRetryEnable}
     * @param taskService - The task service to perform the activation
     * @param newDesc - The new descriptor
     * @param oldDescriptor - The old descriptor
     * @param numberOfTries - How many retries already occured.
     */
    void activateWithRetry(TaskService taskService, T newDesc, T oldDescriptor, int numberOfTries) {
        try {
            if (oldDescriptor != null) {
                taskService.cancelTasks(getPredicate(oldDescriptor), true);
            }
            activate(newDesc, false, new BasicStatusHolder());
        } catch (IllegalStateException e) {
            if (!ConstantValues.taskRetryEnable.getBoolean()) {
                throw e;
            }
            if (numberOfTries >= 3) {
                log.error("Exceeded the number of retries for activating tasks.", e);
                throw e;
            }
            numberOfTries++;
            log.warn("Exception occurred during task activation: {}", e.getMessage());
            log.debug("Exception occurred during task activation: ", e);
            try {
                Thread.sleep(500);
            } catch (InterruptedException interruptedException) {
                log.warn("Thread got interrupted: {}", interruptedException.getMessage());
                log.debug("Thread got interrupted", interruptedException);
            }
            activateWithRetry(taskService, newDesc, oldDescriptor, numberOfTries);
        }
    }
}
