package org.artifactory.descriptor.repo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * Configuration class for Docker, OCI, and Helm OCI repository settings.
 *
 * @author Nir Paz
 */
@XmlType(name = "dockerType",
        propOrder = {"completeListManifestOverwrite", "enforceStrictTagOverwrite", "sharedDigestStatsSyncEnabled",
                "explicitConfig"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
public class DockerGeneralConfig implements Descriptor {

    @XmlElement(name = "completeListManifestOverwrite", required = false)
    @Builder.Default
    private boolean completeListManifestOverwrite = false;

    @XmlElement(name = "enforceStrictTagOverwrite", required = false)
    @Builder.Default
    private boolean enforceStrictTagOverwrite = false;

    @XmlElement(name = "sharedDigestStatsSyncEnabled", required = false)
    @Builder.Default
    private boolean sharedDigestStatsSyncEnabled = false;

    @XmlElement(name = "explicitConfig", required = false)
    @Builder.Default
    @EqualsAndHashCode.Exclude
    private boolean explicitConfig = false;

    /**
     * Returns whether this configuration was explicitly set in persisted config (UI or API).
     * Hidden from JSON serialization to keep API responses clean.
     */
    @JsonIgnore
    public boolean isExplicitConfig() {
        return explicitConfig;
    }
}
