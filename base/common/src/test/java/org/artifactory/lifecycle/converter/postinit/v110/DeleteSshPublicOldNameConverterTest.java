package org.artifactory.lifecycle.converter.postinit.v110;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.junit.jupiter.api.Test;
import org.testng.annotations.AfterMethod;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

class DeleteSshPublicOldNameConverterTest extends ArtifactoryHomeBoundTest {
    @AfterMethod
    public void tearDown() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }
    @Test
    void convertFirstRanTest() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        ConfigsService configsService = mock(ConfigsService.class);
        when(context.beanForType(ConfigsService.class)).thenReturn(configsService);
        SecurityService securityService = mock(SecurityService.class);
        when(context.beanForType(SecurityService.class)).thenReturn(securityService);
        UserGroupService userGroupService = mock(UserGroupService.class);
        when(context.beanForType(UserGroupService.class)).thenReturn(userGroupService);
        CompoundVersionDetails source = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v7590m001.get(), null, 0L);
        DeleteSshPublicOldNameConverter converter =
                new DeleteSshPublicOldNameConverter(ArtifactoryServiceVersionProvider.v7590m001.get(), null);
        converter.convert(source, source);
        verify(securityService, times(1)).doAsSystem(any());
        verifyNoMoreInteractions(userGroupService);
    }
    @Test
    public void testIsInterested() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        ConfigsService configsService = mock(ConfigsService.class);
        when(context.beanForType(ConfigsService.class)).thenReturn(configsService);
        when(configsService.getConfig(any(ConfigsServiceKey.class))).thenReturn(null);

        DeleteSshPublicOldNameConverter deleteSshPublicOldNameConverter =
                new DeleteSshPublicOldNameConverter(null, null);
        CompoundVersionDetails version = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v7635.get(), null, 0L);
        assertTrue(deleteSshPublicOldNameConverter.isInterested(version, version));

        version = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v7520m001.get(), null, 0L);
        assertFalse(deleteSshPublicOldNameConverter.isInterested(version, version));
    }
}