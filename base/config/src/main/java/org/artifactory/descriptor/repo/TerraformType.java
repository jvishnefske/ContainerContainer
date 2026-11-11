package org.artifactory.descriptor.repo;

import org.artifactory.common.config.metadata.MetadataEnum;

/**
 * @author volodymyrm
 */
public enum TerraformType implements MetadataEnum {
    MODULE,
    PROVIDER;

    public String getName() {
        return this.name().toUpperCase();
    }

    public static TerraformType valueOfIgnoreCase(String name) {
        return TerraformType.valueOf(name.toUpperCase());
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public String getConfigName() {
        return getName();
    }
}