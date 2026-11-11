/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 JFrog Ltd.
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

package org.artifactory.ui.rest.service.setmeup;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.setmeup.NixTrustedPublicKeyIModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the Nix {@code trusted-public-keys} value for the given repo's primary Ed25519
 * key pair(s), ready to embed in the SetMeUp client snippet.
 * <p>
 * Behavior by {@code rclass}:
 * <ul>
 *   <li><b>local</b> — returns the bound primary Ed25519 keypair as {@code <alias>:<base64>},
 *       or {@code null} when no Ed25519 key is configured.</li>
 *   <li><b>virtual</b> — walks the virtual's member keys; for each <em>readable</em> local or
 *       smart-remote member (per {@link AuthorizationService#canRead}), includes its primary
 *       Ed25519 key. Multiple keys are joined with a space. Duplicates collapse.</li>
 *   <li><b>smart remote</b> — smart remotes never carry a key pair; resolves by extracting
 *       the repo key from the remote's URL and looking up that local repo on this Artifactory
 *       instance to obtain its primary Ed25519 key.</li>
 *   <li><b>plain remote</b> — returns {@code null}.</li>
 * </ul>
 * <p>
 * Returns {@code 404} when the repo is not Nix, {@code 400} for an invalid repo key, and a
 * payload with a {@code null} key when the repo exists but no readable member exposes a key.
 *
 * @author Natalie Berni
 */
@Component
public class GetNixTrustedPublicKeyPerRepoService implements RestService<String> {

    private final RepositoryConfigService repositoryConfigService;
    private final AuthorizationService authorizationService;
    private final CentralConfigService centralConfigService;

    @Autowired
    public GetNixTrustedPublicKeyPerRepoService(RepositoryConfigService repositoryConfigService,
            AuthorizationService authorizationService,
            CentralConfigService centralConfigService) {
        this.repositoryConfigService = repositoryConfigService;
        this.authorizationService = authorizationService;
        this.centralConfigService = centralConfigService;
    }

    // RestService interface declares a raw RestResponse parameter; parameterising the override
    // produces a name-clash with the same erasure, so we suppress S3740 here.
    @Override
    @SuppressWarnings("java:S3740")
    public void execute(ArtifactoryRestRequest<String> request, RestResponse response) {
        String repoKey = request.getPathParamByKey("repoKey");
        RepoConfig config = repoKey == null ? null : repositoryConfigService.getRepoConfigByKey(repoKey);
        if (config == null) {
            response.error(repoKey == null ? "No repo param found" : "No such repo '" + repoKey + "'");
            return;
        }
        if (config.getPackageType() != RepoType.Nix) {
            response.error("Repo '" + repoKey + "' is not a Nix repository");
            return;
        }
        String trustedPublicKey = collectKeys(config);
        response.iModel(new NixTrustedPublicKeyIModel(trustedPublicKey));
    }

    /**
     * Dispatch on rclass. Virtual → space-joined union of readable members' keys.
     * Plain remote → null. Smart remote → upstream local repo key (by URL lookup).
     * Local → primary keypair of the repo itself, or null.
     */
    private String collectKeys(RepoConfig config) {
        if (config instanceof VirtualRepoConfig virtual) {
            List<String> keys = collectVirtualMemberKeys(virtual);
            return keys.isEmpty() ? null : String.join(" ", keys);
        }
        if (config instanceof RemoteRepoConfig remote) {
            if (!remote.isSmart()) {
                return null;
            }
            return resolveSmartRemoteKey(remote).orElse(null);
        }
        return resolveKeyPair(config).orElse(null);
    }

    /**
     * Walk the virtual's member repo keys, keeping only those the caller has READ on. Local
     * and smart-remote members contribute their primary Ed25519 key. Nested virtuals are
     * recursed. Insertion order is preserved via {@link LinkedHashSet}; equal entries collapse.
     */
    private List<String> collectVirtualMemberKeys(VirtualRepoConfig virtual) {
        Set<String> seen = new LinkedHashSet<>();
        for (String memberKey : virtual.getRepoTypeConfig().getRepositoryRefs()) {
            if (!canReadRepo(memberKey)) {
                continue;
            }
            RepoConfig member = repositoryConfigService.getRepoConfigByKey(memberKey);
            if (member instanceof VirtualRepoConfig nestedVirtual) {
                seen.addAll(collectVirtualMemberKeys(nestedVirtual));
            } else if (member instanceof RemoteRepoConfig remote && remote.isSmart()) {
                resolveSmartRemoteKey(remote).ifPresent(seen::add);
            } else if (member instanceof LocalRepoConfig) {
                resolveKeyPair(member).ifPresent(seen::add);
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * Smart remotes never carry a key pair — the key belongs to the upstream local repo.
     * Extracts the repo key from the remote's URL and looks it up on this Artifactory instance.
     */
    private Optional<String> resolveSmartRemoteKey(RemoteRepoConfig remote) {
        String upstreamKey = NixSetMeUpUtils.extractRepoKeyFromUrl(remote.getUrl());
        if (upstreamKey == null) {
            return Optional.empty();
        }
        RepoConfig upstream = repositoryConfigService.getRepoConfigByKey(upstreamKey);
        if (!(upstream instanceof LocalRepoConfig)) {
            return Optional.empty();
        }
        return resolveKeyPair(upstream);
    }

    private Optional<String> resolveKeyPair(RepoConfig config) {
        String ref = config.getSecurityConfig().getPrimaryKeyPairRef();
        if (StringUtils.isBlank(ref)) {
            return Optional.empty();
        }
        KeyPairDescriptor keyPair = centralConfigService.getDescriptor().getKeyPair(ref);
        return NixSetMeUpUtils.toNixTrustedPublicKey(keyPair);
    }

    private boolean canReadRepo(String repoKey) {
        return authorizationService.canRead(RepoPathFactory.create(repoKey, ""));
    }
}
