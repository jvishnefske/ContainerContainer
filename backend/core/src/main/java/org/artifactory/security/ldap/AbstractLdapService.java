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

package org.artifactory.security.ldap;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.AuthenticationException;
import org.springframework.ldap.BadLdapGrammarException;
import org.springframework.ldap.CommunicationException;
import org.springframework.ldap.InvalidNameException;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.artifactory.security.ldap.ArtifactoryLdapAuthenticator.LDAP_CONNECT_TIMEOUT_DEFAULT_MILLIS;

/**
 * @author Tomer Cohen
 */
public class AbstractLdapService {
    private static final Logger log = LoggerFactory.getLogger(AbstractLdapService.class);

    private static final long CONNECT_TIMEOUT =
            Long.parseLong(ArtifactoryHome.get().getArtifactoryProperties().getProperty(
                    "artifactory.security.ldap.connect.timeoutMillis", LDAP_CONNECT_TIMEOUT_DEFAULT_MILLIS));

    protected void handleException(Exception e, BasicStatusHolder status, String username,
            boolean isSearchAndBindActive, long authenticateTimeMillis) {

        log.debug("LDAP connection test failed with exception", e);
        if (e instanceof CommunicationException) {
            sleepIfNecessary(authenticateTimeMillis);
            status.error("Error connecting to the LDAP server.", e, log);
        } else if (e instanceof NameNotFoundException) {
            status.error("Server failed to parse the request: " +
                    ((NameNotFoundException) e).getMostSpecificCause().getMessage(), e, log);
        } else if (e instanceof InvalidNameException) {
            status.error("Server failed to parse the request: " +
                    ((InvalidNameException) e).getMostSpecificCause().getMessage(), e, log);
        } else if (e instanceof AuthenticationException) {
            if (isSearchAndBindActive) {
                status.warn("LDAP authentication failed for " + username +
                        ". Note: you have configured direct user binding " +
                        "and manager-based search, which are usually mutually exclusive. For AD leave the User DN " +
                        "Pattern field empty.", e, log);
            } else {
                status.error("Authentication failed. Probably a wrong manager dn or manager password", e,
                        log);
            }
        } else if (e instanceof BadCredentialsException || e instanceof UsernameNotFoundException) {
            status.error("Failed to authenticate user: '" + username + "'", e, log);
        } else if (e instanceof BadLdapGrammarException) {
            status.error("Failed to parse R\\DN", e, log);
        } else {
            sleepIfNecessary(authenticateTimeMillis);
            String message = "Error connecting to the LDAP server.";
            log.error(message, e);
            status.error(message, log);
        }
    }

    /**
     * Create LDAP template to be used for performing LDAP queries.
     *
     * @param settings The LDAP settings with which to create the LDAP template.
     * @return The LDAP template.
     */
    public LdapTemplate createLdapTemplate(LdapSetting settings) {
        LdapContextSource securityContext =
                ArtifactoryLdapAuthenticator.createSecurityContext(settings, ArtifactoryHome.get());
        return createLdapTemplate(securityContext);
    }

    /**
     * Create LDAP template to be used for performing LDAP queries on a domain level.
     *
     * @param settings The LDAP settings with which to create the LDAP template.
     * @return The LDAP template.
     */
    public LdapTemplate createLdapTemplateForDomain(LdapSetting settings) {
        LdapContextSource securityContext =
                ArtifactoryLdapAuthenticator.createSecurityContextForDomain(settings, ArtifactoryHome.get());
        return createLdapTemplate(securityContext);
    }

    /**
     * Added to obfuscate the timeout for testing connection of a wrong yet listening port.
     * An attacker was able to use the ldap connection test in order to check for open ports within the cluster.
     * Adding the time to reach the timeout on any failed request (even one that does not reach it) help against this
     * vulnerability
     * See https://www.jfrog.com/jira/browse/RTFACT-23209
     */
    private void sleepIfNecessary(long connectTime) {
        long timeToSleep = CONNECT_TIMEOUT - connectTime;
        if (timeToSleep > 0) {
            try {
                log.debug("Going to sleep. connect time was:{}, time to sleep is:{}", connectTime, timeToSleep);
                Thread.sleep(timeToSleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private LdapTemplate createLdapTemplate(LdapContextSource securityContext) {
        LdapTemplate ldapTemplate = new LdapTemplate(securityContext);
        ldapTemplate.setIgnorePartialResultException(true);
        return ldapTemplate;
    }
}
