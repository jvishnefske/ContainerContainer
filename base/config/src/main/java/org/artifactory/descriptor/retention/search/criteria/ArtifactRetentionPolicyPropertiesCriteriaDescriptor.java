package org.artifactory.descriptor.retention.search.criteria;

import lombok.Data;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * @author Tamar Rosen
 */
@XmlType(name = "RetentionPolicyPropertiesCriteriaType", propOrder = {"include", "exclude"})
@GenerateDiffFunction
@Data
public class ArtifactRetentionPolicyPropertiesCriteriaDescriptor implements Descriptor {
    @XmlElement
    ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor include;

    @XmlElement
    ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor exclude;
}
