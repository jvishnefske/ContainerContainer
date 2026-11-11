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

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.util.AolUtils;

import java.util.List;

import static org.artifactory.descriptor.repo.KeyPairDescriptor.ECDSA_TYPE;
import static org.artifactory.descriptor.repo.KeyPairDescriptor.ED25519_TYPE;
import static org.artifactory.descriptor.repo.KeyPairDescriptor.GPG_TYPE;
import static org.artifactory.descriptor.repo.KeyPairDescriptor.RSA_TYPE;

/**
 * @author Aviad Shikloshi
 */
@Getter
@Setter
public class RepositoryFieldsValuesModel {

    private final List<String> repositoryLayouts;
    private final List<String> keyPairs;
    private final List<String> gpgKeyPairs;
    private final List<String> ecdsaKeyPairs;
    private final List<String> ed25519KeyPairs;
    private final List<String> packageTypes;
    private final List<String> webStartKeyPairs;
    private final List<String> proxies;
    private final String defaultProxy;
    private boolean isCdnAllowedPerRepo;
    private List<String> cdnRedirectAllowedRepos;
    private List<String> forcedCdnRedirectRepos;
    private boolean isDockerV1Enabled;

    public RepositoryFieldsValuesModel(CentralConfigDescriptor descriptor,
            ArtifactoryBinaryService binaryService,
            List<String> cdnRedirectAllowedRepos, List<String> forcedCdnRedirectRepos) {
        repositoryLayouts = descriptor.getRepoLayouts().stream()
                .map(RepoLayout::getName)
                .toList();

        keyPairs = descriptor.getKeyPairs().stream()
                .filter(keyPairDescriptor -> RSA_TYPE.equals(keyPairDescriptor.getKeyType()))
                .map(KeyPairDescriptor::getKey)
                .toList();

        gpgKeyPairs = descriptor.getKeyPairs().stream()
                .filter(keyPairDescriptor -> GPG_TYPE.equals(keyPairDescriptor.getKeyType()))
                .map(KeyPairDescriptor::getKey)
                .toList();

        ecdsaKeyPairs = descriptor.getKeyPairs().stream()
                .filter(keyPairDescriptor -> ECDSA_TYPE.equals(keyPairDescriptor.getKeyType()))
                .map(KeyPairDescriptor::getKey)
                .toList();

        ed25519KeyPairs = descriptor.getKeyPairs().stream()
                .filter(keyPairDescriptor -> ED25519_TYPE.equals(keyPairDescriptor.getKeyType()))
                .map(KeyPairDescriptor::getKey)
                .toList();

        packageTypes = Lists.newArrayList(RepoType.values()).stream()
                .filter(RepositoryFieldsValuesModel::shouldIncludeRepoType)
                .map(RepoType::name)
                .toList();

        webStartKeyPairs = ContextHelper.get().beanForType(AddonsManager.class).addonByType(ArtifactWebstartAddon.class)
                .getKeyPairNames();

        CentralConfigDescriptor centralDescriptor = ContextHelper.get().beanForType(CentralConfigService.class)
                .getDescriptor();

        proxies = centralDescriptor.getProxies().stream().map(ProxyDescriptor::getKey).toList();

        ProxyDescriptor proxy = descriptor.getDefaultProxy();
        defaultProxy = proxy != null ? proxy.getKey() : null;

        this.cdnRedirectAllowedRepos = cdnRedirectAllowedRepos;
        this.forcedCdnRedirectRepos = forcedCdnRedirectRepos;
        this.isCdnAllowedPerRepo = binaryService.isCdnAllowed();
        this.isDockerV1Enabled = !AolUtils.skipDockerDns();
    }

    /**
     * @return Return false if the given repo type is under development and should not be visible in the UI
     */
    private static boolean shouldIncludeRepoType(RepoType repoType) {
        return switch (repoType) {
            case BazelModules -> ConstantValues.bazelModulesRepositoryEnabled.getBoolean();
            case Nix -> ConstantValues.nixRepositoryEnabled.getBoolean();
            case AgentPlugins -> ConstantValues.agentPluginsRepositoryEnabled.getBoolean();
            case MachineLearning -> ConstantValues.machineLearningRepositoryEnabled.getBoolean();
            case Ansible -> ConstantValues.ansibleRepositoryEnabled.getBoolean();
            case Hex -> ConstantValues.hexRepositoryEnabled.getBoolean();
            case NimModel -> ConstantValues.nimModelRepositoryEnabled.getBoolean();
            case Jem -> ConstantValues.jemRepositoryEnabled.getBoolean();
            case AIEditorExtensions -> ConstantValues.aieditorExtensionsRepositoryEnabled.getBoolean();
            case JetBrainsPlugins ->  ConstantValues.jetbrainsPluginsRepositoryEnabled.getBoolean();
            default -> true;
        };
    }
}
