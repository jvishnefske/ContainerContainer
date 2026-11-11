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

package org.artifactory.descriptor.security.sso;

import org.jfrog.access.client.model.HttpSsoSetting;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the behavior of the HTTP SSO settings descriptor
 *
 * @author Noam Y. Tenne
 */
@Test
public class HttpSsoSettingsTest {

    /**
     * Tests the validity of the default values
     */
    public void testDefaultConstructor() {
        HttpSsoSetting httpSsoSettings = HttpSsoSetting.builder().build();
        assertFalse(httpSsoSettings.isHttpSsoProxied(), "Proxying should not be enabled by default.");
        assertTrue(httpSsoSettings.isAutoCreateUser(), "Auto user creation should be enabled by default.");
        assertEquals(httpSsoSettings.getRemoteUserRequestVariable(), "REMOTE_USER",
                "Default remote user request variable should be 'REMOTE_USER'.");
    }
}