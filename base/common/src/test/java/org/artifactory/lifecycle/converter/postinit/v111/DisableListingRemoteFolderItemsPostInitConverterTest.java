package org.artifactory.lifecycle.converter.postinit.v111;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
public class DisableListingRemoteFolderItemsPostInitConverterTest extends ArtifactoryHomeBoundJUnitTest {

    private DisableListingRemoteFolderItemsPostInitConverter converter;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private CompoundVersionDetails source;
    private RemoteRepoConfig repoConfigMaven;
    private RemoteRepoConfig repoConfigGeneric;
    private RemoteRepoConfig repoConfigGenericSmart;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void init() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        repoConfigMaven = RemoteRepoConfig.create("repoKey", RepoType.Maven);
        repoConfigGeneric = RemoteRepoConfig.create("generic", RepoType.Generic);
        repoConfigGenericSmart = RemoteRepoConfig.create("genericSmart", RepoType.Generic);
        repoConfigGenericSmart.getRepoTypeConfig().getContentSynchronisation().setEnabled(true);
        repoConfigGenericSmart.getRepoTypeConfig().setListRemoteFolderItems(true);
        repoConfigMaven.getRepoTypeConfig().setListRemoteFolderItems(true);
        repoConfigGeneric.getRepoTypeConfig().setListRemoteFolderItems(true);
        converter = new DisableListingRemoteFolderItemsPostInitConverter(null, null);
    }

    @AfterEach
    void AfterEach() throws Exception {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void testConvert() {
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(
                List.of(repoConfigMaven, repoConfigGeneric));
        converter.convert(source, source);
        assertFalse(repoConfigMaven.getRepoTypeConfig().isListRemoteFolderItems());
        assertFalse(repoConfigGeneric.getRepoTypeConfig().isListRemoteFolderItems());
        assertTrue(repoConfigGenericSmart.getRepoTypeConfig().isListRemoteFolderItems());
    }

    @ParameterizedTest
    @MethodSource("sourceTargetVersions")
    void testIsInterested(ArtifactoryServiceVersion sourceVersion, ArtifactoryServiceVersion targetVersion,
            boolean expected) {
        CompoundVersionDetails source = mock(CompoundVersionDetails.class);
        CompoundVersionDetails target = mock(CompoundVersionDetails.class);
        when(source.getVersion()).thenReturn(sourceVersion);
        when(target.getVersion()).thenReturn(targetVersion);
        assertEquals(converter.isInterested(source, target), expected);
    }

    static Stream<Arguments> sourceTargetVersions() {
        return Stream.of(
                Arguments.of(ArtifactoryServiceVersionProvider.v7930.get(),
                        ArtifactoryServiceVersionProvider.v7930.get(), false),
                Arguments.of(ArtifactoryServiceVersionProvider.v7910.get(),
                        ArtifactoryServiceVersionProvider.v7930.get(), false),
                Arguments.of(ArtifactoryServiceVersionProvider.v7930.get(),
                        ArtifactoryServiceVersionProvider.v7940.get(), true)
        );
    }

    @Test
    void testAssertConversionPrecondition() {
        converter.assertConversionPrecondition(null, null, null);
    }

    @Test
    void testCovertThrowException() {
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE))
                .thenThrow(new RuntimeException("Test exception"));
        converter.convert(source, source);
        assertTrue(repoConfigMaven.getRepoTypeConfig().isListRemoteFolderItems());
        assertTrue(repoConfigGeneric.getRepoTypeConfig().isListRemoteFolderItems());
        assertTrue(repoConfigGenericSmart.getRepoTypeConfig().isListRemoteFolderItems());
    }
}