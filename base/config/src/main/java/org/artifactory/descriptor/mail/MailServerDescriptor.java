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

package org.artifactory.descriptor.mail;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.util.UrlValidator;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.ws.rs.BadRequestException;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * The descriptor of the mail server configuration
 *
 * @author Fred Simon
 */
@Slf4j
@XmlType(name = "MailServerType", propOrder = {"enabled", "host", "port", "username", "password", "from",
        "subjectPrefix", "tls", "ssl", "artifactoryUrl"}, namespace = Descriptor.NS)
@GenerateDiffFunction
public class MailServerDescriptor implements Descriptor {

    @XmlElement(defaultValue = "true")
    private boolean enabled = true;
    @NotUnicode
    private String host;
    private int port = 25;
    @NotUnicode
    private String username;
    @NotUnicode
    private String password;
    @NotUnicode
    private String from;
    @NotUnicode
    private String subjectPrefix = "[JFrog]";
    @NotUnicode
    private String artifactoryUrl;
    private boolean tls;
    private boolean ssl;
    @NotUnicode
    public static final String MAIL_BASE_URL_NOT_CONFIGURED_LINK = "/artifactory_url_not_set_in_mail_config";
    @NotUnicode
    public static final String MAIL_BASE_URL_NOT_CONFIGURED_TITLE = "\"Artifactory Url not set in mail config\"";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubjectPrefix() {
        return subjectPrefix;
    }

    public void setSubjectPrefix(String subjectPrefix) {
        this.subjectPrefix = subjectPrefix;
    }

    public boolean isTls() {
        return tls;
    }

    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    /**
     * Artifactory URL that will be used <b>exclusively<b/> in <b>EMAILS ONLY!<b/>
     *
     * @return The Artifactory URL.
     */
    public String getArtifactoryUrl() {
        return artifactoryUrl;
    }

    public void setArtifactoryUrl(String artifactoryUrl) {
        if (isUrlValid(artifactoryUrl)) {
            this.artifactoryUrl = artifactoryUrl;
        } else {
            throw new BadRequestException("Artifactory url is in illegal form.");
        }
    }

    /**
     * Validates URL using UrlValidator.
     *
     * @param urlBase url to validate.
     * @throws BadRequestException if URL is in illegal form.
     * @see org.artifactory.util.UrlValidator
     */
    private boolean isUrlValid(String urlBase) {
        if (StringUtils.isBlank(urlBase)) {return true;}
        UrlValidator urlValidator = new UrlValidator(false);
        try {
            urlValidator.validate(urlBase);
        } catch (UrlValidator.UrlValidationException e) {
            log.debug("Failed to update mail server because Artifactory url is in invalid form.", e);
            return false;
        }
        return true;
    }
}
