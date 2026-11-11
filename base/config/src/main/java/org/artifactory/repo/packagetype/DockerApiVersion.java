package org.artifactory.repo.packagetype;

import org.artifactory.common.config.metadata.MetadataEnum;

import jakarta.xml.bind.annotation.XmlEnumValue;

/**
 * @author Tamar Rosen
 */
public enum DockerApiVersion implements MetadataEnum {
    @XmlEnumValue("V1") V1("V1"),
    @XmlEnumValue("V2") V2("V2");

    private final String val;

    DockerApiVersion(String val) {
        this.val = val;
    }

    @Override
    public String toString() {
        return val;
    }

    public String getVal() {
        return val;
    }

    @Override
    public String getConfigName() {
        return val;
    }
}
