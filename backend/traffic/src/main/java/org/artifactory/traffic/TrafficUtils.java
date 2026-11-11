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

package org.artifactory.traffic;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.request.Request;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.ServiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.artifactory.addon.xray.XrayAddon.ARTIFACTORY_XRAY_USER;
import static org.artifactory.api.request.ArtifactoryHeaders.ARTIFACTORY_ARCHIVE;
import static org.artifactory.api.request.ArtifactoryHeaders.PARAM_REPLICATION_DOWNLOAD_REQUEST;
import static org.artifactory.api.security.access.UserTokenSpec.ARTIFACTORY_FEDERATION_USER_PREFIX;
import static org.artifactory.api.security.access.UserTokenSpec.TOKEN_PREFIX;

/**
 * Utility class for the traffic module
 *
 * @author Noam Tenne
 */
public abstract class TrafficUtils {

    private static final String XRAY_REPETITIVE = "XRAY_REPETITIVE";
    private static final String BA_BILLING_LOGSHIPPING = "BA_BILLING_LOGSHIPPING";
    private static final String XRAY_CREDIT = "XRAY_CREDIT";
    private static final String REPLICATION = "REPLICATION";
    private static final String SMART_REMOTE = "SMART_REMOTE";

    private static final Map<String, TrafficEntry.TrafficActor> ACTORS_MAP = new HashMap<>();
    private static final Logger log = LoggerFactory.getLogger(TrafficUtils.class);

    static {
        ACTORS_MAP.put(ServiceType.XRAY, TrafficEntry.TrafficActor.XRAY);
        ACTORS_MAP.put(ServiceType.XRAY_INDEXER, TrafficEntry.TrafficActor.XRAY);
        ACTORS_MAP.put(ServiceType.XRAY_PERSIST, TrafficEntry.TrafficActor.XRAY);
        ACTORS_MAP.put(ServiceType.XRAY_ANALYSIS, TrafficEntry.TrafficActor.XRAY);
        ACTORS_MAP.put(ServiceType.PIPELINES, TrafficEntry.TrafficActor.PIPELINES);
        ACTORS_MAP.put(ServiceType.REPLICATOR, TrafficEntry.TrafficActor.REPLICATOR);
        ACTORS_MAP.put(ServiceType.JF_CONNECT, TrafficEntry.TrafficActor.JFCONNECT);
        ACTORS_MAP.put(ServiceType.JF_LINK, TrafficEntry.TrafficActor.JFLINK);
        ACTORS_MAP.put(ServiceType.OBSERVABILITY, TrafficEntry.TrafficActor.OBSERVABILITY);
        ACTORS_MAP.put(XRAY_REPETITIVE, TrafficEntry.TrafficActor.XRAY_REPETITIVE);
        ACTORS_MAP.put(XRAY_CREDIT, TrafficEntry.TrafficActor.XRAY_CREDIT);
        ACTORS_MAP.put(BA_BILLING_LOGSHIPPING, TrafficEntry.TrafficActor.BA_BILLING_LOGSHIPPING);
        ACTORS_MAP.put(REPLICATION, TrafficEntry.TrafficActor.REPLICATION);
        ACTORS_MAP.put(SMART_REMOTE, TrafficEntry.TrafficActor.SMART_REMOTE);
    }

    private TrafficUtils() {
        // utility class
    }

    /**
     * Performs a comparison that checks if the date to compare is equals or before the date we want to compare to
     *
     * @param to Compare Date to compare
     * @param to Compare to
     * @return boolean - If the date to compare is equals or before the date we compare to
     */
    public static boolean dateEqualsBefore(Date toCompare, Date to) {
        return toCompare.compareTo(to) < 1;
    }

    /**
     * Performs a comparison that checks if the date to compare is equals or after the date we want to compare to
     *
     * @param to Compare Date to compare
     * @param to Compare to
     * @return boolean - If the date to compare is equals or after the date we compare to
     */
    public static boolean dateEqualsAfter(Date toCompare, Date to) {
        return toCompare.compareTo(to) > -1;
    }

    public static TrafficEntry.TrafficActor getTrafficActor(String userAgent, String username,
            Authentication authentication) {
        String serviceType = getServiceType(authentication);
        TrafficEntry.TrafficActor trafficActor =
                ACTORS_MAP.getOrDefault(serviceType, getTrafficActor(userAgent, username, serviceType));
        log.trace("Traffic actor is {}", trafficActor);
        return trafficActor;
    }

    /**
     * Returns the traffic actor based on the user agent, username, authentication, and request.
     * NOTE: The order of the checks is important (don't change the order of the conditions):
     * 1. If the request is a replication request, it will return the REPLICATION actor.
     * 2. If the request is a smart remote request, it will return the SMART_REMOTE actor.
     * 3. If the authentication is an Access token, it will determine the actor based on the service type.
     * 4. If the user agent or username indicates a specific actor, it will return that actor.
     * 5. If none of the above conditions are met, it will return the USER actor.
     */
    public static TrafficEntry.TrafficActor getTrafficActorFromRequest(String userAgent, String username,
            Authentication authentication, Request artifactoryRequest) {
        String serviceType = getServiceType(authentication);
        TrafficEntry.TrafficActor trafficActor =
                ACTORS_MAP.getOrDefault(serviceType, getTrafficActor(userAgent, username, serviceType));
        if (isReplicationActor(artifactoryRequest)) {
            log.trace("Replication request, traffic actor is {}", REPLICATION);
            return ACTORS_MAP.getOrDefault(REPLICATION, getTrafficActor(userAgent, username, REPLICATION));
        }
        if (isSmartRemoteActor(artifactoryRequest)) {
            log.trace("Repository is a smart remote, traffic actor is {}", SMART_REMOTE);
            return ACTORS_MAP.getOrDefault(SMART_REMOTE, getTrafficActor(userAgent, username, SMART_REMOTE));
        }
        log.trace("Traffic actor is {}", trafficActor);
        return trafficActor;
    }

    private static boolean isReplicationActor(Request request) {
        return ConstantValues.trafficReplicationAddActorEnabled.getBoolean()
                && Boolean.parseBoolean(request.getParameter(PARAM_REPLICATION_DOWNLOAD_REQUEST));
    }

    private static boolean isSmartRemoteActor(Request request) {
        String repoKey = request.getRepoPath().getRepoKey();
        RemoteRepoConfig remoteRepoConfig = ContextHelper.get()
                .beanForType(RepositoryConfigService.class)
                .getRemoteRepoConfigByKey(repoKey);
        return remoteRepoConfig != null && remoteRepoConfig.isSmart();
    }

    public static TrafficEntry.TrafficActor getTrafficActor(String identifier, boolean xrayAuthenticationUser,
            Authentication authentication) {
        if (StringUtils.isNotBlank(identifier)) {
            if (xrayAuthenticationUser) {
                String serviceType = getServiceType(authentication);
                return ACTORS_MAP.getOrDefault(serviceType, TrafficEntry.TrafficActor.XRAY);
            }
            if (startsWith(identifier.toLowerCase(), "jfrog-replicator/")) {
                return TrafficEntry.TrafficActor.REPLICATOR;
            }
            if (startsWith(identifier.toLowerCase(), "observability")) {
                return TrafficEntry.TrafficActor.OBSERVABILITY;
            }
            if (startsWith(identifier.toLowerCase(), "ba-billing-logshipping")) {
                return TrafficEntry.TrafficActor.BA_BILLING_LOGSHIPPING;
            }
        }
        return TrafficEntry.TrafficActor.USER;
    }

    private static String getServiceType(Authentication authentication) {
        String result = "";
        if (authentication instanceof AccessTokenAuthentication accessTokenAuthentication) {
            String subject = accessTokenAuthentication.getAccessToken().getSubject();
            if (StringUtils.isNotBlank(subject)) {
                try {
                    ServiceId serviceId = ServiceId.fromFormattedName(subject);
                    String serviceType = serviceId.getServiceType();
                    if (ServiceType.XRAY.equals(serviceType)) {
                        serviceType = handleXraySubject(subject);
                    }
                    result = serviceType;
                    log.trace("Service type is {}", result);
                } catch (Exception e) {
                    log.trace("Error getting service id from subject {}", subject, e);
                }
            }
            List<String> scopes = accessTokenAuthentication.getAccessToken().getScope();
            if (scopes.stream().anyMatch(scope -> scope.contains("artifactory-cold"))) {
                return ARTIFACTORY_ARCHIVE;
            }
        } else {
            if (authentication == null) {
                log.trace("Authentication is null");
            } else {
                log.trace("Authentication class {}", authentication.getClass().getName());
            }
        }
        return result;
    }

    private static String handleXraySubject(String subject) {
        return Arrays.stream(XraySpecialSubjects.values())
                .filter(val -> subject.startsWith(val.getSubject()))
                .map(XraySpecialSubjects::getActor)
                .findFirst().orElse(ServiceType.XRAY);
    }

    private static TrafficEntry.TrafficActor getTrafficActor(String userAgent, String username, String serviceType) {
        log.trace("Get traffic actor using useragent: {} username: {}", userAgent, username);

        if (isActorArchive(userAgent, username, serviceType)) {
            return TrafficEntry.TrafficActor.ARCHIVE;
        }

        if (ARTIFACTORY_XRAY_USER.equals(username)) {
            return TrafficEntry.TrafficActor.XRAY;
        }

        if (StringUtils.isNoneBlank(username) &&
                username.startsWith(TOKEN_PREFIX + ARTIFACTORY_FEDERATION_USER_PREFIX)) {
            return TrafficEntry.TrafficActor.FEDERATION;
        }

        String lowercased = userAgent == null ? "" : userAgent.toLowerCase();
        if (lowercased.contains("xray")) {
            return TrafficEntry.TrafficActor.XRAY;
        }
        if (lowercased.contains("jfrog-replicator/")) {
            return TrafficEntry.TrafficActor.REPLICATOR;
        }
        if (lowercased.contains("observability") && username.equals(SecurityService.USER_SYSTEM)) {
            return TrafficEntry.TrafficActor.OBSERVABILITY;
        }
        if (lowercased.contains("jfconnect") && username.equals(SecurityService.USER_SYSTEM)) {
            return TrafficEntry.TrafficActor.JFCONNECT;
        }
        if (lowercased.contains("jflink") && username.equals(SecurityService.USER_SYSTEM)) {
            return TrafficEntry.TrafficActor.JFLINK;
        }
        if (lowercased.contains("ba-billing-logshipping") && username.equals(SecurityService.USER_SYSTEM)) {
            return TrafficEntry.TrafficActor.BA_BILLING_LOGSHIPPING;
        }

        return TrafficEntry.TrafficActor.USER;
    }

    private static boolean isActorArchive(String userAgent, String username, String serviceType) {
        boolean isUserSystemOrColdEnv =
                (StringUtils.isNoneBlank(username) && username.equals(SecurityService.USER_SYSTEM)) ||
                        serviceType.equals(ARTIFACTORY_ARCHIVE);
        return !StringUtils.isEmpty(userAgent) && userAgent.equals(ARTIFACTORY_ARCHIVE) && isUserSystemOrColdEnv;
    }

}
