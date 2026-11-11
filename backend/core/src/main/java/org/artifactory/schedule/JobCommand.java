
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * User: freds Date: 7/6/11 Time: 4:34 PM
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface JobCommand {

    boolean singleton() default false;

    /**
     * Set to {@code true} for internal jobs that must not block graceful shutdown. Such jobs are ignored by
     * {@link TaskService#canBeShutdown()} even while running.
     */
    boolean shutdownTransparent() default false;

    /**
     * Set to allow task history record in quartz_task_history table
     */
    boolean monitored() default false;

    /**
     * Relevant only when {@link JobCommand#monitored()} is set to {@code true}.
     * Set if it is mandatory to use {@link org.artifactory.schedule.TaskUtils}'s create variants with external ID
     * param and provide it when creating the task
     */
    boolean externalIdMandatory() default false;

    TaskUser schedulerUser() default TaskUser.INVALID;

    /**
     * Set to allow manual activation and provide strategy for finding authentication token.
     */
    TaskUser manualUser() default TaskUser.INVALID;

    StopCommand[] commandsToStop() default {};

    String[] keyAttributes() default {};

    boolean clusterSingleton() default Task.DEFAULT_TASK_CLUSTER_SINGLETON;

    /**
     * Short optional description of the job.
     */
    String description() default "";

    /**
     * The system name for this Job. In the future may allow us to control with fine-grain if a specific job can or
     * cannot run on some node in the cluster.
     */
    JobName jobName();

    /**
     * Whether or not this task can be controlled by the Task Affinity protocol. In other words, some Jobs may not
     * want to allow the user the control where or whether the Job can run on a given node in the cluster.
     */
    boolean allowedAffinity() default true;

    /**
     * If a Job is tagged as hard affinity, the job will never fallback to running on any other node that isn't
     * defined as Affinity for it, even in situations where the affinity node(s) is down.
     */
    boolean hardAffinity() default false;
}

