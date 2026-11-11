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

package org.artifactory.layout;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author nadavy
 */
@Test
public class ReverseProxyConfigurationChangesInterceptorTest extends ArtifactoryHomeBoundTest {

    private AddonsManager addonsManager;

    @BeforeMethod
    public void setup() {
        addonsManager = mock(AddonsManager.class);
    }

    @AfterMethod
    public void clenaUp() {
        ArtifactoryContextThreadBinder.unbind();
    }

    public void testHandleReverseProxySettingsOssRunningMode() {
        when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.OSS);
        ReverseProxyConfigurationChangesInterceptor repoConfChangesInterceptor =
                new ReverseProxyConfigurationChangesInterceptor(addonsManager);
        CentralConfigDescriptorImpl newDescriptor = new CentralConfigDescriptorImpl();
        newDescriptor.addReverseProxy(new ReverseProxyDescriptor());
        repoConfChangesInterceptor.handleReverseProxySettings(newDescriptor);
        Assert.assertEquals(newDescriptor.getReverseProxies().size(), 0);
    }

    public void testHandleReverseProxySettingsProRunningMode() {
        when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.PRO);
        ReverseProxyConfigurationChangesInterceptor repoConfChangesInterceptor =
                new ReverseProxyConfigurationChangesInterceptor(addonsManager);
        CentralConfigDescriptorImpl newDescriptor = new CentralConfigDescriptorImpl();
        newDescriptor.addReverseProxy(new ReverseProxyDescriptor());
        repoConfChangesInterceptor.handleReverseProxySettings(newDescriptor);
        Assert.assertEquals(newDescriptor.getReverseProxies().size(), 1);
    }

    public void testHandleReverseProxySettingsJcrRunningMode() {
        when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.JCR);
        ReverseProxyConfigurationChangesInterceptor repoConfChangesInterceptor =
                new ReverseProxyConfigurationChangesInterceptor(addonsManager);
        CentralConfigDescriptorImpl newDescriptor = new CentralConfigDescriptorImpl();
        newDescriptor.addReverseProxy(new ReverseProxyDescriptor());
        repoConfChangesInterceptor.handleReverseProxySettings(newDescriptor);
        Assert.assertEquals(newDescriptor.getReverseProxies().size(), 1);
    }
}