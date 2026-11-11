package org.artifactory.descriptor.retention.search.criteria;

import lombok.Data;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * @author Tamar Rosen
 */
@XmlType(name = "RetentionPolicyPropertyCriteriaType", propOrder = {"propertyKey", "propertyValue"})
@GenerateDiffFunction
@Data
public class ArtifactRetentionPolicyPropertyCriteriaDescriptor implements Descriptor {
    @XmlElement
    @NotUnicode
    String propertyKey;

    @XmlElement
    @NotUnicode
    String propertyValue;
}
