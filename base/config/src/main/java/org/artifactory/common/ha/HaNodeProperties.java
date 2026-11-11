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

package org.artifactory.common.ha;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.jfrog.sysconf.SysConfig;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;
import java.util.Properties;

/**
 * @author yoavl
 */
public class HaNodeProperties {
    private static final Logger log = LoggerFactory.getLogger(HaNodeProperties.class);

    public static final String PROP_NODE_ID = "node.id";
    public static final String PROP_NODE_IP = "node.ip";
    public static final String PROP_HA_DATA_DIR = "artifactory.ha.data.dir";
    public static final String PROP_BACKUP_DIR = "artifactory.ha.backup.dir";
    public static final String PROP_PRIMARY = "primary";
    public static final String PROP_ENABLED = "enabled";
    public static final String PROP_MEMBERSHIP_PORT = "membership.port";
    // Binarystore props in node properties
    public static final String PROP_CROSS_ZONE_ORDER = "cross.zone.order";
    private static final String DEFAULT_NODE_ID = "Artifactory";

    private final Properties properties = new Properties();

    public void load(SysConfig config) {
        String nodeId = config.get("artifactory.node.id", DEFAULT_NODE_ID);
        properties.setProperty(PROP_NODE_ID, nodeId);
        config.get("artifactory.node.haEnabled").ifPresent(v -> properties.setProperty(PROP_ENABLED, v));
        config.get("artifactory.node.primary").ifPresent(v -> properties.setProperty(PROP_PRIMARY, v));
        config.get("artifactory.node.ip").ifPresent(v -> properties.setProperty(PROP_NODE_IP, v));
        config.get("artifactory.node.membershipPort").ifPresent(v -> properties.setProperty(PROP_MEMBERSHIP_PORT, v));
        config.get("artifactory.node.haDataDir").ifPresent(v -> properties.setProperty(PROP_HA_DATA_DIR, v));
        config.get("artifactory.node.haDataBackup").ifPresent(v -> properties.setProperty(PROP_BACKUP_DIR, v));
        config.get("artifactory.node.crossZoneOrder").ifPresent(v -> properties.setProperty(PROP_CROSS_ZONE_ORDER, v));
        if (isEnabled()) {
            log.info("Artifactory is running in clustered mode.");
        } else {
            log.info("Artifactory is running in non-clustered mode.");
        }
    }

    public String getServerId() {
        return properties.getProperty(PROP_NODE_ID);
    }


    public boolean isEnabled() {
        return Boolean.parseBoolean(properties.getProperty(PROP_ENABLED));
    }

    public Properties getProperties() {
        return properties;
    }

    @Nullable
    public Integer getMembershipPort() {
        String membershipPort = properties.getProperty(PROP_MEMBERSHIP_PORT);
        return StringUtils.isNotBlank(membershipPort) ? Integer.valueOf(membershipPort) : null;
    }

    public ImmutableMap<String, String> getPropertiesMap() {
        return Maps.fromProperties(properties);
    }

    public void setProperty(String key, String value) {
        properties.put(key, value);
    }

    public String getHaDirProperty(String key) {
        return (String) properties.get("artifactory.ha." + key + ".dir");
    }

    public String getBackupDir() {
        return properties.getProperty(PROP_BACKUP_DIR, null);
    }

    public String getClusterDataDir() {
        return properties.getProperty(PROP_HA_DATA_DIR, null);
    }

    public String getIp() {
        return properties.getProperty(PROP_NODE_IP);
    }
}
