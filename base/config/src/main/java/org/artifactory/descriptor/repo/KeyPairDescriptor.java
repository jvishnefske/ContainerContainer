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

package org.artifactory.descriptor.repo;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import java.util.List;

/**
 * @author Yuval Reches
 */
@XmlType(name = "KeyPairType",
        propOrder = {"key", "alias", "vaultType", "keyType", "privateKey", "publicKey", "passphrase"})
@GenerateDiffFunction
@Getter
@Setter
public class KeyPairDescriptor implements Descriptor {

    public static final String RSA_PUBLIC_KEY_EXTENSION = ".rsa.pub";
    public static final String GPG_PUBLIC_KEY_EXTENSION = ".gpg.pub";
    public static final String RSA_TYPE = "RSA";
    public static final String GPG_TYPE = "GPG";
    public static final String ECDSA_TYPE = "ECDSA";
    public static final String ED25519_TYPE = "ED25519";
    public static final String VAULT = "vault";
    public static final String DEFAULT_GPG_KEY = "default-gpg-key";
    public static final String DEFAULT_LIFECYCLE_GPG_KEY = "default-lifecycle-key";
    public static final String DEFAULT_RSA_KEY = "default-rsa-key";
    public static final List<RepoType> GPG_REPO_TYPES = List.of(RepoType.Debian, RepoType.YUM, RepoType.Opkg);

    @XmlID
    @XmlElement(required = true)
    @DiffKey
    @NotUnicode
    private String key;

    /**
     * To be used a public key filename when downloading it via REST API
     */
    @XmlElement(required = true)
    @NotUnicode
    private String alias;

    @XmlElement
    @NotUnicode
    private String vaultType;

    @XmlElement(required = true)
    @NotUnicode
    private String keyType;

    /**
     * Might be encrypted
     */
    @XmlElement
    @NotUnicode
    private String privateKey = StringUtils.EMPTY;

    @XmlElement
    @NotUnicode
    private String publicKey = StringUtils.EMPTY;

    /**
     * Might be encrypted
     */
    @NotUnicode
    private String passphrase;

    // Internal flag for vault status
    @XmlTransient
    private Boolean unavailable = false;

    // Internal storage for vault key
    @XmlTransient
    private String vaultKey = "";

    public String getAliasWithExtension() {
        return String.format("%s.%s.pub", alias, getKeyType().toLowerCase());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        KeyPairDescriptor that = (KeyPairDescriptor) o;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
