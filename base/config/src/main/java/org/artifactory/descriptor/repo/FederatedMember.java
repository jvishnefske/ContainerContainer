package org.artifactory.descriptor.repo;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.DiffKey;

@XmlType(name = "FederatedMembersType", propOrder = {"url", "mode", "enabled"},
        namespace = Descriptor.NS)
@Data
public class FederatedMember implements Descriptor {

    @XmlID
    @XmlElement(required = true)
    @Getter(value = AccessLevel.NONE)
    private String url;
    private String mode;

    @XmlElement(defaultValue = "true", required = true)
    @DiffKey
    private boolean enabled = true;

    /**
     * Returns the federated member URL that may or may not contain a baseURL placeholder (template), depending on
     * the type of federated member (remote, local)
     * The placeholder URL that is returned from this method will be resolved dynamically, rather than containing a
     * strict base URL string.
     *
     * @return the full federated member URL for remote members, and a URL template for the local member.
     */
    public String getUrlOrTemplate() {
        return url;
    }

    public void setUrlOrTemplate(String url) {
        this.url = url;
    }
}
