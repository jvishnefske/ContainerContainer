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

package org.artifactory.descriptor.security;

import org.mockito.Mockito;
import org.testng.annotations.Test;


import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the SecurityDescriptor.
 *
 * @author Yossi Shaul
 */
@Test
public class SecurityDescriptorTest {
    public void defaultConstructor() {
        SecurityDescriptor security = new SecurityDescriptor();
        assertNotNull(security.getPasswordSettings(), "Password settings list should not be null");
    }

    public void checkExternalAuthEnabled() {
        SecurityDescriptor securityDescriptor = Mockito.spy(new SecurityDescriptor());
        Mockito.doReturn(true).when(securityDescriptor).isCentralOAuthEnabled();
        assertTrue(securityDescriptor.isAnyExternalAuthEnabled());
        Mockito.verify(securityDescriptor).isCentralOAuthEnabled();
        Mockito.verify(securityDescriptor).isAnyExternalAuthEnabled();
        Mockito.verifyNoMoreInteractions(securityDescriptor);
    }

    public void checkExternalAuthDisabled() {
        SecurityDescriptor securityDescriptor = Mockito.spy(new SecurityDescriptor());
        Mockito.doReturn(false).when(securityDescriptor).isCentralOAuthEnabled();
        assertFalse(securityDescriptor.isAnyExternalAuthEnabled());
        Mockito.verify(securityDescriptor).isCentralOAuthEnabled();
        Mockito.verify(securityDescriptor).isAnyExternalAuthEnabled();
        Mockito.verifyNoMoreInteractions(securityDescriptor);
    }

}