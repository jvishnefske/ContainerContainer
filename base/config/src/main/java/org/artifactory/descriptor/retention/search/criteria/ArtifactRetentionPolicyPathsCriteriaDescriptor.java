package org.artifactory.descriptor.retention.search.criteria;

import lombok.Data;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * @author Tamar Rosen
 */
@XmlType(name = "RetentionPolicyPathsCriteriaType", propOrder = {"include", "exclude"})
@GenerateDiffFunction
@Data
public class ArtifactRetentionPolicyPathsCriteriaDescriptor
        implements Descriptor, ArtifactRetentionIncludeExcludeCriteria {

    @XmlElement
    ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor include;

    @XmlElement
    ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor exclude;
}
