package org.artifactory.descriptor.retention;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.TaskDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicySearchCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.PackageSearchCriteriaDescriptor;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.Objects;

/**
 * @author Tamar Rosen
 */
@XmlType(name = "RetentionPolicyType", propOrder = {"key", "enabled", "description", "aqlQuery", "cronExp", "type",
        "durationInMinutes", "expirationTimeInMonths", "skipTrashcan", "packageRetention", "searchCriteriaForm",
        "packageSearchCriteriaForm"})
@GenerateDiffFunction
@XmlRootElement
@Data
public class ArtifactRetentionPolicyDescriptor implements TaskDescriptor {

    @XmlElement(required = true)
    @DiffKey
    @NotUnicode
    private String key;

    @XmlElement(required = true)
    private boolean enabled;

    @XmlElement
    @NotUnicode
    private String description;

    @XmlElement
    @NotUnicode
    private String aqlQuery;

    @XmlElement
    @NotUnicode
    private String cronExp;

    @XmlElement(required = true)
    @NotUnicode
    private String type = ArtifactRetentionOperationType.ARCHIVE.name();

    @XmlElement(defaultValue = "0")
    private long durationInMinutes;

    @XmlElement(defaultValue = "0")
    private long expirationTimeInMonths;

    @XmlElement(defaultValue = "false")
    private boolean skipTrashcan;

    @XmlElement(defaultValue = "false")
    private boolean packageRetention;

    @XmlElement
    private ArtifactRetentionPolicySearchCriteriaDescriptor searchCriteriaForm;

    @XmlElement
    private PackageSearchCriteriaDescriptor packageSearchCriteriaForm;

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        ArtifactRetentionPolicyDescriptor that = (ArtifactRetentionPolicyDescriptor) o;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    @Override
    public boolean sameTaskDefinition(TaskDescriptor otherDescriptor) {
        if (!(otherDescriptor instanceof ArtifactRetentionPolicyDescriptor)) {
            throw new IllegalArgumentException("Cannot compare retention descriptor " +
                    this + " with " + otherDescriptor);
        }
        ArtifactRetentionPolicyDescriptor descriptor = (ArtifactRetentionPolicyDescriptor) otherDescriptor;
        return StringUtils.equals(descriptor.key, this.key) &&
                StringUtils.equals(descriptor.cronExp, this.cronExp) &&
                descriptor.enabled == this.enabled;
    }

    public void normalizeInputs() {
        if (packageRetention) {
            String packageType = getPackageSearchCriteriaForm().getPackageType();
            getPackageSearchCriteriaForm().setPackageType(packageType.toLowerCase());
        }
    }
}
