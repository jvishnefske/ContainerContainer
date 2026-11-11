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

package org.artifactory.ui.rest.model.admin.services.indexer;

import com.google.common.collect.Lists;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.index.IndexerDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.util.JsonUtil;
import org.artifactory.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;

/**
 * @author Chen Keinan
 */
public class Indexer implements RestModel {

    private boolean enabled;
    private String cronExp;

    private List<String> includedRepos = Lists.newArrayList();
    private List<String> excludedRepos = Lists.newArrayList();

    Indexer() {
    }

    public Indexer(IndexerDescriptor indexerDescriptor) {
        if (indexerDescriptor != null) {
            setEnabled(indexerDescriptor.isEnabled());
            setCronExp(indexerDescriptor.getCronExp());
            populateRepos(indexerDescriptor.getIncludedRepositories());
        }
    }

    void populateRepos(SortedSet<String> configIncludedRepos) {
        RepositoryConfigService repoConfigService =
                ContextHelper.get().beanForType(RepositoryConfigService.class);
        List<String> mavenRepoKeys = repoConfigService.getRepoConfigsByTypes(Set.of(LOCAL, FEDERATED, REMOTE, VIRTUAL))
                .stream().filter(repoConfig -> repoConfig.getPackageType().isMavenGroup())
                .map(RepoConfig::getKey)
                .collect(Collectors.toList());

        if (CollectionUtils.notNullOrEmpty(configIncludedRepos)) {
            includedRepos.addAll(configIncludedRepos);
        }

        // add all the rest of the repos to the exclusion list
        mavenRepoKeys.removeAll(includedRepos);
        excludedRepos.addAll(mavenRepoKeys);
    }

    public List<String> getIncludedRepos() {
        return includedRepos;
    }

    public void setIncludedRepos(List<String> includedRepos) {
        this.includedRepos = includedRepos;
    }

    public List<String> getExcludedRepos() {
        return excludedRepos;
    }

    public void setExcludedRepos(List<String> excludedRepos) {
        this.excludedRepos = excludedRepos;
    }

    public IndexerDescriptor toDescriptor() {
        final IndexerDescriptor desc = new IndexerDescriptor();
        desc.setCronExp(cronExp);
        desc.setEnabled(enabled);
        desc.setIncludedRepositories(new TreeSet<>(includedRepos));
        return desc;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCronExp() {
        return cronExp;
    }

    public void setCronExp(String cronExp) {
        this.cronExp = cronExp;
    }
}
