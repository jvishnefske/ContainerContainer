package org.artifactory.addon;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class VersionResult {
    String version;
    String revision;
    Map<String, String> servicesVersions;
    List<String> addons;
    String license;
    Map<String, Boolean> entitlements;

    public VersionResult(String version, String revision, Map<String, String> servicesVersions, List<String> addons,
            String license, Map<String, Boolean> entitlements) {
        this.version = version;
        this.revision = revision;
        this.servicesVersions = servicesVersions;
        this.addons = addons;
        this.license = license;
        this.entitlements = entitlements;
    }

    public VersionResult(String version, String revision, List<String> addons, String license,
            Map<String, Boolean> entitlements) {
        this(version, revision, Map.of(), addons, license, entitlements);
    }


}
