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

package org.artifactory.model.security;

import org.artifactory.security.BuildPermissionTarget;
import org.artifactory.security.MutableBuildPermissionTarget;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * @author Yuval Reches
 */
@XmlRootElement(name = "target")
@XmlAccessorType(XmlAccessType.FIELD)
public class BuildPermissionTargetImpl extends RepoPermissionTargetImpl implements MutableBuildPermissionTarget {

    public BuildPermissionTargetImpl() {
        this("");
    }

    public BuildPermissionTargetImpl(String name) {
        super(name, new ArrayList<>(), new ArrayList<>());
    }

    BuildPermissionTargetImpl(BuildPermissionTarget copy) {
        super(copy.getName(),
                new LinkedHashSet<>(copy.getRepoKeys()),
                new ArrayList<>(copy.getIncludes()),
                new ArrayList<>(copy.getExcludes())
        );
    }

    @Override
    public String toString() {
        return "BuildPermissionTargetImpl{" +
                "name='" + name + '\'' +
                '}';
    }
}