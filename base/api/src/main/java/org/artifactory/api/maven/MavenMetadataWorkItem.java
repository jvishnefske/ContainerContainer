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

package org.artifactory.api.maven;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.artifactory.repo.RepoPath;
import org.artifactory.workqueue.WorkItem;

import jakarta.annotation.Nonnull;

/**
 * @author gidis
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class MavenMetadataWorkItem extends WorkItem {
    private final RepoPath repoPath;
    private final boolean recursive;
    private final boolean mavenPlugin;
    /**
     * True when this work item was triggered by a manual reindex (e.g. the
     * {@code POST .../calculateMetadata/...} REST endpoint), false when triggered by an upload / move /
     * copy / delete event in the storage interceptor pipeline. Used to tag indexing duration metrics with the
     * correct {@code trigger} value (reindex vs. index).
     */
    private final boolean reindex;

    public MavenMetadataWorkItem(RepoPath repoPath, boolean recursive) {
        this(repoPath, recursive, true, false);
    }

    public MavenMetadataWorkItem(RepoPath repoPath, boolean recursive, boolean mavenPlugin) {
        this(repoPath, recursive, mavenPlugin, false);
    }

    public MavenMetadataWorkItem(RepoPath repoPath, boolean recursive, boolean mavenPlugin, boolean reindex) {
        this.repoPath = repoPath;
        this.recursive = recursive;
        this.mavenPlugin = mavenPlugin;
        this.reindex = reindex;
    }

    @Override
    public String toString() {
        return "MavenMetadataWorkItem{" +
                "repoPath=" + repoPath +
                ", recursive=" + recursive +
                ", mavenPlugin=" + mavenPlugin +
                ", reindex=" + reindex +
                '}';
    }

    @Override
    @Nonnull
    public String getUniqueKey() {
        return this.toString();
    }
}
