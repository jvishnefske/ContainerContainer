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

package org.artifactory.descriptor.index;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.TaskDescriptor;
import org.jfrog.common.config.diff.DiffAtomic;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.annotation.Nullable;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;
import java.util.SortedSet;

/**
 * @author Fred Simon
 */
@XmlType(name = "IndexerType", propOrder = {"enabled", "cronExp", "includedRepositories"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
public class IndexerDescriptor implements TaskDescriptor {

    private static final long serialVersionUID = 1L;

    private boolean enabled;

    @NotUnicode
    private String cronExp;

    @XmlElementWrapper(name = "includedRepositories")
    @XmlElement(name = "repositoryRef", required = false)
    @DiffAtomic
    private SortedSet<@NotUnicode String> includedRepositories;

    public IndexerDescriptor() {
        // By Default index once a day at 05:23AM
        this.cronExp = "0 23 5 * * ?";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCronExp() {
        return cronExp;
    }

    public void setCronExp(String cronExp) {
        this.cronExp = cronExp;
    }

    @Nullable
    public SortedSet<String> getIncludedRepositories() {
        return includedRepositories;
    }

    public void setIncludedRepositories(SortedSet<String> includedRepositories) {
        this.includedRepositories = includedRepositories;
    }

    public void removeIncludedRepository(String repoKey) {
        if (includedRepositories != null) {
            includedRepositories.remove(repoKey);
        }
    }

    @Override
    public boolean sameTaskDefinition(TaskDescriptor otherDescriptor) {
        if (otherDescriptor == null || !(otherDescriptor instanceof IndexerDescriptor)) {
            throw new IllegalArgumentException(
                    "Cannot compare indexer descriptor " + this + " with " + otherDescriptor);
        }
        IndexerDescriptor indexerDesc = (IndexerDescriptor) otherDescriptor;
        return indexerDesc.enabled == this.enabled &&
                StringUtils.equals(indexerDesc.cronExp, this.cronExp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        IndexerDescriptor that = (IndexerDescriptor) o;

        if (enabled != that.enabled) {
            return false;
        }
        if (cronExp != null ? !cronExp.equals(that.cronExp) : that.cronExp != null) {
            return false;
        }
        return includedRepositories != null ? includedRepositories.equals(that.includedRepositories) :
                that.includedRepositories == null;
    }

    @Override
    public int hashCode() {
        int result = (enabled ? 1 : 0);
        result = 31 * result + (cronExp != null ? cronExp.hashCode() : 0);
        result = 31 * result + (includedRepositories != null ? includedRepositories.hashCode() : 0);
        return result;
    }
}