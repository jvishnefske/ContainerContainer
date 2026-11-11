package org.artifactory.descriptor.repo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * @author shahafg
 */
@XmlType(name = "pypiType",
        propOrder = {"simpleJsonFormatEnabled", "simpleJsonFormatEnabledExplicit"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PypiGeneralConfig implements Descriptor {

    @XmlElement(name = "simpleJsonFormatEnabled", required = false)
    private boolean simpleJsonFormatEnabled = false;

    @XmlElement(name = "simpleJsonFormatEnabledExplicit", required = false)
    private boolean simpleJsonFormatEnabledExplicit = false;

    public boolean isSimpleJsonFormatEnabled() {
        return simpleJsonFormatEnabled;
    }

    /**
     * Returns whether this configuration was explicitly set in persisted config (UI or API).
     * Hidden from JSON serialization to keep API responses clean.
     */
    @JsonIgnore
    public boolean isSimpleJsonFormatEnabledExplicit() {
        return simpleJsonFormatEnabledExplicit;
    }
}
