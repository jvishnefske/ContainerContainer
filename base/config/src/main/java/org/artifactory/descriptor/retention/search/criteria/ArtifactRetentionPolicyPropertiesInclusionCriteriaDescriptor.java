package org.artifactory.descriptor.retention.search.criteria;

import lombok.Data;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.DiffAtomic;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * @author Tamar Rosen
 */
@XmlType(name = "RetentionPolicyPropertiesInclusionCriteriaType", propOrder = {"operator", "values"})
@GenerateDiffFunction
@Data
public class ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor implements Descriptor {
    @XmlElement
    @NotUnicode
    String operator = "or";

    @XmlElementWrapper(name = "values")
    @XmlElement(name = "property")
    @DiffAtomic
    private List<ArtifactRetentionPolicyPropertyCriteriaDescriptor> values;
}
