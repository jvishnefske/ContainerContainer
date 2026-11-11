package org.artifactory.addon.cargo;


import lombok.Getter;

import java.util.List;

@Getter
public class CargoMetadataInfo {
    private final CargoInfo info;
    private final List<CargoDependencyMetadataInfo> dependencies;

    public CargoMetadataInfo(CargoInfo info, List<CargoDependencyMetadataInfo> dependencies) {
        this.info = info;
        this.dependencies = dependencies;
    }
}
