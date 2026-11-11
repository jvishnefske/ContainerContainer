package org.artifactory.descriptor.retention.search.criteria;

import lombok.Data;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;
import java.util.List;

@XmlType(name = "RetentionPolicyPathsInclusionCriteriaType", propOrder = {"operator", "values"})
@GenerateDiffFunction
@Data
public class ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor implements Descriptor {
    @XmlElement
    @NotUnicode
    private String operator = "or";

    @XmlElementWrapper(name = "values")
    @XmlElement(name = "value")
    private List<String> values;
}
