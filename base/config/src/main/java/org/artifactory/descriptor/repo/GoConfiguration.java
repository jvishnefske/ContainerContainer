package org.artifactory.descriptor.repo;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;


/**
 * @author Michael Akushsky
 */
@Getter
@Setter
@XmlType(name = "GoConfigurationType", namespace = Descriptor.NS)
@GenerateDiffFunction
public class GoConfiguration {
    @XmlElement(name = "gitLabResolveSubgroups")
    private boolean gitLabResolveSubgroups;
}