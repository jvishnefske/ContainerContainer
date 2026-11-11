package org.artifactory.repo.packagetype.configuration;

import lombok.Data;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;
import org.jfrog.common.config.validation.NotUnicode;

import java.util.List;

/**
 * @author Uriah Levy
 */
@Data
@MetadataVersion(metadataVersion = 1)
public class DebianConfiguration {
    @MetadataInfo(defaultValue = "0")
    private long modelVersion;
    @MetadataInfo(defaultValue = "false")
    private boolean debianTrivialLayout = false;
    @MetadataInfo(defaultValue = "false")
    private boolean ddebSupported;
    @MetadataInfo(skip = true)
    private String debianDefaultArchitectures = "i386,amd64";
    @MetadataInfo(defaultValue = "bz2")
    private List<@NotUnicode String> optionalIndexCompressionFormats;
    @MetadataInfo(skip = true)
    private List<@NotUnicode String> debianOptionalIndexCompressionFormats;
}
