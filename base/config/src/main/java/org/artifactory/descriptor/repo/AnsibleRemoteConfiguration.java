package org.artifactory.descriptor.repo;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

@Getter
@Setter
@XmlType(name = "AnsibleConfigurationType", namespace = Descriptor.NS)
@GenerateDiffFunction
public class AnsibleRemoteConfiguration implements Descriptor {

    @XmlElement
    private String authUrl;

    /**
     * Kept for JAXB backward compatibility — existing XML configs may contain a {@code <token>} element.
     * No longer used; password field on the remote repo is used instead.
     */
    @Deprecated
    @XmlElement
    private String token;

    /**
     * Always returns null to prevent JAXB from re-serializing the legacy token value.
     */
    public String getToken() {
        return null;
    }
}
