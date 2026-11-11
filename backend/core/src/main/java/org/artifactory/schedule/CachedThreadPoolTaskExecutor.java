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

import org.artifactory.async.AsyncExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * @author Yoav Landman
 */
public class CachedThreadPoolTaskExecutor extends ConcurrentTaskExecutor implements AsyncExecutor {

    public CachedThreadPoolTaskExecutor() {
        super(new ArtifactoryConcurrentExecutor());
    }

    public CachedThreadPoolTaskExecutor(String threadNamePrefix) {
        super(new ArtifactoryConcurrentExecutor(threadNamePrefix));
    }

    public CachedThreadPoolTaskExecutor(ArtifactoryConcurrentExecutor executor) {
        super(executor);
    }

    private ArtifactoryConcurrentExecutor getArtifactoryExecutor() {
        return (ArtifactoryConcurrentExecutor) getConcurrentExecutor();
    }

    public void destroy() {
        getArtifactoryExecutor().shutdown();
    }

    public <T> Future<T> submit(Runnable task, T result) {
        return getArtifactoryExecutor().submit(task, result);
    }

    @Override
    public <T> Future<T> execute(Callable<T> task) {
        return submit(task);
    }

    @Override
    public int getPendingTasks() {
        return getArtifactoryExecutor().getPendingTasks();
    }

    @Override
    public int getActiveTasks() {
        return getArtifactoryExecutor().getActiveCount();
    }
}
