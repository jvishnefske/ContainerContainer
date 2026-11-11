/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
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

package org.artifactory.api.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * @author Lior Gur
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProxyModel {
    private String key;
    private String host;
    private int port;
    private String username;
    private String password;
    @JsonProperty("nt_host")
    private String ntHost;
    private String domain;
    @JsonProperty("platform_default")
    private boolean platformDefault;
    @JsonProperty("redirected_to_hosts")
    private String redirectedToHosts;
    private Set<String> services;

    public ProxyModel(ProxyDescriptor proxyDescriptor) {
        this.platformDefault = proxyDescriptor.isPlatformDefault();
        this.domain = proxyDescriptor.getDomain();
        this.host = proxyDescriptor.getHost();
        this.key = proxyDescriptor.getKey();
        this.ntHost = proxyDescriptor.getNtHost();
        this.password = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), proxyDescriptor.getPassword());
        this.port = proxyDescriptor.getPort();
        this.redirectedToHosts = proxyDescriptor.getRedirectedToHosts();
        this.username = proxyDescriptor.getUsername();
        this.services = proxyDescriptor.getServicesList();
    }
}
