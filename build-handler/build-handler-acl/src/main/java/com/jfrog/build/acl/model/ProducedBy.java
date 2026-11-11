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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.joda.time.format.DateTimeFormat;

import static java.util.Objects.nonNull;

/**
 * @author Chen Keinan
 */
@Getter
@Setter
@NoArgsConstructor
public class ProducedBy {

    private static final String DATE_TIME_FORMAT = "dd-MM-yy HH:mm:ss z";

    private String name;
    private String number;
    private long started;
    private String startedString;
    private String ciUrl;
    private String releaseStatus;
    private String moduleID;
    private String buildRepo;

    public ProducedBy(
            BuildRun artifactBuild,
            String module) {
        if (nonNull(artifactBuild)) {
            this.name = artifactBuild.getName();
            this.number = artifactBuild.getNumber();
            this.started = artifactBuild.getStartedDate().getTime();
            this.startedString = formatStarted();
            this.ciUrl = artifactBuild.getCiUrl();
            this.releaseStatus = artifactBuild.getReleaseStatus();
            this.moduleID = module;
            this.buildRepo = artifactBuild.getBuildRepo();
        }
    }

    public ProducedBy(
            String name,
            String number,
            long started,
            String ciUrl,
            String releaseStatus,
            String moduleID,
            String buildRepo) {
        this.name = name;
        this.number = number;
        this.started = started;
        this.startedString = formatStarted();
        this.ciUrl = ciUrl;
        this.releaseStatus = releaseStatus;
        this.moduleID = moduleID;
        this.buildRepo = buildRepo;
    }

    @NonNull
    private String formatStarted() {
        return DateTimeFormat.forPattern(DATE_TIME_FORMAT).print(started);
    }
}
