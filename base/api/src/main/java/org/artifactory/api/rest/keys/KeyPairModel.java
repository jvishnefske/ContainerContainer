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

package org.artifactory.api.rest.keys;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

import static org.artifactory.descriptor.repo.KeyPairDescriptor.RSA_TYPE;
import static org.jfrog.common.ArgUtils.requireMatches;
import static org.jfrog.common.ArgUtils.requireNonBlank;
import static org.jfrog.common.ArgUtils.requireSatisfies;

/**
 * @author Yuval Reches
 */
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class KeyPairModel {

    private String pairName;
    private String pairType;
    private String vaultKey;
    private String alias;

    private String privateKey;
    private String publicKey;
    private String passphrase;

    private VaultPath vaultPrivateKey;
    private VaultPath vaultPublicKey;
    private VaultPath vaultPassphrase;

    private Boolean unavailable;

    public void validate() {
        requireNonBlank(pairName, "key pair name is required");
        requireMatches(pairName, Pattern.compile("[a-zA-Z0-9_\\-]+"),
                "invalid key pair name, it cannot contain special characters");
        requireNonBlank(pairType, "key pair type is required");
        requireNonBlank(alias, "key pair alias is required");
        requireMatches(alias, Pattern.compile("[^\\s]+"), "invalid key pair alias, it cannot contain spaces");

        if (vaultKey != null) {
            requireSatisfies(vaultPublicKey,
                    this::validateVaultPath, "key pair public key is required");
            requireSatisfies(vaultPrivateKey,
                    this::validateVaultPath, "key pair private key is required");
        } else if (RSA_TYPE.equals(pairType)) {
            requireNonBlank(privateKey, "key pair private key is required");
            requireNonBlank(publicKey, "key pair public key is required");
        }
    }

    private boolean validateVaultPath(VaultPath key) {
        return key != null
                && StringUtils.isNotEmpty(key.getPath())
                && StringUtils.isNotEmpty(key.getKey());
    }
}
