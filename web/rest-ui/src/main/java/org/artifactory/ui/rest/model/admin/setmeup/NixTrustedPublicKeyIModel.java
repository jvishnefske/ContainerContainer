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

package org.artifactory.ui.rest.model.admin.setmeup;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.rest.common.model.BaseModel;

/**
 * SetMeUp response for the Nix {@code trusted-public-keys} value.
 * <p>
 * Holds the space-separated line a Nix client must paste into {@code nix.conf}:
 * {@code trusted-public-keys = <alias>:<base64> [<alias2>:<base64> ...]}.
 * <p>
 * For a local Nix repo, the value is a single {@code <alias>:<base64>} token, or {@code null}
 * when no Ed25519 primary key pair is bound. For a virtual Nix repo, it is a space-separated
 * string of one token per readable local or smart-remote member; unreadable members are silently
 * omitted. For a smart remote Nix repo, contains the remote's own primary Ed25519 key token;
 * plain remote repos return {@code null}.
 *
 * @author Natalie Berni
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NixTrustedPublicKeyIModel extends BaseModel {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String trustedPublicKey;
}
