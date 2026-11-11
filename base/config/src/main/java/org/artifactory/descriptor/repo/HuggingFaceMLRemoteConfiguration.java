package org.artifactory.descriptor.repo;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * @author Volodymyr Shulga
 */
@Getter
@Setter
@XmlType(name = "HuggingFaceConfigurationType", namespace = Descriptor.NS)
@GenerateDiffFunction
public class HuggingFaceMLRemoteConfiguration implements Descriptor {

    @XmlElement(defaultValue = "false")
    private boolean mlRepoLayout;
}
