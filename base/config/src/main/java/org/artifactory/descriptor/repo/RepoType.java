/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.descriptor.repo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import lombok.Getter;
import lombok.NonNull;
import org.artifactory.common.config.metadata.MetadataEnum;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author Shay Yaakov
 */
@XmlEnum
@GenerateDiffFunction(typeMethod = "getType")
public enum RepoType implements MetadataEnum {
    @XmlEnumValue("bazelmodules") @JsonProperty("BazelModules") BazelModules("bazelmodules", "Bazel Modules"),
    @XmlEnumValue("nix") @JsonProperty("Nix") Nix("nix", "Nix"),
    @XmlEnumValue("agentplugins") @JsonProperty("AgentPlugins") AgentPlugins("agentplugins", "Agent Plugins"),
    @XmlEnumValue("maven") @JsonProperty("Maven") Maven("maven", "Maven", "gav"),
    @XmlEnumValue("gradle") @JsonProperty("Gradle") Gradle("gradle", "Gradle", "gav"),
    @XmlEnumValue("ivy") @JsonProperty("Ivy") Ivy("ivy", "Ivy"),
    @XmlEnumValue("sbt") @JsonProperty("SBT") SBT("sbt", "SBT", "gav"),
    @XmlEnumValue("nuget") @JsonProperty("NuGet") NuGet("nuget", "NuGet"),
    @XmlEnumValue("gems") @JsonProperty("Gems") Gems("gems", "Gems", "rubygems"),
    @XmlEnumValue("npm") @JsonProperty("Npm") Npm("npm", "npm"),
    @XmlEnumValue("bower") @JsonProperty("Bower") Bower("bower", "Bower"),
    @XmlEnumValue("debian") @JsonProperty("Debian") Debian("debian", "Debian", "deb"),
    @XmlEnumValue("pypi") @JsonProperty("Pypi") Pypi("pypi", "Pypi"),
    @XmlEnumValue("cargo") @JsonProperty("Cargo") Cargo("cargo", "Cargo"),
    @XmlEnumValue("puppet") @JsonProperty("Puppet") Puppet("puppet", "Puppet"),
    @XmlEnumValue("docker") @JsonProperty("Docker") Docker("docker", "Docker"),
    @XmlEnumValue("oci") @JsonProperty("OCI") OCI("oci", "OCI"),
    @XmlEnumValue("helmOci") @JsonProperty("HelmOCI") HelmOCI("helmoci", "HelmOCI"),
    @XmlEnumValue("vagrant") @JsonProperty("Vagrant") Vagrant("vagrant", "Vagrant"),
    @XmlEnumValue("gitlfs") @JsonProperty("GitLfs") GitLfs("gitlfs", "GitLfs"),
    @XmlEnumValue("yum") @JsonProperty("YUM") YUM("yum", "RPM", "rpm"),
    @XmlEnumValue("vcs") @JsonProperty("VCS") VCS("vcs", "VCS"),
    @XmlEnumValue("p2") @JsonProperty("P2") P2("p2", "P2"),
    @XmlEnumValue("generic") @JsonProperty("Generic") Generic("generic", "Generic"),
    @XmlEnumValue("opkg") @JsonProperty("Opkg") Opkg("opkg", "Opkg"),
    @XmlEnumValue("cocoapods") @JsonProperty("CocoaPods") CocoaPods("cocoapods", "CocoaPods"),
    @XmlEnumValue("conan") @JsonProperty("Conan") Conan("conan", "Conan"),
    @XmlEnumValue("distribution") @JsonProperty("Distribution") Distribution("distribution", "Distribution"),
    @XmlEnumValue("releasebundles") @JsonProperty("ReleaseBundles") ReleaseBundles("releasebundles", "ReleaseBundles"),
    @XmlEnumValue("buildinfo") @JsonProperty("BuildInfo") BuildInfo("buildinfo", "BuildInfo"),
    @XmlEnumValue("pipeinfo") @JsonProperty("PipeInfo") PipeInfo("pipeinfo", "PipeInfo"),
    @XmlEnumValue("chef") @JsonProperty("Chef") Chef("chef", "Chef"),
    @XmlEnumValue("composer") @JsonProperty("Composer") Composer("composer", "Composer"),
    @XmlEnumValue("helm") @JsonProperty("Helm") Helm("helm", "Helm"),
    @XmlEnumValue("go") @JsonProperty("Go") Go("go", "Go"),
    @XmlEnumValue("cran") @JsonProperty("CRAN") CRAN("cran", "CRAN"),
    @XmlEnumValue("conda") @JsonProperty("Conda") Conda("conda", "Conda"),
    @XmlEnumValue("alpine") @JsonProperty("Alpine") Alpine("alpine", "Alpine Linux"),
    @XmlEnumValue("supportbundles") @JsonProperty("Support") Support("support", "Support"),
    @XmlEnumValue("pub") @JsonProperty("Pub") Pub("pub", "Pub"),
    @XmlEnumValue("swift") @JsonProperty("Swift") Swift("swift", "Swift"),
    @XmlEnumValue("terraform") @JsonProperty("Terraform") Terraform("terraform", "Terraform"),
    @XmlEnumValue("terraformbackend") @JsonProperty("TerraformBackend") TerraformBackend("terraformbackend",
            "TerraformBackend"),
    @XmlEnumValue("huggingfaceml") @JsonProperty("HuggingFaceML") HuggingFaceML("huggingfaceml", "HuggingFaceML"),
    @XmlEnumValue("frogml") @JsonProperty("FrogML") FrogML("frogml", "FrogML"),
    @XmlEnumValue("machinelearning") @JsonProperty("MachineLearning") MachineLearning("machinelearning",
            "MachineLearning"),
    @XmlEnumValue("ansible") @JsonProperty("Ansible") Ansible("ansible", "Ansible"),
    @XmlEnumValue("hex") @JsonProperty("Hex") Hex("hex", "Hex"),
    @XmlEnumValue("nimmodel") @JsonProperty("NimModel") NimModel("nimmodel", "NimModel"),
    @XmlEnumValue("jem") @JsonProperty("Jem") Jem("jem", "Jem"),
    @XmlEnumValue("aieditorextensions") @JsonProperty("AIEditorExtensions") AIEditorExtensions("aieditorextensions",
            "AI-Editor Extensions"),
    @XmlEnumValue("jetbrainsplugins") @JsonProperty("JetBrainsPlugins") JetBrainsPlugins("jetbrainsplugins",
            "JetBrains Plugins"),
    @XmlEnumValue("luarocks") @JsonProperty("LuaRocks") LuaRocks("luarocks", "LuaRocks"),
    @XmlEnumValue("skills") @JsonProperty("Skills") Skills("skills", "Skills"),
    @XmlEnumValue("agentpackages") @JsonProperty("AgentPackages") AgentPackages("agentpackages", "Agent Packages")
    ;


    @Getter
    private final String type;
    @Getter
    private final String displayName;
    private final String pkgidPrefix;
    private static final RepoType[] repoTypes = values();
    private static final Map<String, RepoType> TYPE_BY_NORMALIZED = buildTypeByNormalized();

    private static Map<String, RepoType> buildTypeByNormalized() {
        Map<String, RepoType> m = new HashMap<>(repoTypes.length + 1);
        for (RepoType rt : repoTypes) {
            m.put(rt.type, rt);
        }
        m.put("rpm", YUM);
        return Collections.unmodifiableMap(m);
    }

    private static final Set<RepoType> MAVEN_GROUP = Set.of(Maven, Ivy, Gradle, P2, SBT);
    private static final Set<RepoType> MAVEN_GROUP_EXCLUDE_P2 = Set.of(Maven, Ivy, Gradle, SBT);

    RepoType(String type, String displayName) {
        this.type = type;
        this.displayName = displayName;
        this.pkgidPrefix = type;
    }

    RepoType(String type, String displayName, String pkgidPrefix) {
        this.type = type;
        this.displayName = displayName;
        this.pkgidPrefix = pkgidPrefix;
    }

    // Used by REST methods to properly display package types to the users
    public String getEffectiveType() {
        if (this == YUM) {
            return "rpm";
        }
        return type;
    }

    public boolean isMavenGroup() {
        return MAVEN_GROUP.contains(this);
    }

    public static Set<RepoType> getMavenGroupTypes() {
        return MAVEN_GROUP;
    }

    public static Set<RepoType> getMavenGroupExcludeP2Types() {
        return MAVEN_GROUP_EXCLUDE_P2;
    }

    /**
     * All the repoTypes that knows to serve docker API requests
     */
    public boolean isDockerGroup() {
        return this == Docker || this == OCI || this == HelmOCI;
    }

    public boolean isNix() {
        return this == Nix;
    }

    public boolean isTerraformRegistry() {
        return this == Terraform;
    }

    public boolean isHuggingFace() {
        return this == HuggingFaceML;
    }

    public boolean isHelmOci() {
        return this == HelmOCI;
    }

    public boolean isConan() {
        return this == Conan;
    }

    public boolean isPipeInfo() {
        return this == PipeInfo;
    }

    public boolean isBuildInfo() {
        return this == BuildInfo;
    }

    /**
     * Permissions for build-info and release-bundle repositories are environment-agnostic in Access, so assigning an
     * env/stage to these repository types has no effect on access control. Used to reject such assignments.
     */
    public boolean isEnvironmentAgnostic() {
        return isBuildInfo() || this == ReleaseBundles;
    }

    public String getPkgidPrefix() {
        if (!pkgidPrefix.equals(type)) {
            return pkgidPrefix;
        }
        return type;
    }

    public boolean isVcsGroup() {
        return this == VCS || this == Composer || this == CocoaPods || this == Bower;
    }

    public static RepoType fromType(String type) {
        return TYPE_BY_NORMALIZED.getOrDefault(normalizePackageType(type), Generic);
    }

    public static RepoType fromTypeOrThrow(String type) {
        type = normalizePackageType(type);
        for (RepoType repoType : values()) {
            if (type.equalsIgnoreCase(repoType.type)) {
                return repoType;
            }
        }
        throw new IllegalArgumentException("No matching RepoType found for type: " + type);
    }

    // Used to convert users requests (REST \ UI) to backend type
    public static RepoType fromTypeWithNullable(String type) {
        if (type == null) {
            return null;
        }
        for (RepoType repoType : values()) {
            type = type.toLowerCase(Locale.ROOT);
            if (repoType.type.equalsIgnoreCase(type)) {
                return repoType;
            }
        }
        return null;
    }

    /**
     * Matches a string to a corresponding {@link RepoType} value. Case-insensitive.
     * "RPM" is matched to YUM package type.
     *
     * @return a matching enum value. <code>null</code> if no matching value found.
     */
    @Nullable
    public static RepoType fromString(@NonNull String input) {
        String normalizedType = normalizePackageType(input);
        for (RepoType repoType : values()) {
            if (repoType.type.equals(normalizedType)) {
                return repoType;
            }
        }
        return null;
    }

    private static String normalizePackageType(String packageType) {
        packageType = packageType.toLowerCase();
        if (packageType.equals("rpm")) {
            return "yum";
        }
        return packageType;
    }

    @Override
    public String getConfigName() {
        return type;
    }

    public static class RepoNameComparator implements Comparator<RepoType> {

        @Override
        public int compare(RepoType o1, RepoType o2) {
            return o1.name().compareTo(o2.name());
        }
    }
}
