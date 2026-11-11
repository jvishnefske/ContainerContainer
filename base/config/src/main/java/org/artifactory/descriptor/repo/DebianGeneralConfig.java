package org.artifactory.descriptor.repo;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * @author nadavy
 */
@XmlType(name = "debianType",
        propOrder = {"blockUnsafeChecksums"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebianGeneralConfig implements Descriptor {

    @XmlElement(name = "blockUnsafeChecksums", required = false)
    private boolean blockUnsafeChecksums = false;
}
