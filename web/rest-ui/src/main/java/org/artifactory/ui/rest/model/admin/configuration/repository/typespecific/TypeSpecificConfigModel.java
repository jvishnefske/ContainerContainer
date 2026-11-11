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

package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.config.RepoConfigDefaultValues.DefaultUrl;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.model.RestModel;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_ENABLE_TOKEN_AUTHENTICATION;

/**
 * @author Dan Feldman
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "repoType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = NixTypeSpecificConfigModel.class, name = "Nix"),
        @JsonSubTypes.Type(value = BazelModulesTypeSpecificConfigModel.class, name = "BazelModules"),
        @JsonSubTypes.Type(value = AgentPluginsTypeSpecificConfigModel.class, name = "AgentPlugins"),
        @JsonSubTypes.Type(value = BowerTypeSpecificConfigModel.class, name = "Bower"),
        @JsonSubTypes.Type(value = CargoTypeSpecificConfigModel.class, name = "Cargo"),
        @JsonSubTypes.Type(value = ChefTypeSpecificConfigModel.class, name = "Chef"),
        @JsonSubTypes.Type(value = CocoaPodsTypeSpecificConfigModel.class, name = "CocoaPods"),
        @JsonSubTypes.Type(value = ComposerTypeSpecificConfigModel.class, name = "Composer"),
        @JsonSubTypes.Type(value = ConanTypeSpecificConfigModel.class, name = "Conan"),
        @JsonSubTypes.Type(value = DebTypeSpecificConfigModel.class, name = "Debian"),
        @JsonSubTypes.Type(value = OpkgTypeSpecificConfigModel.class, name = "Opkg"),
        @JsonSubTypes.Type(value = DockerTypeSpecificConfigModel.class, name = "Docker"),
        @JsonSubTypes.Type(value = OciTypeSpecificConfigModel.class, name = "OCI"),
        @JsonSubTypes.Type(value = HelmOciTypeSpecificConfigModel.class, name = "HelmOCI"),
        @JsonSubTypes.Type(value = GemsTypeSpecificConfigModel.class, name = "Gems"),
        @JsonSubTypes.Type(value = GenericTypeSpecificConfigModel.class, name = "Generic"),
        @JsonSubTypes.Type(value = GitLfsTypeSpecificConfigModel.class, name = "GitLfs"),
        @JsonSubTypes.Type(value = GradleTypeSpecificConfigModel.class, name = "Gradle"),
        @JsonSubTypes.Type(value = HelmTypeSpecificConfigModel.class, name = "Helm"),
        @JsonSubTypes.Type(value = AlpineTypeSpecificConfigModel.class, name = "Alpine"),
        @JsonSubTypes.Type(value = GoTypeSpecificConfigModel.class, name = "Go"),
        @JsonSubTypes.Type(value = CranTypeSpecificConfigModel.class, name = "CRAN"),
        @JsonSubTypes.Type(value = CondaTypeSpecificConfigModel.class, name = "Conda"),
        @JsonSubTypes.Type(value = IvyTypeSpecificConfigModel.class, name = "Ivy"),
        @JsonSubTypes.Type(value = MavenTypeSpecificConfigModel.class, name = "Maven"),
        @JsonSubTypes.Type(value = NpmTypeSpecificConfigModel.class, name = "Npm"),
        @JsonSubTypes.Type(value = NugetTypeSpecificConfigModel.class, name = "NuGet"),
        @JsonSubTypes.Type(value = P2TypeSpecificConfigModel.class, name = "P2"),
        @JsonSubTypes.Type(value = PypiTypeSpecificConfigModel.class, name = "Pypi"),
        @JsonSubTypes.Type(value = PubTypeSpecificConfigModel.class, name = "Pub"),
        @JsonSubTypes.Type(value = PuppetTypeSpecificConfigModel.class, name = "Puppet"),
        @JsonSubTypes.Type(value = ReleaseBundlesRepoTypeSpecificConfigModel.class, name = "ReleaseBundles"),
        @JsonSubTypes.Type(value = SbtTypeSpecificConfigModel.class, name = "SBT"),
        @JsonSubTypes.Type(value = SwiftTypeSpecificConfigModel.class, name = "Swift"),
        @JsonSubTypes.Type(value = VagrantTypeSpecificConfigModel.class, name = "Vagrant"),
        @JsonSubTypes.Type(value = VcsTypeSpecificConfigModel.class, name = "VCS"),
        @JsonSubTypes.Type(value = YumTypeSpecificConfigModel.class, name = "YUM"),
        @JsonSubTypes.Type(value = TerraformTypeSpecificConfigModel.class, name = "Terraform"),
        @JsonSubTypes.Type(value = TerraformBackendTypeSpecificConfigModel.class, name = "TerraformBackend"),
        @JsonSubTypes.Type(value = BuildInfoTypeSpecificConfigModel.class, name = "BuildInfo"),
        @JsonSubTypes.Type(value = HuggingFaceMLTypeSpecificConfigModel.class, name = "HuggingFaceML"),
        @JsonSubTypes.Type(value = MLTypeSpecificConfigModel.class, name = "MachineLearning"),
        @JsonSubTypes.Type(value = AnsibleTypeSpecificConfigModel.class, name = "Ansible"),
        @JsonSubTypes.Type(value = HexTypeSpecificConfigModel.class, name = "Hex"),
        @JsonSubTypes.Type(value = NimModelTypeSpecificConfigModel.class, name = "NimModel"),
        @JsonSubTypes.Type(value =  JemTypeSpecificConfigModel.class, name = "Jem"),
        @JsonSubTypes.Type(value = AIEditorExtensionsTypeSpecificConfigModel.class, name = "AIEditorExtensions"),
        @JsonSubTypes.Type(value = JetBrainsPluginsTypeSpecificConfigModel.class, name = "JetBrainsPlugins"),
        @JsonSubTypes.Type(value = LuaRocksTypeSpecificConfigModel.class, name = "LuaRocks"),
        @JsonSubTypes.Type(value = SkillsTypeSpecificConfigModel.class, name = "Skills"),
        @JsonSubTypes.Type(value = AgentPackagesTypeSpecificConfigModel.class, name = "AgentPackages")

})
public interface TypeSpecificConfigModel extends RestModel {

    RepoType getRepoType();

    default Boolean isEnableTokenAuthentication() {
        return DEFAULT_ENABLE_TOKEN_AUTHENTICATION;
    }

    default void setEnableTokenAuthentication(Boolean enableTokenAuthentication) {
    }

    /**
     * This should retrieve the default remote url for each package type
     * For instance: Maven, Gradle, Ivy and SBT should return DefaultUrl.Constants#MAVEN_URL
     * <p>
     * Notice: method name corresponds to the JSON model field name and is used by the UI.
     */
    default String getUrl() {
        return DefaultUrl.urlByType(getRepoType());
    }

    /**
     * Handle configuration shared between local, remote and virtual repositories
     */
    default void validateSharedTypeSpecific() {
        // no implementation by default
    }

    /**
     * Handle configuration specific to local repositories
     */
    default void validateLocalTypeSpecific() throws RepoConfigException {
        // no implementation by default
    }

    /**
     * Handle configuration specific to federated repositories
     */
    default void validateFederatedTypeSpecific() throws RepoConfigException {
        // no implementation by default
    }

    /**
     * Handle configuration specific to remote repositories
     */
    default void validateRemoteTypeSpecific() throws RepoConfigException {
        // no implementation by default
    }

    /**
     * Handle configuration specific to virtual repositories
     */
    default void validateVirtualTypeSpecific(AddonsManager addonsManager) throws RepoConfigException {
        // no implementation by default
    }

    default void throwUnsupportedRemoteRepoType() throws RepoConfigException {
        throw new RepoConfigException("Package type " + getRepoType().name()
                + " is unsupported in remote repositories", SC_BAD_REQUEST);
    }

    default void throwUnsupportedVirtualRepoType() throws RepoConfigException {
        throw new RepoConfigException("Package type " + getRepoType().name()
                + " is unsupported in virtual repositories", SC_BAD_REQUEST);
    }
}
