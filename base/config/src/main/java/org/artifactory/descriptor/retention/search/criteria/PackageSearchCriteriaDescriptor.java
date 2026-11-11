package org.artifactory.descriptor.retention.search.criteria;

import lombok.Data;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * @author Oleksandr Legenkiy
 */
@XmlType(name = "RetentionPolicyPackageSearchCriteriaType", propOrder = {"packageType", "componentName", "version",
        "packageRepos", "createdBefore", "downloadedBefore"
})
@GenerateDiffFunction
@Data
public class PackageSearchCriteriaDescriptor implements Descriptor {
    @XmlElement
    private String packageType;
    @XmlElement
    private String componentName;
    @XmlElement
    private String version;
    @XmlElementWrapper(name = "packageRepos")
    @XmlElement(name = "repo")
    private List<String> packageRepos;
    @XmlElement
    private Long createdBefore;
    @XmlElement
    private Long downloadedBefore;
}
