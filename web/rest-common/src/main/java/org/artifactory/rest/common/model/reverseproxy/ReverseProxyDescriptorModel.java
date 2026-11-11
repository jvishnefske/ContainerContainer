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

package org.artifactory.rest.common.model.reverseproxy;

import lombok.Data;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.ReverseProxyMethod;
import org.artifactory.descriptor.repo.WebServerType;
import org.artifactory.util.TomcatListeningDetector;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * @author Chen Keinan
 */
@Data
@JsonPropertyOrder(value = {"key", "webServerType", "artifactoryAppContext", "publicAppContext",
        "serverName", "serverNameExpression", "artifactoryServerName", "artifactoryPort", "routerPort",
        "sslCertificate", "sslKey", "dockerReverseProxyMethod", "useHttps", "useHttp", "httpsPort", "httpPort"})
public class ReverseProxyDescriptorModel {
    // The name of the default module
    private String key = "direct";
    private WebServerType webServerType;
    private String artifactoryAppContext;
    private String publicAppContext;
    private String serverName;
    private String serverNameExpression;
    private String artifactoryServerName;
    private String upStreamName;
    private int artifactoryPort = TomcatListeningDetector.detectMainListeningPort();
    private int routerPort = ArtifactoryHome.get().getSysConfig().helper().getRouterExternalPort();
    private String sslCertificate;
    private String sslKey;
    private ReverseProxyMethod dockerReverseProxyMethod;
    private boolean useHttps;
    private boolean useHttp;
    @JsonProperty("httpsPort")
    private int sslPort = 443;
    private int httpPort = 80;
    private ReverseProxyRepositories reverseProxyRepositories;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ReverseProxyDescriptorModel that = (ReverseProxyDescriptorModel) o;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
