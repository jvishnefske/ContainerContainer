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

import com.google.common.collect.ImmutableList;
import org.artifactory.security.BuildAcl;
import org.artifactory.security.GroupInfo;
import org.artifactory.security.ReleaseBundleAcl;
import org.artifactory.security.RepoAcl;
import org.artifactory.security.SecurityInfo;
import org.artifactory.security.UserInfo;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: freds Date: Jun 12, 2008 Time: 1:57:29 PM
 */
@XmlRootElement(name = "security")
@XmlAccessorType(XmlAccessType.FIELD)
public class SecurityDataImpl implements SecurityInfo {
    @XmlAttribute
    private String version;
    @XmlElementWrapper(name = "users")
    @XmlElement(name = "user")
    private List<UserImpl> users;
    @XmlElementWrapper(name = "groups")
    @XmlElement(name = "group")
    private List<GroupImpl> groups;
    @XmlElementWrapper(name = "acls")
    @XmlElement(name = "acl")
    private List<MutableRepoAclImpl> acls;
    @XmlElementWrapper(name = "buildAcls")
    @XmlElement(name = "buildAcl")
    private List<MutableBuildAclImpl> buildAcls;
    @XmlElementWrapper(name = "releaseBundleAcls")
    @XmlElement(name = "releaseBundleAcl")
    private List<MutableReleaseBundleAclImpl> releaseBundleAcls;

    public SecurityDataImpl() {
        // for JAXB
    }

    public SecurityDataImpl(List<UserInfo> users, List<GroupInfo> groups, List<RepoAcl> repoAcls,
            List<BuildAcl> buildAcls, List<ReleaseBundleAcl> releaseBundleAcls) {
        if (users != null) {
            this.users = new ArrayList<>(users.size());
            for (UserInfo user : users) {
                this.users.add(new UserImpl(user));
            }
        }
        if (groups != null) {
            this.groups = new ArrayList<>(groups.size());
            for (GroupInfo group : groups) {
                this.groups.add(new GroupImpl(group));
            }
        }
        if (repoAcls != null) {
            this.acls = new ArrayList<>(repoAcls.size());
            for (RepoAcl acl : repoAcls) {
                this.acls.add(new MutableRepoAclImpl(acl));
            }
        }
        if (buildAcls != null) {
            this.buildAcls = new ArrayList<>(buildAcls.size());
            for (BuildAcl acl : buildAcls) {
                this.buildAcls.add(new MutableBuildAclImpl(acl));
            }
        }
        if (releaseBundleAcls != null) {
            this.releaseBundleAcls = new ArrayList<>(releaseBundleAcls.size());
            for (ReleaseBundleAcl acl : releaseBundleAcls) {
                this.releaseBundleAcls.add(new MutableReleaseBundleAclImpl(acl));
            }
        }
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public List<UserInfo> getUsers() {
        if (users == null) {
            return null;
        }
        return ImmutableList.<UserInfo>copyOf(users);
    }

    @Override
    public List<GroupInfo> getGroups() {
        if (groups == null) {
            return null;
        }
        return ImmutableList.<GroupInfo>copyOf(groups);
    }

    @Override
    public List<RepoAcl> getRepoAcls() {
        if (acls == null) {
            return null;
        }
        return ImmutableList.<RepoAcl>copyOf(acls);
    }

    @Override
    public List<BuildAcl> getBuildAcls() {
        if (buildAcls == null) {
            return Collections.emptyList();
        }
        return ImmutableList.copyOf(buildAcls);
    }

    @Override
    public List<ReleaseBundleAcl> getReleaseBundleAcls() {
        if (releaseBundleAcls == null) {
            return Collections.emptyList();
        }
        return ImmutableList.copyOf(releaseBundleAcls);
    }

}