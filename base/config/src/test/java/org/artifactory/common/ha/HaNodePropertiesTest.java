/*
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
 */

package org.artifactory.common.ha;

import com.jfrog.sysconf.SysConfig;
import org.testng.annotations.Test;

import static org.artifactory.common.ha.HaNodeProperties.PROP_CROSS_ZONE_ORDER;
import static org.jfrog.common.ResourceUtils.getResource;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the {@link HaNodeProperties}.
 *
 * @author Yossi Shaul
 */
@Test
public class HaNodePropertiesTest {

    public void loadFromSysConfig() {
        SysConfig sysConfig = new SysConfig.Builder().withHome("test")
                .withSystemYaml(getResource("/sysconf/hanodeprops.system.yaml")).build();
        HaNodeProperties nodeProps = new HaNodeProperties();
        nodeProps.load(sysConfig);
        assertEquals(nodeProps.getServerId(), "art1id");
        assertEquals(nodeProps.getIp(), "15.5.19.78");
        assertTrue(nodeProps.isEnabled());
        assertEquals(nodeProps.getClusterDataDir(), "/mnt/shared/artifactory/ha-data");
        assertEquals(nodeProps.getBackupDir(), "/mnt/shared/artifactory/ha-backup");
        assertEquals(nodeProps.getProperties().getProperty(PROP_CROSS_ZONE_ORDER), "1,2");
    }

}