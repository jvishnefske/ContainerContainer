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

package org.artifactory.descriptor.replication;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.TaskDescriptor;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import java.util.regex.Pattern;

/**
 * @author Noam Y. Tenne
 */
@XmlType(name = "ReplicationBaseType", propOrder = {"enabled", "cronExp", "syncDeletes", "syncProperties",
        "repoKey", "enableEventReplication", "replicationKey", "checkBinaryExistenceInFilestore",
        "includePathPrefixPattern", "excludePathPrefixPattern"}, namespace = Descriptor.NS)
@Data
public abstract class ReplicationBaseDescriptor implements TaskDescriptor {

    @XmlTransient
    private static Pattern p = Pattern.compile("([^*]*/).*[*?{]");

    @XmlElement(defaultValue = "false")
    private boolean enabled;

    @XmlElement(required = false)
    @NotUnicode
    private String cronExp;

    @XmlElement(defaultValue = "true")
    private boolean syncDeletes = false;

    @XmlElement(defaultValue = "true")
    private boolean syncProperties = true;

    @XmlElement(defaultValue = "", required = false)
    @NotUnicode
    private String includePathPrefixPattern;

    @XmlElement(defaultValue = "", required = false)
    @NotUnicode
    private String excludePathPrefixPattern;

    @XmlElement(required = true)
    @NotUnicode
    private String repoKey;

    @XmlElement(required = true)
    @DiffKey
    @NotUnicode
    private String replicationKey;

    @XmlElement(defaultValue = "true")
    private boolean enableEventReplication = false;

    @XmlElement(defaultValue = "false")
    private boolean checkBinaryExistenceInFilestore = false;

    public boolean isEventBasedReplicationEnabled() {
        return enabled && enableEventReplication;
    }

    @Override
    public boolean sameTaskDefinition(TaskDescriptor otherDescriptor) {
        if (!(otherDescriptor instanceof ReplicationBaseDescriptor)) {
            throw new IllegalArgumentException(
                    "Cannot compare replication descriptor " + this + " with " + otherDescriptor);
        }
        ReplicationBaseDescriptor replicationDescriptor = (ReplicationBaseDescriptor) otherDescriptor;
        return replicationDescriptor.enabled == this.enabled &&
                StringUtils.equals(replicationDescriptor.repoKey, this.repoKey) &&
                StringUtils.equals(replicationDescriptor.cronExp, this.cronExp);
    }
}
