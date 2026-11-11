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

package org.artifactory.descriptor.security.oauth;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.xml.bind.annotation.XmlType;

/**
 * @author Gidi Shabat
 */
@XmlType(name = "oauthProvidersSettingsType",
        propOrder = {"name", "enabled", "providerType", "id", "secret", "apiUrl", "authUrl", "tokenUrl", "basicUrl",
                "domain", "central", "pkce", "useDefaultProxy"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@Getter
@Setter
@EqualsAndHashCode
public class OAuthProviderSettings implements Descriptor {
    @DiffKey
    @NotUnicode
    private String name;
    private Boolean enabled = false;
    @NotUnicode
    private String providerType;
    @NotUnicode
    private String id;
    @NotUnicode
    private String secret;
    @NotUnicode
    private String apiUrl;
    @NotUnicode
    private String authUrl;
    @NotUnicode
    private String tokenUrl;
    @NotUnicode
    private String basicUrl;
    @NotUnicode
    private String domain;

    private Boolean central = false;

    private Boolean pkce = false;

    private Boolean useDefaultProxy = false;
}
