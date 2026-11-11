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

package org.artifactory.util.bearer;

import org.apache.http.HttpRequest;
import org.apache.http.auth.Credentials;
import org.apache.http.protocol.HttpContext;
import org.artifactory.addon.ansible.AnsibleAuthHelper;
import org.artifactory.addon.ansible.SsoRemoteTokenProvider;
import org.artifactory.addon.common.DefaultRemoteTokenProvider;
import org.artifactory.addon.docker.DockerPreemptiveAuthHelper;
import org.artifactory.addon.docker.rest.DockerRemoteTokenProvider;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.jfrog.client.http.auth.BearerScheme;
import org.jfrog.client.http.auth.TokenProvider;

import java.util.Optional;

/**
 * Bearer authentication scheme as defined in RFC 2617
 *
 * @author Shay Yaakov
 */
public class ArtifactoryBearerScheme extends BearerScheme {

    private final String repoKey;

    public ArtifactoryBearerScheme(String repoKey) {
        super(getTokenProviderByRepoKey(repoKey), ConstantValues.bearerAuthMaxAllowedRetries.getInt());
        this.repoKey = repoKey;
    }

    public ArtifactoryBearerScheme(String repoKey, RepoType repoType) {
        super(getTokenProviderByRepoType(repoKey, repoType), ConstantValues.bearerAuthMaxAllowedRetries.getInt());
        this.repoKey = repoKey;
    }

    @Override
    public String getToken(Credentials dummyCredentials, HttpRequest request, HttpContext context) {
        return tokenProvider.getToken(getParameters(),
                request.getRequestLine().getMethod(),
                request.getRequestLine().getUri(),
                repoKey);
    }

    private static TokenProvider getTokenProviderByRepoKey(String repoKey) {
        RepoType type = Optional.ofNullable(ContextHelper.get().beanForType(RepositoryService.class)
                        .repoDescriptorByKey(repoKey))
                .orElseThrow(() -> new RuntimeException("No such repository " + repoKey))
                .getType();
        return getTokenProviderByRepoType(repoKey, type);
    }

    private static TokenProvider getTokenProviderByRepoType(String repoKey, RepoType type) {
        if (type.isDockerGroup()) {
            return getTokenProviderForDockerGroup(repoKey);
        }
        if (RepoType.Ansible == type) {
            return getTokenProviderForAnsible(repoKey);
        }
        return ContextHelper.get().beanForType(DefaultRemoteTokenProvider.class);
    }

    private static TokenProvider getTokenProviderForAnsible(String repoKey) {
        if (AnsibleAuthHelper.shouldUseSsoAuth(repoKey)) {
            return ContextHelper.get().beanForType(SsoRemoteTokenProvider.class);
        }
        return ContextHelper.get().beanForType(DefaultRemoteTokenProvider.class);
    }

    private static TokenProvider getTokenProviderForDockerGroup(String repoKey) {
        if (DockerPreemptiveAuthHelper.shouldUsePreemptiveAuth(repoKey)) {
            return ContextHelper.get().beanForType(DefaultRemoteTokenProvider.class);
        }
        return ContextHelper.get().beanForType(DockerRemoteTokenProvider.class);
    }
}
