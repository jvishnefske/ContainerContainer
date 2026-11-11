/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
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

package org.artifactory.ui.rest.service.admin.security.keys.keypairs;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.common.keys.KeyPairService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.security.keys.KeyPairIModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;

/**
 * @author Yuval Reches
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetAllKeyPairsService implements RestService {

    private final RepositoryConfigService repositoryConfigService;
    private final KeyPairService keyPairService;

    @Autowired
    public GetAllKeyPairsService(RepositoryConfigService repositoryConfigService, KeyPairService keyPairService) {
        this.repositoryConfigService = repositoryConfigService;
        this.keyPairService = keyPairService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        List<String> types = request.getQueryParamListByKey("type");
        List<KeyPairDescriptor> keyPairs = keyPairService.getKeyPairs(types);
        response.iModelList(keyPairs.stream().map(this::createKeyPairIModel).toList());
    }

    private KeyPairIModel createKeyPairIModel(KeyPairDescriptor keyPair) {
        KeyPairIModel keyPairIModel = new KeyPairIModel();
        keyPairIModel.setPairName(keyPair.getKey());
        keyPairIModel.setAlias(keyPair.getAlias());
        keyPairIModel.setPairType(keyPair.getKeyType());
        keyPairIModel.setSource(keyPair.getVaultType());
        keyPairIModel.setResources(getListOfReposPerKeyPair(keyPair.getKey()));
        keyPairIModel.setResourcesAsSecondary(getListOfReposPerKeyPairAsSecondary(keyPair.getKey()));
        keyPairIModel.setUnavailable(keyPair.getUnavailable());
        if (StringUtils.isNotBlank(keyPair.getVaultKey()))
            keyPairIModel.setVaultKey(keyPair.getVaultKey());
        return keyPairIModel;
    }

    @VisibleForTesting
    List<String> getListOfReposPerKeyPair(String key) {
        return getReposWithPair(key, repositoryConfigService.getRepoConfigsByTypes(Set.of(LOCAL, FEDERATED, VIRTUAL)));
    }

    private List<String> getListOfReposPerKeyPairAsSecondary(String key) {
        return getReposWithPairAsSecondary(key,
                repositoryConfigService.getRepoConfigsByTypes(Set.of(LOCAL, FEDERATED, VIRTUAL)));
    }

    private List<String> getReposWithPair(String key, List<RepoConfig> reposList) {
        return reposList.stream()
                .filter(repo -> repo.getSecurityConfig().getPrimaryKeyPairRef() != null)
                .filter(repo -> key.equals(repo.getSecurityConfig().getPrimaryKeyPairRef()))
                .map(RepoConfig::getKey).toList();
    }

    private List<String> getReposWithPairAsSecondary(String key, List<RepoConfig> reposList) {
        return reposList.stream()
                .filter(repo -> repo.getSecurityConfig().getSecondaryKeyPairRef() != null)
                .filter(repo -> key.equals(repo.getSecurityConfig().getSecondaryKeyPairRef()))
                .map(RepoConfig::getKey).toList();
    }
}