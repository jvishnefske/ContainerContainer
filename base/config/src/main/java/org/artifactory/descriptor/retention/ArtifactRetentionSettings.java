package org.artifactory.descriptor.retention;

import lombok.Data;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Uriah Levy
 */
@XmlType(name = "RetentionSettingsType", propOrder = {
        "jfrogColdStorageArchivingNamespace", "jfrogColdStorageMasterToken", "retentionPolicies"})
@GenerateDiffFunction
@Data
public class ArtifactRetentionSettings implements Descriptor {

    @XmlElement
    @DiffKey
    @NotUnicode
    private String jfrogColdStorageArchivingNamespace;

    @XmlElement(name = "jfrogColdStorageMasterToken")
    private ArtifactRetentionMasterToken jfrogColdStorageMasterToken;

    @XmlElementWrapper(name = "retentionPolicies")
    @XmlElement(name = "retentionPolicy")
    private List<ArtifactRetentionPolicyDescriptor> retentionPolicies = new ArrayList<>();

    public List<ArtifactRetentionPolicyDescriptor> getRetentionPolicies() {
        return retentionPolicies;
    }
}
