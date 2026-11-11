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
import lombok.EqualsAndHashCode;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.jfrog.common.config.diff.DiffReference;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.validation.Valid;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlType;

/**
 * @author Noam Y. Tenne
 */
@XmlType(
        name = "LocalReplicationType",
        propOrder = {"url", "proxy", "disableProxy", "socketTimeoutMillis", "username", "password", "syncStatistics"},
        namespace = Descriptor.NS
)
@Data
@EqualsAndHashCode(callSuper = true)
@GenerateDiffFunction
public class LocalReplicationDescriptor extends ReplicationBaseDescriptor {

    @XmlElement
    @NotUnicode
    private String url;

    @XmlIDREF
    @XmlElement(name = "proxyRef")
    @DiffReference
    @Valid
    private ProxyDescriptor proxy;

    @XmlElement(defaultValue = "false")
    private boolean disableProxy;

    @XmlElement(defaultValue = "15000")
    private int socketTimeoutMillis = 15000;//Default socket timeout

    @XmlElement
    @NotUnicode
    private String username;

    @XmlElement
    @NotUnicode
    private String password;

    @XmlElement(defaultValue = "false")
    private boolean syncStatistics;

    @Override
    public String toString() {
        return "LocalReplication[" + getRepoKey() + "|" + url + "|" + getReplicationKey() + "]";
    }
}
