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

package com.jfrog.build.acl.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Date;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static com.jfrog.build.acl.util.BuildInfoUtils.parseBuildTime;
import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.apache.commons.lang3.StringUtils.EMPTY;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BuildRunImpl implements BuildRun {

    @ToString.Include
    private final long buildId;

    @ToString.Include
    private final String buildRepo;

    @ToString.Include
    @EqualsAndHashCode.Include
    private final String name;

    @ToString.Include
    @EqualsAndHashCode.Include
    private final String number;

    @ToString.Include
    @EqualsAndHashCode.Include
    private final String started;

    private final String ciUrl;
    private final String releaseStatus;
    private final String releaseRepoKey;

    /**
     * DO NOT USE IT - ONLY FOR TESTS
     */
    public BuildRunImpl(
            String name,
            String number,
            Date started) {
        this(name, number, formatBuildTime(started.getTime()), BUILDINFO_REPO_KEY);
    }

    /**
     * DO NOT USE IT - ONLY FOR TESTS
     */
    public BuildRunImpl(
            String name,
            String number,
            String started) {
        this(name, number, started, null, null, BUILDINFO_REPO_KEY);
    }

    public BuildRunImpl(
            String name,
            String number,
            String started,
            String buildRepo) {
        this(name, number, started, null, null, buildRepo);
    }

    public BuildRunImpl(
            String name,
            String number,
            String started,
            String ciUrl,
            String releaseStatus,
            String buildRepo) {
        this(0L, name, number, started, ciUrl, releaseStatus, EMPTY, buildRepo);
    }

    public BuildRunImpl(
            long buildId,
            String name,
            String number,
            String started,
            String ciUrl,
            String releaseStatus,
            String releaseRepoKey,
            String buildRepo) {
        this.buildId = buildId;
        this.name = name;
        this.number = number;
        this.started = started;
        this.ciUrl = ciUrl;
        this.releaseStatus = releaseStatus;
        this.releaseRepoKey = releaseRepoKey;
        this.buildRepo = buildRepo;
    }

    @Override
    public Date getStartedDate() {
        return new Date(parseBuildTime(getStarted()));
    }
}