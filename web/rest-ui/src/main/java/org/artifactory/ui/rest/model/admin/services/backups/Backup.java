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

package org.artifactory.ui.rest.model.admin.services.backups;

import com.fasterxml.jackson.annotation.JsonFilter;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.model.RestSpecialFields;
import org.artifactory.rest.common.service.IgnoreSpecialFields;
import org.artifactory.rest.common.util.JsonUtil;
import org.jfrog.common.config.diff.DiffIgnore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.getExcludedFromImportSystemRepos;

/**
 * @author Chen Keinan
 */
@JsonFilter("exclude fields")
@IgnoreSpecialFields(value = {"excludeRepos", "dir", "retentionPeriodHours",
        "excludedRepositories", "sendMailOnError", "excludeBuilds", "incremental", "precalculate",
        "exportMissionControl"})
public class Backup extends BackupDescriptor implements RestModel, RestSpecialFields {

    private List<String> excludeRepos;
    private List<String> includeRepos;
    @DiffIgnore
    private boolean isEdit = false;
    @SuppressWarnings("unused")
    private boolean incremental;
    @SuppressWarnings("unused")
    private boolean precalculate;

    Backup() {
    }

    public Backup(BackupDescriptor backupDescriptor, boolean isEdit) {
        if (backupDescriptor != null) {
            super.setCronExp(backupDescriptor.getCronExp());
            super.setEnabled(backupDescriptor.isEnabled());
            super.setKey(backupDescriptor.getKey());
            super.setExcludeNewRepositories(backupDescriptor.isExcludeNewRepositories());
            populateditFields(backupDescriptor, isEdit);
        }
    }

    /**
     * populate extra fields require for edit
     *
     * @param backupDescriptor - back up descriptor data
     * @param isEdit           - if true , populate fields for edit
     */
    void populateditFields(BackupDescriptor backupDescriptor, boolean isEdit) {
        if (isEdit) {
            this.isEdit = isEdit;
            super.setRetentionPeriodHours(backupDescriptor.getRetentionPeriodHours());
            super.setCreateArchive(backupDescriptor.isCreateArchive());
            super.setDir(backupDescriptor.getDir());
            super.setSendMailOnError(backupDescriptor.isSendMailOnError());
            super.setCreateArchive(backupDescriptor.isCreateArchive());
            super.setPrecalculate(backupDescriptor.isPrecalculate());
            super.setExportMissionControl(backupDescriptor.isExportMissionControl());
        }
        populateExcludeRepo(backupDescriptor.getExcludedRepositories());
        populateIncludeRepo();
    }

    /**
     * populate exclude Real repo keys
     *
     * @param realRepoKeys - real repo keys
     */
    void populateExcludeRepo(List<String> realRepoKeys) {
        excludeRepos = new ArrayList<>(realRepoKeys);
    }

    /**
     * populate exclude Real repo keys
     */
    void populateIncludeRepo() {
        List<String> repos = new ArrayList<>();
        RepositoryService repositoryService = ContextHelper.get().beanForType(RepositoryService.class);
        List<LocalConfig> localRepoConfigs = repositoryService.getLocalRepoConfigsIncludingInfoRepos();
        List<FederatedRepoConfig> federatedRepoConfigs = repositoryService.getFederatedRepoConfigs();
        List<RemoteRepoConfig> remoteRepoConfigs = repositoryService.getRemoteRepoConfigs();
        List<ReleaseBundleRepoConfig> releaseBundleRepoConfigs = repositoryService.getReleaseBundleRepoConfigs();

        Function<RepoConfig, String> getKeyFunc = RepoConfig::getKey;

        repos.addAll(localRepoConfigs.stream().map(getKeyFunc).collect(Collectors.toSet()));
        repos.addAll(remoteRepoConfigs.stream().map(getKeyFunc).collect(Collectors.toSet()));
        repos.addAll(releaseBundleRepoConfigs.stream().map(getKeyFunc).collect(Collectors.toSet()));
        repos.addAll(federatedRepoConfigs.stream().map(getKeyFunc).collect(Collectors.toSet()));

        repos.removeIf(repoKey -> excludeRepos.contains(repoKey)
                || getExcludedFromImportSystemRepos().contains(repoKey));
        includeRepos = repos;
    }

    public List<String> getIncludeRepos() {
        return includeRepos;
    }

    public void setIncludeRepos(List<String> includeRepos) {
        this.includeRepos = includeRepos;
    }

    public List<String> getExcludeRepos() {
        return excludeRepos;
    }

    /**
     * get list of repo key and build list of Real repo descriptors
     *
     * @param excludeRepos - list of repos key to exclude
     */
    public void setExcludeRepos(List<String> excludeRepos) {
        this.excludeRepos = excludeRepos;
        RepositoryConfigService repositoryConfigService =
                ContextHelper.get().beanForType(RepositoryConfigService.class);
        List<String> excludedRepoKeys =
                repositoryConfigService.getRepoConfigsByTypes(Set.of(LOCAL, FEDERATED, REMOTE, RELEASE_BUNDLE)).stream()
                        .map(RepoConfig::getKey)
                        .filter(excludeRepos::contains).toList();
        super.setExcludedRepositories(excludedRepoKeys);
    }

    public String toString() {
        return JsonUtil.jsonToStringIgnoreSpecialFields(this);
    }

    @Override
    public boolean ignoreSpecialFields() {
        return !isEdit;
    }
}
