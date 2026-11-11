package org.artifactory.repo.config;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.config.RepoConfigDefaultValues.DefaultUrl;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @author Dan Feldman
 * @author BarakH
 */
class RepoConfigDefaultValuesTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private AolService aolService;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        Mockito.when(context.beanForType(AolService.class)).thenReturn(aolService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void verifyDefaultUrls() {
        Set<RepoType> uniqueTypes = Arrays.stream(DefaultUrl.values())
                .map(DefaultUrl::getType)
                .collect(Collectors.toSet());
        if (uniqueTypes.size() != DefaultUrl.values().length) {
            fail("There can only be one default url per package type.");
        }
    }

    @Test
    void testUrlByType() {
        assertNotNull(DefaultUrl.urlByType(RepoType.Generic));
        assertTrue(StringUtils.isEmpty(DefaultUrl.urlByType(RepoType.Generic)));
        assertEquals(DefaultUrl.urlByType(RepoType.Docker), "https://registry-1.docker.io/");
        assertEquals(DefaultUrl.urlByType(RepoType.Terraform), "https://github.com/");
        assertEquals(DefaultUrl.urlByType(RepoType.Go), "https://proxy.golang.org/");
    }

    @Test
    void testUrlListByType() {
        assertTrue(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Generic).isEmpty());
        assertTrue(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.GitLfs).isEmpty());
        assertTrue(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Vagrant).isEmpty());
        assertTrue(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Swift).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Cargo).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Maven).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Alpine).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Bower).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.CocoaPods).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Composer).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Chef).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Cargo).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Conda).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Conan).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.CRAN).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Debian).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Docker).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Gems).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Go).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Gradle).isEmpty());
        assertTrue(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Helm).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Ivy).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Maven).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Npm).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.NuGet).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Opkg).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.P2).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Puppet).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Pypi).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.YUM).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.SBT).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.VCS).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Pub).isEmpty());
        assertFalse(RepoConfigDefaultValues.DefaultUrl.urlListByType(RepoType.Terraform).isEmpty());
    }

    @Test
    void testIsDefaultUrl() {
        Mockito.when(aolService.isAol()).thenReturn(false);
        assertTrue(new RemoteRegistry("a", "b", RemoteRegistry.DefaultState.ONPREM).isDefault());
        assertTrue(new RemoteRegistry("a", "b", RemoteRegistry.DefaultState.ALL).isDefault());
        assertFalse(new RemoteRegistry("a", "b", RemoteRegistry.DefaultState.NONE).isDefault());
        assertFalse(new RemoteRegistry("a", "b", RemoteRegistry.DefaultState.AOL).isDefault());

        Mockito.when(aolService.isAol()).thenReturn(true);
        assertTrue(new RemoteRegistry("a", "b", RemoteRegistry.DefaultState.AOL).isDefault());
        assertTrue(new RemoteRegistry("a", "b", RemoteRegistry.DefaultState.ALL).isDefault());
        assertFalse(new RemoteRegistry("a", "b", RemoteRegistry.DefaultState.NONE).isDefault());
        assertFalse(new RemoteRegistry("a", "b", RemoteRegistry.DefaultState.ONPREM).isDefault());
    }

}