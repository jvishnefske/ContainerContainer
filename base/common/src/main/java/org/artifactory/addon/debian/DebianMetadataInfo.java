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

package org.artifactory.addon.debian;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Yuval Reches
 */
@Setter
@Getter
@NoArgsConstructor
public class DebianMetadataInfo {

    private DebianInfo debianInfo;
    private List<String> debianDependencies;
    private String failReason;
    @JsonUnwrapped
    private DscInfo dscInfo;

    DebianMetadataInfo(DscInfo dscInfo) {
        this.dscInfo = dscInfo;
    }

    DebianMetadataInfo(DebianInfo debianInfo, List<String> debianDependencies, String failReason) {
        this.debianInfo = debianInfo;
        this.debianDependencies = debianDependencies;
        this.failReason = failReason;
    }
}
