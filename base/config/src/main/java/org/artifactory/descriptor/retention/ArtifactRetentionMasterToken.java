package org.artifactory.descriptor.retention;

import lombok.AllArgsConstructor;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.authentication.AuthenticationToken;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlType;

/**
 * @author volodymyrm
 */
@AllArgsConstructor
@XmlType(name = "RetentionMasterTokenType")
@GenerateDiffFunction
public class ArtifactRetentionMasterToken extends AuthenticationToken implements Descriptor {

}