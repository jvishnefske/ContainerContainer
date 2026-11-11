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

package com.jfrog.build.model;

import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.acl.util.BuildInfoUtils;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.EMPTY;

@Getter
@Builder
@RequiredArgsConstructor
@EqualsAndHashCode(of = {"started", "name", "number"})
public class BuildIdImpl implements BuildId {

    private final long buildId;
    private final String name;
    private final String number;
    private final long started;
    private final String buildRepo;
    private final Set<BuildReleaseBundle> releaseBundles = new LinkedHashSet<>();

    /**
     * Returns the starting time of the build
     *
     * @return Build start time
     */
    @Override
    public String getStarted() {
        return started > 0L ? BuildInfoUtils.formatBuildTime(started) : EMPTY;
    }

    /**
     * Returns a date representation of the build starting time
     *
     * @return Build started date
     */
    @Override
    public Date getStartedDate() {
        return new Date(BuildInfoUtils.parseBuildTime(getStarted()));
    }
}
