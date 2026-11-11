package org.artifactory.descriptor.retention.search.criteria;

import lombok.Data;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * @author Tamar Rosen
 */
@XmlType(name = "RetentionPolicySearchCriteriaType", propOrder = {
        "properties", "repositories", "paths", "names", "createdBefore", "downloadedBefore"})
@GenerateDiffFunction
@Data
public class ArtifactRetentionPolicySearchCriteriaDescriptor implements Descriptor {
    @XmlElement
    private ArtifactRetentionPolicyPropertiesCriteriaDescriptor properties;
    @XmlElement
    private ArtifactRetentionPolicyPathsCriteriaDescriptor repositories;
    @XmlElement
    private ArtifactRetentionPolicyPathsCriteriaDescriptor paths;
    @XmlElement
    private ArtifactRetentionPolicyPathsCriteriaDescriptor names;
    @XmlElement
    private Long createdBefore;
    @XmlElement
    private Long downloadedBefore;
}
