package org.artifactory.descriptor.repo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * Global Hugging Face ML package configuration.
 *
 * <p>{@code xetEnabled}/{@code xetEnabledExplicit} are the unified flags and control XET for both local and remote
 * repositories. The legacy {@code xetRemoteEnabled}/{@code xetRemoteEnabledExplicit} elements that previously
 * controlled remote-only XET are migrated to these fields by {@code HuggingFaceXetEnabledConverter} at config upgrade
 * time. The two-argument constructor populates these fields and marks the configuration as explicitly set.
 *
 * @author shahafg
 */
@XmlType(name = "huggingFaceMLType",
        propOrder = {"xetEnabled", "xetEnabledExplicit"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@Data
@NoArgsConstructor
public class HuggingFaceMlGeneralConfig implements Descriptor {

    @XmlElement(name = "xetEnabled", required = false)
    private boolean xetEnabled = false;

    @XmlElement(name = "xetEnabledExplicit", required = false)
    private boolean xetEnabledExplicit = false;

    public HuggingFaceMlGeneralConfig(boolean xetEnabled, boolean xetEnabledExplicit) {
        this.xetEnabled = xetEnabled;
        this.xetEnabledExplicit = xetEnabledExplicit;
    }

    /**
     * Returns whether this configuration was explicitly set in persisted config (UI or API).
     * Hidden from JSON serialization to keep API responses clean.
     */
    @JsonIgnore
    public boolean isXetEnabledExplicit() {
        return xetEnabledExplicit;
    }
}
