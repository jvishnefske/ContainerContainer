package org.artifactory.repo.converter;

import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

/**
 * @author Tamar Rosen
 */
public enum PackageConfigConverterEnum {
    ;
    private long version;
    private final PackagesConverter[] packagesConverters;

    PackageConfigConverterEnum(long version, PackagesConverter... packagesConverters) {
        this.version = version;
        this.packagesConverters = packagesConverters;
    }

    public static List<PackagesConverter> getRelevantConverters(long fromVersion) {
        List<PackagesConverter> converters = Lists.newArrayList();
        for (PackageConfigConverterEnum converterVersion : PackageConfigConverterEnum.values()) {
            if (converterVersion.version > fromVersion) {
                converters.addAll(Arrays.asList(converterVersion.getPackagesConverters()));
            }
        }
        return converters;
    }

    public PackagesConverter[] getPackagesConverters() {
        return packagesConverters;
    }
}
