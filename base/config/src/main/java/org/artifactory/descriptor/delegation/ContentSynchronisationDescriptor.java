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

package org.artifactory.descriptor.delegation;

import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Delegation definitions container
 *
 * @author Michael Pasternak
 */
@XmlType(name = "ContentSynchronisation",
        propOrder = {"enabled", "statistics", "properties", "source"},
        namespace = Descriptor.NS
)
@GenerateDiffFunction
public class ContentSynchronisationDescriptor implements Descriptor {

    @XmlElement(name = "enabled", required = true, namespace = Descriptor.NS)
    private boolean enabled = false;
    @XmlElement(name = "statistics", required = true, namespace = Descriptor.NS)
    private StatisticsContentDescriptor statistics;
    @XmlElement(name = "properties", required = true, namespace = Descriptor.NS)
    private PropertiesContentDescriptor properties;
    @XmlElement(name = "source", required = true, namespace = Descriptor.NS)
    private SourceContentDescriptor source;

    public ContentSynchronisationDescriptor() {
        this.statistics = new StatisticsContentDescriptor();
        this.properties = new PropertiesContentDescriptor();
        this.source = new SourceContentDescriptor();
    }

    /**
     * Enables delegation when SmartRepo discovered
     */
    public void enable() {
        enabled = true;
    }

    /**
     * Disables delegation
     */
    public void disable() {
        enabled = false;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Checks whether delegation is enabled on container level
     *
     * @return boolean
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns delegate representing statistics
     *
     * @return {@link StatisticsContentDescriptor}
     */
    public StatisticsContentDescriptor getStatistics() {
        return statistics;
    }

    /**
     * Returns delegate representing properties
     *
     * @return {@link PropertiesContentDescriptor}
     */
    public PropertiesContentDescriptor getProperties() {
        return properties;
    }

    /**
     * Returns delegate representing source remote content
     *
     * @return {@link SourceContentDescriptor}
     */
    public SourceContentDescriptor getSource() {
        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ContentSynchronisationDescriptor that = (ContentSynchronisationDescriptor) o;

        if (enabled != that.enabled) {
            return false;
        }
        if (properties != null ? !properties.equals(that.properties) : that.properties != null) {
            return false;
        }
        if (statistics != null ? !statistics.equals(that.statistics) : that.statistics != null) {
            return false;
        }
        return source != null ? source.equals(that.source) : that.source == null;
    }

    @Override
    public int hashCode() {
        int result = (enabled ? 1 : 0);
        result = 13 * result + (statistics != null ? statistics.hashCode() : 0);
        result = 17 * result + (properties != null ? properties.hashCode() : 0);
        result = 31 * result + (source != null ? source.hashCode() : 0);
        return result;
    }
}
