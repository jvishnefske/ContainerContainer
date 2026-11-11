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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.actions;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.bower.BowerAddon;
import org.artifactory.addon.cargo.CargoAddon;
import org.artifactory.addon.chef.ChefAddon;
import org.artifactory.addon.composer.ComposerAddon;
import org.artifactory.addon.pub.PubAddon;
import org.artifactory.addon.puppet.PuppetAddon;
import org.artifactory.addon.swift.SwiftAddon;
import org.artifactory.addon.terraform.TerraformAddon;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.ivy.IvyNaming;
import org.artifactory.mime.MavenNaming;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.LocalConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.NodeRepoTypeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.artifactory.mime.DockerNaming.MANIFEST_LIST_FILENAME;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidenceEnabled;

/**
 * Builds tabs list to be added to a tree node
 *
 * @author Shay Yaakov
 */
@Component
public class TreeNodeTabsPopulator {

    static final String EFFECTIVE_PERMISSION_TAB = "EffectivePermission";
    static final String PROPERTIES_TAB = "Properties";
    static final String EVIDENCE_TAB = "Evidence";
    static final String WATCH_TAB = "Watch";
    static final String GENERAL_TAB = "General";
    static final String XRAY_TAB = "Xray";

    private RepositoryService repoService;
    private AuthorizationService authService;
    private ProjectsService projectsService;
    private PropertiesService propertiesService;
    private AddonsManager addonsManager;
    private PackageSpecification packageSpecification;
    private NodeRepoTypeHelper nodeRepoTypeHelper;

    @VisibleForTesting
    @PostConstruct
    void init() {
        nodeRepoTypeHelper =
                new NodeRepoTypeHelper(addonsManager, repoService, propertiesService, packageSpecification);
    }

    public void populateForRepository(TabsAndActions tabsAndActions) {
        List<TabOrAction> tabs = Lists.newArrayList();
        RepoPath repoPath = tabsAndActions.getRepoPath();
        String repoType = tabsAndActions.getRepoType();
        if ("remote".equals(repoType) || "virtual".equals(repoType) || "trash".equals(repoType) ||
                "supportBundles".equals(repoType)) {
            tabs.add(general());
        } else {
            tabs.add(general());
            addCommonsTabs(tabs, repoPath, false, tabsAndActions);
        }
        tabsAndActions.setTabs(tabs.stream().filter(Objects::nonNull).collect(Collectors.toList()));
    }

    public void populateForFolder(TabsAndActions tabsAndActions) {
        List<TabOrAction> tabs = Lists.newArrayList();
        RepoPath repoPath = tabsAndActions.getRepoPath();
        String repoType = tabsAndActions.getRepoType();
        RepoType repoPackageType = tabsAndActions.getRepoPkgType();
        boolean isCached = tabsAndActions.getCached();
        if ("remote".equals(repoType) || "trash".equals(repoType) || "supportBundles".equals(repoType)) {
            tabs.add(general());
        } else if ("distribution".equals(repoType)) {
            tabs.add(general());
            addCommonsTabs(tabs, repoPath, false, tabsAndActions);
        } else if ("virtual".equals(repoType)) {
            tabs.add(general());
            if (!isCached) {
                tabsAndActions.setTabs(tabs.stream().filter(Objects::nonNull).collect(Collectors.toList()));
                return;
            }
            repoPath = repoService.getVirtualFolderInfo(repoPath).getRepoPath();
            RepoPath finalRepoPath = repoPath;
            if (repoService.getLocalFederatedAndCachedRepoConfigs().stream()
                    .anyMatch(e -> e.getKey().equals(finalRepoPath.getRepoKey()))) {
                tabs.addAll(dockerV1(repoPath));
                tabs.add(dockerV2(repoPath));
                tabs.add(nix(repoPath, repoPackageType));
                addCommonsTabs(tabs, repoPath, true, tabsAndActions);
            }
        } else {
            tabs.add(general());
            tabs.addAll(dockerV1(repoPath));
            tabs.add(dockerV2(repoPath));
            tabs.add(conan(repoPath));
            tabs.add(nix(repoPath, repoPackageType));
            addCommonsTabs(tabs, repoPath, false, tabsAndActions);
        }
        tabsAndActions.setTabs(tabs.stream().filter(Objects::nonNull).collect(Collectors.toList()));
    }

    public void populateForFile(TabsAndActions tabsAndActions) {
        List<TabOrAction> tabs = Lists.newArrayList();
        RepoPath repoPath = tabsAndActions.getRepoPath();
        String repoType = tabsAndActions.getRepoType();
        switch (repoType) {
            case "remote":
            case "trash":
            case "supportBundles":
                tabs.add(general());
                break;
            case "virtual":
                fillVirtualTabs(tabs, tabsAndActions, repoPath);
                break;
            case "distribution":
                fillDistributionTabs(tabs, repoPath, tabsAndActions);
                break;
            default:
                fillDefaultTabs(tabs, repoPath, tabsAndActions);
        }
        tabsAndActions.setTabs(tabs.stream().filter(Objects::nonNull).collect(Collectors.toList()));
    }

    private void fillDefaultTabs(List<TabOrAction> tabs, RepoPath repoPath, TabsAndActions tabsAndActions) {
        tabs.add(general());
        addSpecificFileTabs(tabs, repoPath, tabsAndActions.getRepoPkgType());
        tabs.add(effectivePermissions(repoPath));
        tabs.add(xray(repoPath, false, tabsAndActions));
        tabs.add(properties());
        if (isEvidenceEnabled()) {
            tabs.add(new TabOrAction(EVIDENCE_TAB));
        }
        tabs.add(watch(repoPath));
        addBuildsTab(tabs);
    }

    private void fillDistributionTabs(List<TabOrAction> tabs, RepoPath repoPath, TabsAndActions tabsAndActions) {
        tabs.add(general());
        addCommonsTabs(tabs, repoPath, false, tabsAndActions);
    }

    private void fillVirtualTabs(List<TabOrAction> tabs, TabsAndActions tabsAndActions,
            RepoPath repoPath) {
        tabs.add(general());
        if (!tabsAndActions.getCached()) {
            return;
        }
        repoPath = repoService.getVirtualFileInfo(repoPath).getRepoPath();
        RepoPath finalRepoPath = repoPath;
        List<LocalConfig> localConfigs = repoService.getLocalFederatedAndCachedRepoConfigs().stream()
                .filter(e -> e.getKey().equals(finalRepoPath.getRepoKey()))
                .toList();
        if (isNotEmpty(localConfigs)) {
            boolean hasLocalRepository = localConfigs.stream().anyMatch(it -> it.isLocal() || !it.isCache());
            addTabsForCachedOrLocalFile(tabs, repoPath, tabsAndActions, hasLocalRepository);
        }
    }

    @VisibleForTesting
    void addTabsForCachedOrLocalFile(List<TabOrAction> tabs, RepoPath repoPath, TabsAndActions tabsAndActions,
            boolean hasLocalRepository) {
        addSpecificFileTabs(tabs, repoPath, tabsAndActions.getRepoPkgType());
        tabs.add(effectivePermissions(repoPath));
        tabs.add(xray(repoPath, true, tabsAndActions));
        tabs.add(properties());
        if (hasLocalRepository && isEvidenceEnabled()) {
            tabs.add(new TabOrAction(EVIDENCE_TAB));
        }
        tabs.add(watch(repoPath));
        addBuildsTab(tabs);
    }

    private void addBuildsTab(List<TabOrAction> tabs) {
        if (addonsManager.isEntitled(EntitlementType.JFRT)) {
            tabs.add(builds());
        }
    }

    private void addCommonsTabs(List<TabOrAction> tabs, RepoPath repoPath, boolean virtualSource,
            TabsAndActions tabsAndActions) {
        tabs.add(effectivePermissions(repoPath));
        tabs.add(xray(repoPath, virtualSource, tabsAndActions));
        tabs.add(properties());
        tabs.add(watch(repoPath));
    }

    /**
     * We display the Xray tab in case the Xray integration is enabled (globally)
     * + Xray is configured per the repository
     * + Xray can handle the specific file
     * + Repo is not virtual (currently we do not support that. Change this is the future)
     * RTDEV-23907 - The tab is now shown regardless of supported types or repository config.
     * Now only checking for Xray enabled.
     *
     * @param virtualSource True in case the original path is of a virtual repo, False otherwise.
     */
    TabOrAction xray(RepoPath repoPath, boolean virtualSource, TabsAndActions tabsAndActions) {
        XrayAddon xrayAddon = addonsManager.addonByType(XrayAddon.class);
        if (virtualSource) {
            return null;
        }
        String scanVisibilityMinXrayVersion = ConstantValues.treeNodeTabsScanVisibilityMinXrayVersion.getString();
        if (xrayAddon.isXrayEnabled() && xrayAddon.isXrayOfMinimalRequiredVersion(scanVisibilityMinXrayVersion)) {
            if ("file".equals(tabsAndActions.getType())) {
                return new TabOrAction(XRAY_TAB);
            }
        } else {
            if (xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(repoPath.getRepoKey()) &&
                    xrayAddon.isHandledByXray(repoPath)) {
                return new TabOrAction(XRAY_TAB);
            }
        }
        return null;
    }

    private void addSpecificFileTabs(List<TabOrAction> tabs, RepoPath repoPath, RepoType repoPackageType) {
        tabs.add(dockerV2(repoPath));
        tabs.add(pom(repoPath));
        tabs.add(xml(repoPath));
        tabs.add(rpm(repoPath));
        tabs.add(nuget(repoPath, repoPackageType));
        tabs.add(gem(repoPath));
        tabs.add(npm(repoPath, repoPackageType));
        tabs.add(debian(repoPath));
        tabs.add(opkg(repoPath));
        tabs.add(bower(repoPath));
        tabs.add(bazelModulesInfo(repoPath, repoPackageType));
        tabs.add(composer(repoPath));
        tabs.add(chef(repoPath));
        tabs.add(pypi(repoPath, repoPackageType));
        tabs.add(puppet(repoPath));
        tabs.add(helm(repoPath, repoPackageType));
        tabs.add(cargo(repoPath));
        tabs.add(pub(repoPath));
        tabs.add(swift(repoPath));
        tabs.add(alpine(repoPath, repoPackageType));
        tabs.add(go(repoPath, repoPackageType));
        tabs.add(cran(repoPath, repoPackageType));
        tabs.add(conda(repoPath, repoPackageType));
        tabs.add(terraform(repoPath));
        tabs.add(terraformBackend(repoPath));
        tabs.add(huggingFaceMl(repoPath, repoPackageType));
        tabs.add(jetBrainsPlugins(repoPath, repoPackageType));
        tabs.add(machineLearning(repoPath, repoPackageType));
        tabs.add(ansible(repoPath, repoPackageType));
        tabs.add(hex(repoPath, repoPackageType));
        tabs.add(nimModel(repoPath, repoPackageType));
        tabs.add(agentPlugins(repoPath, repoPackageType));
        tabs.add(luarocks(repoPath, repoPackageType));
    }

    private TabOrAction builds() {
        boolean shouldDisplay = authService.hasBuildBasicReadPermission();
        if (shouldDisplay) {
            return new TabOrAction("Builds");
        }
        return null;
    }

    private TabOrAction pom(RepoPath repoPath) {
        if (MavenNaming.isPom((repoPath.getName()))) {
            return new TabOrAction("PomView");
        }
        return null;
    }

    private TabOrAction xml(RepoPath repoPath) {
        if (NamingUtils.isXml(repoPath.getName()) && !MavenNaming.isPom((repoPath.getName()))) {
            return IvyNaming.isIvyFileName(repoPath.getName()) ? new TabOrAction("IVYXml") :
                    new TabOrAction("GeneralXml");
        }
        return null;
    }

    private TabOrAction rpm(RepoPath repoPath) {
        if (repoPath.getName().endsWith(".rpm") && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.YUM)
                && addonsManager.isAddonSupported(AddonType.YUM)) {
            return new TabOrAction("Rpm");
        }
        return null;
    }

    private TabOrAction nuget(RepoPath repoPath, RepoType packageType) {
        if (RepoType.NuGet != packageType) {
            return null;
        }
        boolean isNugetFile =
                packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath(), RepoType.NuGet);
        if (isNugetFile) {
            return new TabOrAction("NuPkgInfo");
        }
        return null;
    }

    private TabOrAction luarocks(RepoPath repoPath, RepoType packageType){
        if (RepoType.LuaRocks != packageType) {
            return null;
        }
        boolean isLuaRocksFile =
                packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath(), RepoType.LuaRocks);
        if (isLuaRocksFile) {
            return new TabOrAction("RockInfo");
        }
        return null;
    }

    private TabOrAction gem(RepoPath repoPath) {
        if (repoPath.getName().endsWith(".gem") && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.Gems)
                && addonsManager.isAddonSupported(AddonType.GEMS)) {
            return new TabOrAction("RubyGems");
        }
        return null;
    }

    private TabOrAction npm(RepoPath repoPath, RepoType packageType) {
        if (RepoType.Npm != packageType) {
            return null;
        }
        if (packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath(), RepoType.Npm)) {
            return new TabOrAction("NpmInfo");
        }
        return null;
    }

    private TabOrAction debian(RepoPath repoPath) {
        if ((repoPath.getName().endsWith(".deb") || repoPath.getName().endsWith(".ddeb") ||
                repoPath.getName().endsWith(".dsc")) &&
                nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.Debian) &&
                addonsManager.isAddonSupported(AddonType.DEBIAN)) {
            return new TabOrAction("DebianInfo");
        }
        return null;
    }

    private TabOrAction opkg(RepoPath repoPath) {
        if (repoPath.getName().endsWith(".ipk") && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.Opkg)
                && addonsManager.isAddonSupported(AddonType.OPKG)) {
            return new TabOrAction("OpkgInfo");
        }
        return null;
    }

    private TabOrAction bower(RepoPath repoPath) {
        boolean isBowerFile = addonsManager.addonByType(BowerAddon.class).isBowerFile(repoPath.getName());
        if (isBowerFile && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.Bower) &&
                addonsManager.isAddonSupported(AddonType.BOWER)) {
            return new TabOrAction("BowerInfo");
        }
        return null;
    }

    private TabOrAction composer(RepoPath repoPath) {
        boolean isComposerFile =
                addonsManager.addonByType(ComposerAddon.class).isComposerSupportedExtension(repoPath.getName());
        if (isComposerFile && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.Composer) &&
                addonsManager.isAddonSupported(AddonType.COMPOSER)) {
            return new TabOrAction("ComposerInfo");
        }
        return null;
    }

    private TabOrAction chef(RepoPath repoPath) {
        boolean isChefFile = addonsManager.addonByType(ChefAddon.class).isChefCookbookFile(repoPath.getName());
        if (isChefFile && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.Chef) &&
                addonsManager.isAddonSupported(AddonType.CHEF)) {
            return new TabOrAction("ChefInfo");
        }
        return null;
    }

    private TabOrAction pypi(RepoPath repoPath, RepoType packageType) {
        if (RepoType.Pypi != packageType) {
            return null;
        }
        if (packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath(), RepoType.Pypi)) {
            return new TabOrAction("PyPIInfo");
        }
        return null;
    }

    private TabOrAction puppet(RepoPath repoPath) {
        boolean isPuppetFile = addonsManager.addonByType(PuppetAddon.class).isPuppetFile(repoPath);
        if (isPuppetFile && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.Puppet) &&
                addonsManager.isAddonSupported(AddonType.PUPPET)) {
            return new TabOrAction("PuppetInfo");
        }
        return null;
    }

    private TabOrAction helm(RepoPath repoPath, RepoType packageType) {
        if (RepoType.Helm != packageType) {
            return null;
        }
        boolean isHelmFile = packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
        if (isHelmFile) {
            return new TabOrAction("HelmInfo");
        }
        return null;
    }

    private TabOrAction cargo(RepoPath repoPath) {
        boolean isCargoFile = addonsManager.addonByType(CargoAddon.class).isCargoFile(repoPath.getName());
        if (isCargoFile && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.Cargo) &&
                addonsManager.isAddonSupported(AddonType.CARGO)) {
            return new TabOrAction("CargoInfo");
        }
        return null;
    }

    private TabOrAction pub(RepoPath repoPath) {
        boolean isPubFile = addonsManager.addonByType(PubAddon.class).isPubFile(repoPath.getName());
        if (isPubFile && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.Pub) &&
                addonsManager.isAddonSupported(AddonType.PUB)) {
            return new TabOrAction("PubInfo");
        }
        return null;
    }

    private TabOrAction bazelModulesInfo(RepoPath repoPath, RepoType packageType) {
        if (RepoType.BazelModules != packageType) {
            return null;
        }
        boolean isBazelModulesInfoFile =
                packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
        if (isBazelModulesInfoFile) {
            return new TabOrAction("BazelModulesInfo");
        }
        return null;
    }

    private TabOrAction huggingFaceMl(RepoPath repoPath, RepoType packageType) {
        if (RepoType.HuggingFaceML != packageType) {
            return null;
        }
        boolean isHuggingFaceMlFile = packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
        if (isHuggingFaceMlFile) {
            if (repoPath.getPath().startsWith("models")) {
                return new TabOrAction("HuggingfacemlModelInfo");
            } else {
                return new TabOrAction("HuggingfacemlDatasetInfo");
            }
        }
        return null;
    }

    private TabOrAction jetBrainsPlugins(RepoPath repoPath, RepoType packageType) {
        if (RepoType.JetBrainsPlugins != packageType) {
            return null;
        }
        boolean isJetBrainsPluginsFile =
                packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
        if (isJetBrainsPluginsFile && repoPath.getPath().endsWith(".zip")) {
            return new TabOrAction("JetBrainsPluginsInfo");
        }
        return null;
    }

    private TabOrAction machineLearning(RepoPath repoPath, RepoType packageType) {
        if (RepoType.MachineLearning != packageType) {
            return null;
        }
        boolean isMLFile = packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
        if (isMLFile) {
            if (repoPath.getPath().startsWith("models")) {
                return new TabOrAction("MachineLearningModelInfo");
            } else {
                return new TabOrAction("MachineLearningDatasetInfo");
            }
        }
        return null;
    }

    private TabOrAction ansible(RepoPath repoPath, RepoType packageType) {
        if (RepoType.Ansible != packageType) {
            return null;
        }
        boolean isAnsibleFile = packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
        if (isAnsibleFile) {
            return new TabOrAction("AnsibleInfo");
        }
        return null;
    }

    private TabOrAction hex(RepoPath repoPath, RepoType packageType) {
        if (RepoType.Hex != packageType) {
            return null;
        }
        boolean isHexPackage = packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
        if (isHexPackage) {
            return new TabOrAction("HexInfo");
        }
        return null;
    }

    private TabOrAction nimModel(RepoPath repoPath, RepoType packageType) {
        if (RepoType.NimModel != packageType) {
            return null;
        }
        boolean isNimModel = packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
        if (isNimModel) {
            return new TabOrAction("NimModelInfo");
        }
        return null;
    }

    private TabOrAction agentPlugins(RepoPath repoPath, RepoType packageType) {
        if (RepoType.AgentPlugins != packageType) {
            return null;
        }
        boolean isAgentPlugins = packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
        if (isAgentPlugins) {
            return new TabOrAction("AgentPluginsInfo");
        }
        return null;
    }

    private TabOrAction terraform(RepoPath repoPath) {
        boolean isTerraformFile = addonsManager.addonByType(TerraformAddon.class).isTerraformFile(repoPath.getName());
        if (isTerraformFile && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.Terraform) &&
                addonsManager.isAddonSupported(AddonType.TERRAFORM)) {
            return new TabOrAction("TerraformInfo");
        }
        return null;
    }

    private TabOrAction terraformBackend(RepoPath repoPath) {
        boolean isTerraformStateFile =
                addonsManager.addonByType(TerraformAddon.class).isTerraformStateFile(repoPath.getName());
        if (isTerraformStateFile && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.TerraformBackend) &&
                addonsManager.isAddonSupported(AddonType.TERRAFORM)) {
            return new TabOrAction("StateInfo");
        }
        return null;
    }

    private TabOrAction swift(RepoPath repoPath) {
        boolean isSwiftFile = addonsManager.addonByType(SwiftAddon.class).isSwiftFile(repoPath.getPath());
        if (isSwiftFile && nodeRepoTypeHelper.isRepoSupportType(repoPath, RepoType.Swift) &&
                addonsManager.isAddonSupported(AddonType.SWIFT)) {
            return new TabOrAction("SwiftInfo");
        }
        return null;
    }

    private TabOrAction alpine(RepoPath repoPath, RepoType packageType) {
        if (RepoType.Alpine != packageType) {
            return null;
        }
        boolean isAlpinePackage =
                packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath(), RepoType.Alpine);
        if (isAlpinePackage) {
            return new TabOrAction("AlpineInfo");
        }
        return null;
    }

    private TabOrAction go(RepoPath repoPath, RepoType packageType) {
        if (RepoType.Go != packageType) {
            return null;
        }
        if (packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath())) {
            return new TabOrAction("GoInfo");
        }
        return null;
    }

    private TabOrAction cran(RepoPath repoPath, RepoType packageType) {
        if (RepoType.CRAN != packageType) {
            return null;
        }
        if (packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath())) {
            return new TabOrAction("CranInfo");
        }
        return null;
    }

    private TabOrAction nix(RepoPath repoPath, RepoType packageType) {
        if (RepoType.Nix != packageType) {
            return null;
        }
        if (packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath())) {
            return new TabOrAction("NixInfo");
        }
        return null;
    }


    private TabOrAction conda(RepoPath repoPath, RepoType packageType) {
        if (RepoType.Conda != packageType) {
            return null;
        }
        if (packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath())) {
            return new TabOrAction("CondaInfo");
        }
        return null;
    }

    private List<TabOrAction> dockerV1(RepoPath repoPath) {
        if (nodeRepoTypeHelper.isDockerFileTypeAndSupported(repoPath)) {
            return Lists.newArrayList(new TabOrAction("DockerInfo"), new TabOrAction("DockerAncestryInfo"));
        }
        return Lists.newArrayList();
    }

    private TabOrAction dockerV2(RepoPath repoPath) {
        boolean isManifestListFile = MANIFEST_LIST_FILENAME.equals(repoPath.getName());
        if (isManifestListFile || nodeRepoTypeHelper.isDockerManifestFolder(repoPath)) {
            return new TabOrAction("DockerV2Info");
        }
        return null;
    }

    private TabOrAction conan(RepoPath repoPath) {
        if (nodeRepoTypeHelper.isConanReferenceFolder(repoPath)) {
            return new TabOrAction("ConanInfo");
        } else if (nodeRepoTypeHelper.isConanPackageFolder(repoPath)) {
            return new TabOrAction("ConanPackageInfo");
        }
        return null;
    }

    private TabOrAction general() {
        return new TabOrAction(GENERAL_TAB);
    }

    private TabOrAction effectivePermissions(RepoPath repoPath) {
        String project = projectsService.getProjectKeyOfRepository(repoPath.getRepoKey());
        if (ProjectsUtils.isDefaultProject(project) && authService.canManage(repoPath)) {
            return new TabOrAction(EFFECTIVE_PERMISSION_TAB);
        }
        return null;
    }

    private TabOrAction properties() {
        return new TabOrAction(PROPERTIES_TAB);
    }

    private TabOrAction watch(RepoPath repoPath) {
        if (authService.canManage(repoPath)) {
            return new TabOrAction(WATCH_TAB);
        }
        return null;
    }

    @Autowired
    public void setProjectsService(@NonNull ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    public void setPropertiesService(@NonNull PropertiesService propertiesService) {
        this.propertiesService = propertiesService;
    }

    @Autowired
    public void setAddonsManager(@NonNull AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setRepoService(@NonNull RepositoryService repoService) {
        this.repoService = repoService;
    }

    @Autowired
    public void setAuthService(@NonNull AuthorizationService authService) {
        this.authService = authService;
    }

    @Autowired
    public void setPackageInfoProvider(PackageSpecification packageSpecification) {
        this.packageSpecification = packageSpecification;
    }
}
