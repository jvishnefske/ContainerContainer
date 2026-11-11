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

package org.artifactory.addon.plugin.replication;


import org.artifactory.addon.replication.ReplicationTargetInfo;

/**
 * @author Yoav Landman
 */
public class ReplicationCtx {

    private boolean skip;
    private final ReplicationTargetInfo targetInfo;

    public ReplicationCtx(ReplicationTargetInfo targetInfo) {
        this.targetInfo = targetInfo;
    }

    public boolean isSkip() {
        return skip;
    }

    /**
     * Earlier skip variable was present as context variable where it was directly accessible and mutable from the
     * plugin script by user.
     * Now, from workers this is not possible and the flow should be if any addOn sets skip to true, and others are
     * false, then replication should be skipped.
     * Workers - true, Plugins - true, skip - true
     * Workers - true, Plugins - false, skip - true
     * Workers - false, Plugins - true, skip - true
     * Workers - false, Plugins - false, skip - false
     */
    public void setSkip(boolean skip) {
        if (this.skip) {
            return;
        }
        this.skip = skip;
    }

    public ReplicationTargetInfo getTargetInfo() {
        return targetInfo;
    }
}
