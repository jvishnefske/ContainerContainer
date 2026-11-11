package org.artifactory.descriptor.authentication;

import lombok.Data;
import org.apache.commons.compress.utils.Lists;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.DiffAtomic;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * @author bilali
 */
@Data
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@GenerateDiffFunction
@XmlType(name = "AuthenticationType", propOrder = {"tokens"}, namespace = Descriptor.NS)
public class AuthenticationDescriptor implements Descriptor {

    @XmlElementWrapper(name = "tokens")
    @XmlElement(name = "token")
    @DiffAtomic
    private List<AuthenticationToken> tokens = Lists.newArrayList();
}
