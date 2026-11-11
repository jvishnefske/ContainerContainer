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

package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import com.google.common.collect.Maps;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepoAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteBasicRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteCacheRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteNetworkRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AIEditorExtensionsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AgentPluginsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AgentPackagesTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AlpineTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.AnsibleTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.BazelModulesTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.BowerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CargoTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.ChefTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CocoaPodsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.ComposerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.ConanTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CondaTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.CranTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.DebTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.DockerTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GemsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GenericTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GoTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.GradleTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HelmOciTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HelmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HexTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.HuggingFaceMLTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.IvyTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.JemTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.JetBrainsPluginsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.LuaRocksTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.MLTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.MavenTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NimModelTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NixTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NpmTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.NugetTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.OciTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.P2TypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.PubTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.PuppetTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.PypiTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SbtTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SkillsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.SwiftTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.TerraformTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.VcsTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.typespecific.YumTypeSpecificConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAdvancedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualBasicRepositoryConfigModel;

import java.util.Map;

/**
 * @author Dan Feldman
 */
public class RepositoryDefaultValuesModel {

    private Map<String, RestModel> defaultModels = Maps.newHashMap();

    public RepositoryDefaultValuesModel() {
        // Construct models with default values
        defaultModels.put("localBasic", new LocalBasicRepositoryConfigModel());
        defaultModels.put("localAdvanced", new LocalAdvancedRepositoryConfigModel());
        defaultModels.put("federatedAdvanced", new FederatedRepoAdvancedRepositoryConfigModel());
        defaultModels.put("remoteBasic", new RemoteBasicRepositoryConfigModel());
        defaultModels.put("remoteAdvanced", new RemoteAdvancedRepositoryConfigModel());
        defaultModels.put("virtualBasic", new VirtualBasicRepositoryConfigModel());
        defaultModels.put("virtualAdvanced", new VirtualAdvancedRepositoryConfigModel());
        defaultModels.put("network", new RemoteNetworkRepositoryConfigModel());
        defaultModels.put("cache", new RemoteCacheRepositoryConfigModel());
        defaultModels.put("bower", new BowerTypeSpecificConfigModel());
        defaultModels.put("cocoapods", new CocoaPodsTypeSpecificConfigModel());
        defaultModels.put("conan", new ConanTypeSpecificConfigModel());
        defaultModels.put("composer", new ComposerTypeSpecificConfigModel());
        defaultModels.put("gradle", new GradleTypeSpecificConfigModel());
        defaultModels.put("helm", new HelmTypeSpecificConfigModel());
        defaultModels.put("alpine", new AlpineTypeSpecificConfigModel());
        defaultModels.put("go", new GoTypeSpecificConfigModel());
        defaultModels.put("cran", new CranTypeSpecificConfigModel());
        defaultModels.put("conda", new CondaTypeSpecificConfigModel());
        defaultModels.put("ivy", new IvyTypeSpecificConfigModel());
        defaultModels.put("debian", new DebTypeSpecificConfigModel());
        defaultModels.put("docker", new DockerTypeSpecificConfigModel());
        defaultModels.put("oci", new OciTypeSpecificConfigModel());
        defaultModels.put("helmoci", new HelmOciTypeSpecificConfigModel());
        defaultModels.put("maven", new MavenTypeSpecificConfigModel());
        defaultModels.put("nuget", new NugetTypeSpecificConfigModel());
        defaultModels.put("npm", new NpmTypeSpecificConfigModel());
        defaultModels.put("p2", new P2TypeSpecificConfigModel());
        defaultModels.put("sbt", new SbtTypeSpecificConfigModel());
        defaultModels.put("vcs", new VcsTypeSpecificConfigModel());
        defaultModels.put("yum", new YumTypeSpecificConfigModel());
        defaultModels.put("pypi", new PypiTypeSpecificConfigModel());
        defaultModels.put("puppet", new PuppetTypeSpecificConfigModel());
        defaultModels.put("generic", new GenericTypeSpecificConfigModel());
        defaultModels.put("gems", new GemsTypeSpecificConfigModel());
        defaultModels.put("chef", new ChefTypeSpecificConfigModel());
        defaultModels.put("cargo", new CargoTypeSpecificConfigModel());
        defaultModels.put("terraform", new TerraformTypeSpecificConfigModel());
        defaultModels.put("pub", new PubTypeSpecificConfigModel());
        defaultModels.put("swift", new SwiftTypeSpecificConfigModel());
        if (ConstantValues.bazelModulesRepositoryEnabled.getBoolean()) {
            defaultModels.put("bazelmodules", new BazelModulesTypeSpecificConfigModel());
        }
        if (ConstantValues.nixRepositoryEnabled.getBoolean()) {
            defaultModels.put("nix", new NixTypeSpecificConfigModel());
        }
        if (ConstantValues.agentPluginsRepositoryEnabled.getBoolean()) {
            defaultModels.put("agentplugins", new AgentPluginsTypeSpecificConfigModel());
        }
        if (ConstantValues.agentPackagesRepositoryEnabled.getBoolean()) {
            defaultModels.put("agentpackages", new AgentPackagesTypeSpecificConfigModel());
        }
        defaultModels.put("huggingfaceml", new HuggingFaceMLTypeSpecificConfigModel());
        if (ConstantValues.machineLearningRepositoryEnabled.getBoolean()) {
            defaultModels.put("machinelearning", new MLTypeSpecificConfigModel());
        }
        if(ConstantValues.ansibleRepositoryEnabled.getBoolean()){
            defaultModels.put("ansible", new AnsibleTypeSpecificConfigModel());
        }
        if(ConstantValues.hexRepositoryEnabled.getBoolean()){
            defaultModels.put("hex", new HexTypeSpecificConfigModel());
        }
        if(ConstantValues.nimModelRepositoryEnabled.getBoolean()){
            defaultModels.put("nimmodel", new NimModelTypeSpecificConfigModel());
        }
        if (ConstantValues.jemRepositoryEnabled.getBoolean()) {
            defaultModels.put("jem", new JemTypeSpecificConfigModel());
        }
        if (ConstantValues.skillsRepositoryEnabled.getBoolean()) {
            defaultModels.put("skills", new SkillsTypeSpecificConfigModel());
        }
        if (ConstantValues.luaRocksRepositoryEnabled.getBoolean()) {
            defaultModels.put("luarocks", new LuaRocksTypeSpecificConfigModel());
        }
        if (isPackagesIdeEntitled()) {
            addPackagesIdeModels();
        }
    }

    private void addPackagesIdeModels() {
        if (ConstantValues.aieditorExtensionsRepositoryEnabled.getBoolean()) {
            defaultModels.put("aieditorextensions", new AIEditorExtensionsTypeSpecificConfigModel());
        }
        if (ConstantValues.jetbrainsPluginsRepositoryEnabled.getBoolean()) {
            defaultModels.put("jetbrainsplugins", new JetBrainsPluginsTypeSpecificConfigModel());
        }
    }

    private static boolean isPackagesIdeEntitled() {
        return ConstantValues.forcePluginsRepositoryEnabled.getBoolean() ||
            ContextHelper.get().beanForType(AddonsManager.class).isEntitled(EntitlementType.PACKAGES_IDE_EXTENSION);
    }

    public Map<String, RestModel> getDefaultModels() {
        return defaultModels;
    }
}
