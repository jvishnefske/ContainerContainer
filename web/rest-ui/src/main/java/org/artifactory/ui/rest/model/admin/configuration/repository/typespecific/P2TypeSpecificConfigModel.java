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

import com.google.common.collect.Lists;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.p2.P2Repo;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.util.JsonUtil;

import java.util.List;

import static java.util.Optional.ofNullable;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_FORCE_P2_AUTH;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_SUPPRESS_POM_CHECKS;

/**
 * @author Dan Feldman
 */
@SuppressWarnings({"squid:S00116", "squid:S00117"}) //UI Model used by UI
public class P2TypeSpecificConfigModel extends MavenTypeSpecificConfigModel {

    protected Boolean forceP2Authentication = DEFAULT_FORCE_P2_AUTH;

    //virtual
    private List<P2Repo> P2Repos;

    public P2TypeSpecificConfigModel() {
        this.suppressPomConsistencyChecks = DEFAULT_SUPPRESS_POM_CHECKS;
    }

    public List<P2Repo> getP2Repos() {
        return P2Repos;
    }

    public void setP2Repos(List<P2Repo> P2Repos) {
        this.P2Repos = P2Repos;
    }

    public Boolean isForceP2Authentication() {
        return forceP2Authentication;
    }

    public void setForceP2Authentication(Boolean forceP2Authentication) {
        this.forceP2Authentication = forceP2Authentication;
    }

    @Override
    public Boolean getSuppressPomConsistencyChecks() {
        return this.suppressPomConsistencyChecks;
    }

    @Override
    public void setSuppressPomConsistencyChecks(Boolean suppressPomConsistencyChecks) {
        this.suppressPomConsistencyChecks = suppressPomConsistencyChecks;
    }

    @Override
    public void validateLocalTypeSpecific() {
        setForceP2Authentication(ofNullable(forceP2Authentication).orElse(DEFAULT_FORCE_P2_AUTH));
    }

    @Override
    public void validateRemoteTypeSpecific() {
        setForceP2Authentication(ofNullable(forceP2Authentication).orElse(DEFAULT_FORCE_P2_AUTH));
        setSuppressPomConsistencyChecks(ofNullable(getSuppressPomConsistencyChecks())
                .orElse(DEFAULT_SUPPRESS_POM_CHECKS));
        super.validateRemoteTypeSpecific();
    }

    @Override
    public void validateVirtualTypeSpecific(AddonsManager addonsManager) {
        setForceP2Authentication(ofNullable(forceP2Authentication).orElse(DEFAULT_FORCE_P2_AUTH));
        setP2Repos(ofNullable(getP2Repos()).orElse(Lists.newArrayList()));
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.P2;
    }

    @Override
    public String getUrl() {
        return null;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
