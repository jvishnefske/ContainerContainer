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

package org.artifactory.descriptor.repo;

import org.apache.commons.compress.utils.Sets;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.client.http.model.ProxyConfig;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.annotation.Nullable;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlType;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author Fred Simon
 */
@XmlType(name = "ProxyType",
        propOrder = {"key", "host", "port", "username", "password", "ntHost", "domain", "platformDefault",
                "redirectedToHosts", "services"})
@GenerateDiffFunction
public class ProxyDescriptor implements Descriptor {

    @XmlID
    @XmlElement(required = true)
    @DiffKey
    @NotUnicode
    private String key;

    @XmlElement(required = true)
    @NotUnicode
    private String host;

    @XmlElement(required = true)
    private int port;

    @NotUnicode
    private String username;

    @NotUnicode
    private String password;

    @NotUnicode
    private String ntHost;

    @NotUnicode
    private String domain;
    /**
     * All services will be using this proxy, Artifactory included
     */
    private boolean platformDefault;
    /**
     * New line or comma separated host names that the proxy might redirect requests to.
     */
    @NotUnicode
    private String redirectedToHosts;
    /**
     * Comma separated services that the proxy is used as a default for.
     * See possible values at {@link org.artifactory.descriptor.security.ProxyServices#VALID_PROXY_SERVICES}
     */
    @NotUnicode
    private String services;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
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

    public String getNtHost() {
        return ntHost;
    }

    public void setNtHost(String ntHost) {
        this.ntHost = ntHost;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public boolean isPlatformDefault() {
        return platformDefault;
    }

    public void setPlatformDefault(boolean platformDefault) {
        this.platformDefault = platformDefault;
    }

    /**
     * Returns new line or comma separated host names that the proxy might redirect requests to.
     * Use {@link ProxyDescriptor#getRedirectedToHostsList()} to get a list of host names.
     */
    @Nullable
    public String getRedirectedToHosts() {
        return redirectedToHosts;
    }

    @Nullable
    public String[] getRedirectedToHostsList() {
        // split by newline, space, comma or semi-colon
        return StringUtils.split(redirectedToHosts, "\n,; ");
    }

    public void setRedirectedToHosts(@Nullable String redirectedToHosts) {
        this.redirectedToHosts = StringUtils.trim(redirectedToHosts);
    }

    public String getServices() {
        return services;
    }

    public void setServices(@Nullable String services) {
        this.services = StringUtils.trim(services);
    }

    public Set<String> getServicesList() {
        // split by newline, space, comma or semi-colon
        String[] servicesList = StringUtils.split(getServices(), "\n,; ");
        if (servicesList != null) {
            return Sets.newHashSet(servicesList);
        }
        return Collections.emptySet();
    }

    /**
     * Password is given *encrypted* here, caller is responsible for decrypting it if required!
     */
    public ProxyConfig toProxyConfig() {
        ProxyConfig proxy = new ProxyConfig();
        proxy.setHost(host);
        proxy.setPort(port);
        proxy.setUsername(username);
        proxy.setPassword(password);
        proxy.setNtHost(ntHost);
        proxy.setDomain(domain);
        proxy.setRedirectedToHosts(redirectedToHosts);
        return proxy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProxyDescriptor that = (ProxyDescriptor) o;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    public boolean diff(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProxyDescriptor)) {
            return false;
        }
        ProxyDescriptor that = (ProxyDescriptor) o;
        return getPort() == that.getPort() &&
                isPlatformDefault() == that.isPlatformDefault() &&
                Objects.equals(getKey(), that.getKey()) &&
                Objects.equals(getHost(), that.getHost()) &&
                Objects.equals(getUsername(), that.getUsername()) &&
                Objects.equals(getPassword(), that.getPassword()) &&
                Objects.equals(getNtHost(), that.getNtHost()) &&
                Objects.equals(getDomain(), that.getDomain()) &&
                Objects.equals(getRedirectedToHosts(), that.getRedirectedToHosts()) &&
                Objects.equals(getServices(), that.getServices());
    }
}
