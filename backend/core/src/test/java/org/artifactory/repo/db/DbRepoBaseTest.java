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

package org.artifactory.repo.db;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.snapshot.MavenSnapshotVersionAdapter;
import org.artifactory.request.NullRequestContext;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.RepoLayoutUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Unit test the DbRepoBase.
 *
 * @author Yossi Shaul
 */
@Test
public class DbRepoBaseTest extends ArtifactoryHomeBoundTest {

    private AuthorizationService authService;
    private SecurityService securityService;
    private DbLocalRepo repo;
    private ArtifactoryContext context;
    private AddonsManager addonsManager;
    private CentralConfigService configService;

    @BeforeMethod
    public void setup() {
        context = mock(InternalArtifactoryContext.class);
        authService = mock(AuthorizationService.class);
        securityService = mock(SecurityService.class);
        addonsManager = mock(AddonsManager.class);
        configService = mock(CentralConfigService.class);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.getAuthorizationService()).thenReturn(authService);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        configDescriptor.setRepoLayouts(List.of(RepoLayoutUtils.MAVEN_2_DEFAULT));
        when(configService.getDescriptor()).thenReturn(configDescriptor);
        ArtifactoryContextThreadBinder.bind(context);
        repo = createDbRepoBase();
    }

    @AfterMethod
    public void cleanUp() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void anonAccessDisabledAndNoReadPermissions() {
        whenXrayBlocked(false);
        RepoPath dummyPath = InternalRepoPathFactory.create("target", "blabla");
        when(authService.canRead(dummyPath)).thenReturn(false);
        StatusHolder status = repo.checkDownloadIsAllowed(dummyPath);
        assertTrue(status.isError(), "Download is allowed: " + status.getStatusMsg());
    }

    @Test
    public void anonAccessEnabledAndNoReadPermissions() {
        whenXrayBlocked(false);
        RepoPath dummyPath = InternalRepoPathFactory.create("target", "blabla");
        when(authService.canRead(dummyPath)).thenReturn(false);
        StatusHolder status = repo.checkDownloadIsAllowed(dummyPath);
        assertTrue(status.isError(), "Download is allowed: " + status.getStatusMsg());
    }

    @Test
    public void xrayBlockedArtifact() {
        XrayAddon xrayAddon = mock(XrayAddon.class);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        when(xrayAddon.isDownloadBlocked(any(RepoPath.class))).thenReturn(false);
        RepoPath dummyPath = InternalRepoPathFactory.create("target", "blabla");
        StatusHolder status = repo.checkDownloadIsNotBlocked(new NullRequestContext(dummyPath), dummyPath);
        assertTrue(!status.isError(), "Download is allowed: " + status.getStatusMsg());
    }

    @Test(dataProvider = "testBlockedArtifactXrayStatusDataProvider")
    public void testBlockedArtifactXrayStatus(boolean xrayAliveByHeartbeat, String whenedMsg) {
        XrayAddon xrayAddon = mock(XrayAddon.class);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        when(xrayAddon.isDownloadBlocked(any(RepoPath.class), anyBoolean())).thenReturn(true);
        when(xrayAddon.isXrayAliveByHeartbeat()).thenReturn(xrayAliveByHeartbeat);
        RepoPath dummyPath = InternalRepoPathFactory.create("target", "blabla");
        StatusHolder status = repo.checkDownloadIsNotBlocked(new NullRequestContext(dummyPath), dummyPath);
        assertTrue(status.isError());
        assertEquals(status.getStatusMsg(), whenedMsg);
        verify(addonsManager).addonByType(XrayAddon.class);
    }

    @DataProvider
    private Object[][] testBlockedArtifactXrayStatusDataProvider() {
        return new Object[][] {
                {true, "Artifact download request rejected: blabla was not downloaded due to the download blocking " +
                        "policy configured in Xray for target."},
                {false, "Artifact download request rejected: blabla was not downloaded due to the download blocking " +
                        "policy configured in Xray for target. Xray is unavailable."},
        };
    }

    private DbLocalRepo<LocalRepoDescriptor, LocalRepoConfig> createDbRepoBase() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repoKey", RepoType.Generic);
        localRepoConfig.getBaseConfig().setRepoLayoutRef(RepoLayoutUtils.MAVEN_2_DEFAULT.getName());
        LocalRepoDescriptor repoDescriptor = new LocalRepoDescriptor(localRepoConfig, List.of(), List.of(), List.of());
        DbLocalRepo<LocalRepoDescriptor, LocalRepoConfig> repo =
                new DbLocalRepo<>(repoDescriptor, localRepoConfig, null, null,
                        configService) {
                    @Override
                    public MavenSnapshotVersionAdapter getMavenSnapshotVersionAdapter() {
                        return null;
                    }

                };

        return repo;
    }

    private void whenXrayBlocked(boolean blocked) {
        XrayAddon xrayAddon = mock(XrayAddon.class);
        when(addonsManager.addonByType(XrayAddon.class)).thenReturn(xrayAddon);
        when(xrayAddon.isDownloadBlocked(any(RepoPath.class))).thenReturn(blocked);
    }
}
