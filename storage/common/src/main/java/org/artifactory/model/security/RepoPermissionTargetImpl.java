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

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.NonNull;
import org.artifactory.security.MutableRepoPermissionTarget;
import org.artifactory.security.RepoPermissionTarget;
import org.jfrog.client.util.PathUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;

/**
 *
 */

//XStream kept here to backwards support security.xml import
@XmlRootElement(name = "target")
@XmlAccessorType(XmlAccessType.FIELD)
public class RepoPermissionTargetImpl implements MutableRepoPermissionTarget {

    protected String name;
    @XmlElementWrapper(name = "includes")
    @XmlElement(name = "string")
    @JsonProperty("includes")
    protected List<String> includes = new ArrayList<>();
    @XmlElementWrapper(name = "excludes")
    @XmlElement(name = "string")
    @JsonProperty("excludes")
    protected List<String> excludes = new ArrayList<>();
    @XmlElementWrapper(name = "repoKeys")
    @XmlElement(name = "string")
    @JsonProperty("repoKeys")
    private Set<String> repoKeys = new LinkedHashSet<>();

    public RepoPermissionTargetImpl() {
        this("", new LinkedHashSet<>(singletonList(ANY_REPO)));
    }

    public RepoPermissionTargetImpl(String name) {
        this(name, new LinkedHashSet<>(singletonList(ANY_REPO)));
    }

    public RepoPermissionTargetImpl(String name, Set<String> repoKeys) {
        this.name = name;
        this.repoKeys = new LinkedHashSet<>(repoKeys);
        this.includes.add(ANY_PATH);
    }

    public RepoPermissionTargetImpl(String name, Set<String> repoKeys, List<String> includes, List<String> excludes) {
        this.name = name;
        this.repoKeys = new LinkedHashSet<>(repoKeys);
        this.includes = includes;
        this.excludes = excludes;
    }

    public RepoPermissionTargetImpl(String name, List<String> includes, List<String> excludes) {
        this.name = name;
        this.includes = includes;
        this.excludes = excludes;
    }

    public RepoPermissionTargetImpl(RepoPermissionTarget copy) {
        this(copy.getName(),
                new LinkedHashSet<>(copy.getRepoKeys()),
                new ArrayList<>(copy.getIncludes()),
                new ArrayList<>(copy.getExcludes())
        );
    }

    public RepoPermissionTargetImpl(String name, Set<String> repoKeys, String includes, String excludes) {
        this(name, repoKeys);
        setIncludesPattern(includes);
        setExcludesPattern(excludes);
    }

    @Override
    public Set<String> getRepoKeys() {
        return repoKeys;
    }

    @Override
    public void setRepoKeys(@NonNull Set<String> repoKeys) {
        this.repoKeys = repoKeys.stream().filter(Objects::nonNull).sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    @Override
    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    @Override
    public void setIncludesPattern(String includesPattern) {
        this.includes = PathUtils.includesExcludesPatternToStringList(includesPattern);
    }

    @Override
    public void setExcludesPattern(String excludesPattern) {
        this.excludes = PathUtils.includesExcludesPatternToStringList(excludesPattern);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public List<String> getIncludes() {
        return this.includes;
    }

    @Override
    public List<String> getExcludes() {
        return this.excludes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RepoPermissionTargetImpl that = (RepoPermissionTargetImpl) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "RepoPermissionTargetImpl{" +
                "name='" + name + '\'' +
                '}';
    }
}