package org.artifactory.descriptor.repo;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

@XmlType(name = "helmType",
        propOrder = {"forceOciFirstUiFlow", "forceMetadataNameVersion", "forceNonDuplicateChart"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HelmGeneralConfig implements Descriptor {

    @XmlElement(name = "forceOciFirstUiFlow", required = false)
    private boolean forceOciFirstUiFlow = true;

    @XmlElement(name = "forceMetadataNameVersion")
    private boolean forceMetadataNameVersion = false;

    @XmlElement(name = "forceNonDuplicateChart")
    private boolean forceNonDuplicateChart = false;
}
