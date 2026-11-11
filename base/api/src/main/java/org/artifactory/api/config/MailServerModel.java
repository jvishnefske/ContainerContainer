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

package org.artifactory.api.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.mail.MailServerDescriptor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The descriptor of the mail server configuration
 *
 * @author Lior Gur
 */
@Setter
@NoArgsConstructor
public class MailServerModel {

    @Getter
    private boolean enabled;
    @Getter
    private String host;
    @Getter
    private int port;
    @Getter
    private String username;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String password;
    @Getter
    private String from;
    @Getter
    @JsonProperty("subject_prefix")
    private String subjectPrefix;
    /**
     * -- GETTER --
     *  Artifactory URL that will be used <b>exclusively<b/> in <b>EMAILS ONLY!<b/>
     *
     * @return The Artifactory URL.
     */
    @Getter
    @JsonProperty("artifactory_url")
    private String artifactoryUrl;
    @Getter
    private boolean tls;
    @Getter
    private boolean ssl;

    public MailServerModel(MailServerDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        this.enabled = descriptor.isEnabled();
        this.host = descriptor.getHost();
        this.port = descriptor.getPort();
        this.username = descriptor.getUsername();
        this.password = descriptor.getPassword();
        this.from = descriptor.getFrom();
        this.subjectPrefix = descriptor.getSubjectPrefix();
        this.artifactoryUrl = descriptor.getArtifactoryUrl();
        this.tls = descriptor.isTls();
        this.ssl = descriptor.isSsl();
    }

    public String getPassword() {
        return CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), password);
    }
}
