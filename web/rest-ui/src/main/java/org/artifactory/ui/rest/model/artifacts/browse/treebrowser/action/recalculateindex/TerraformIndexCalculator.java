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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.recalculateindex;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.artifactory.addon.terraform.TerraformAddon;
import org.artifactory.common.profile.PackageProfile;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;

import java.util.Arrays;

/**
 * @author BarakH
 */
@JsonTypeName("Terraform")
public class TerraformIndexCalculator extends BaseIndexCalculator {

    private final boolean legacyProfile;

    public TerraformIndexCalculator() {
        legacyProfile = !Arrays.asList(environment.getActiveProfiles()).contains(PackageProfile.NEW_TERRAFORM_IMPL);
    }

    @Override
    public void calculateIndex() throws Exception {
        if (legacyProfile) {
            TerraformAddon terraformAddon = addonsManager.addonByType(TerraformAddon.class);
            String repoKey = getRepoKey();
            if (terraformAddon != null && assertRepoType(repoKey, RepoType.Terraform)) {
                terraformAddon.reindexTerraform(repoKey);
            }
        } else {
            packageResourceService.reindex(PackageType.TERRAFORM, getRepoKey(), null);
        }
    }
}