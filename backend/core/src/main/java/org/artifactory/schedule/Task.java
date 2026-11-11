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

import com.google.common.collect.ImmutableMap;
import org.artifactory.concurrent.StateAware;

/**
 * Represents the control unit over execution (as opposed to the actual work callback). Has a state that can be
 * controlled through the {@link TaskService}.
 * <p/>
 *
 * @author Yoav Landman
 */
public interface Task extends StateAware {
    String TASK_TOKEN = "TASK_TOKEN";
    String JOB_NAME = "JOB_NAME";
    String JOB_OUTCOME = "JOB_OUTCOME";
    String CONCURRENCY_KEY = "CONCURRENCY_KEY";
    String ALLOWED_AFFINITY = "ALLOWED_AFFINITY";
    String HARD_AFFINITY = "HARD_AFFINITY";
    String TASK_AUTHENTICATION = "TASK_AUTHENTICATION";
    String TASK_RUN_CLUSTER_SINGLETON = "TASK_RUN_CLUSTER_SINGLETON";
    String REPO_KEY = "repoKey";
    String PUSH_REPLICATION_URL = "repoUrl";
    String ARTIFACTORY_GROUP = "artifactory";
    String SCHEDULER_USERNAME = "scheduler";

    int MAX_LOCK_NAME_PREFIX = 200;
    boolean DEFAULT_TASK_CLUSTER_SINGLETON = true;

    Class<? extends TaskCallback> getType();

    String getTaskName();

    String getTaskId();

    default int getRunId() {
        throw new IllegalStateException(
                "Task is not logged and does not have run ID. Did you forget to mark the @JobCommand annotation" +
                        " as 'monitored=true'?");
    }

    TaskState getCurrentState();

    long getLastStarted();

    ImmutableMap getAttributeMap();

    /**
     * @return Optional task description.
     */
    String getDescription();

    boolean isRunning();

    boolean isRunningOnCluster();

    /**
     * Whether this task is non-cyclic one and is canceled after a single execution
     */
    boolean isSingleExecution();

    boolean processActive();

    boolean processActiveOnCluster();

    void addAttribute(String key, Object value);

    Object getAttribute(String key);

    boolean keyEquals(Object... keyValues);

    boolean keyEquals(Task task);

    Object[] getKeyValues();

    /**
     * Needs to be called from the execution loop of the task that wants to check if to pause or to stop
     *
     * @return returns {@code true} if task should stop, {@code false} otherwise.
     */
    boolean blockIfPausedAndShouldBreak();

    /**
     * Report a custom task outcome to use in metric labels (instead of "ok" or "failed")
     * @param outcome - the custom outcome string to put in the 'result' label
     */
    default void setOutcome(String outcome) {
        addAttribute(Task.JOB_OUTCOME, outcome);
    }
}
