/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2016 JFrog Ltd.
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

import org.artifactory.security.AceInfo;
import org.artifactory.security.Acl;
import org.artifactory.security.BuildPermissionTarget;
import org.artifactory.security.MutableBuildAcl;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.Set;

/**
 * @author Yuval Reches
 */
@XmlRootElement(name = "buildAcl")
@XmlAccessorType(XmlAccessType.FIELD)
public class MutableBuildAclImpl extends MutableBaseRepoAclImpl<BuildPermissionTarget> implements MutableBuildAcl {
    protected BuildPermissionTargetImpl permissionTarget;

    public MutableBuildAclImpl() {
        super();
        this.permissionTarget = new BuildPermissionTargetImpl();
    }

    public MutableBuildAclImpl(Acl<BuildPermissionTarget> copy) {
        super(copy.getLastUpdated(), copy.getUpdatedBy(), copy.getAccessIdentifier(), copy.getAces());
        this.permissionTarget = new BuildPermissionTargetImpl(copy.getPermissionTarget());
    }

    public MutableBuildAclImpl(BuildPermissionTarget permissionTarget, Set<AceInfo> aces, String updatedBy,
            long lastUpdated) {
        super(aces, updatedBy, lastUpdated);
        this.permissionTarget = new BuildPermissionTargetImpl(permissionTarget);
    }

    public MutableBuildAclImpl(BuildPermissionTarget permissionTarget, Set<AceInfo> aces, String updatedBy,
            long lastUpdated, String accessIdentifier) {
        super(lastUpdated, updatedBy, accessIdentifier, aces);
        this.permissionTarget = new BuildPermissionTargetImpl(permissionTarget);
    }

    @Override
    public BuildPermissionTargetImpl getPermissionTarget() {
        return permissionTarget;
    }

    @Override
    public void setPermissionTarget(BuildPermissionTarget permissionTarget) {
        this.permissionTarget = new BuildPermissionTargetImpl(permissionTarget);
    }
}