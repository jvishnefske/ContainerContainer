package org.artifactory.rest.services.cleanup.mapper;

import lombok.experimental.UtilityClass;
import org.artifactory.policy.cleanup.packages.FilterCleanupPackages;
import org.artifactory.policy.cleanup.packages.FilterPackage;
import org.artifactory.rest.resource.cleanup.dto.PackagesCleanupFilterDto;
import org.artifactory.rest.resource.cleanup.dto.PackagesDto;
import org.springframework.lang.NonNull;

@UtilityClass
public class FilterCleanupPackagesDtoMapper {

    public FilterCleanupPackages fromDto(@NonNull PackagesCleanupFilterDto dto) {

        return FilterCleanupPackages.builder()
                .property(dto.property())
                .propertyThreshold(dto.propertyThreshold())
                .packages(dto.packages().stream().map(FilterCleanupPackagesDtoMapper::fromFilterPackageDto).toList())
                .build();
    }

    public FilterPackage fromFilterPackageDto(@NonNull PackagesDto dto) {

        return FilterPackage.builder()
                .path(dto.path())
                .build();
    }
}