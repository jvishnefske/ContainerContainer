package org.artifactory.descriptor.repo;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * This class is used to configure settings specific to NuGet repositories.
 *
 * @author bhavyab
 */
@XmlType(name = "nugetType",
        propOrder = {"enableGlobalNormalization"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NugetGeneralConfig implements Descriptor {

    //This flag will be used to toggle the global normalization of NuGet in the UI screen
    // Artifactory Settings -> Packages Settings
    @XmlElement(name = "enableGlobalNormalization")
    private boolean enableGlobalNormalization = false;
}
