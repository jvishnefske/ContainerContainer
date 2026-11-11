package org.artifactory.lifecycle.converter.postinit.v109;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.junit.jupiter.api.Test;
import org.testng.annotations.AfterMethod;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

class SshPublicKeyNameConverterTest extends ArtifactoryHomeBoundTest {
    @AfterMethod
    public void tearDown() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }
    @Test
    void convert() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        UserGroupService userGroupService = mock(UserGroupService.class);
        when(context.beanForType(UserGroupService.class)).thenReturn(userGroupService);
        CompoundVersionDetails source = mock(CompoundVersionDetails.class);
        SshPublicKeyNameConverter converter = new SshPublicKeyNameConverter(null, null);
        converter.convert(source, source);
    }
    @Test
    public void testIsInterested() {
        SshPublicKeyNameConverter sshPublicKeyNameConverter =
                new SshPublicKeyNameConverter(null, null);
        CompoundVersionDetails from = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v7635.get(), null, 0L);
        CompoundVersionDetails to = new CompoundVersionDetails(
                ArtifactoryServiceVersionProvider.v7730.get(), null, 0L);
        assertTrue(sshPublicKeyNameConverter.isInterested(from, to));
    }
}