package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.watch.ArtifactWatchAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.mockito.Mockito.when;

/**
 * @author Gunaseelan Narayanan
 * Date: 30/3/23
 */
public class RepositoryInfoTest extends ArtifactoryHomeBoundTest {

    private AutoCloseable mockitoClosable;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private SecurityService securityService;
    @Mock
    private ArtifactoryContext context;
    @Mock
    private AddonsManager addonsManagerMock;
    @Mock
    private ArtifactWatchAddon watchAddon;
    @Mock
    private ItemInfo itemInfo;
    @Mock
    private LocalRepoDescriptor repoDescriptor;
    private KeyPairDescriptor keyPairDescriptor;
    private RepoPath repoPath;
    private String repoKey = "repoKey";

    private static Stream<Arguments> providerForRepoType() {
        return Stream.of(
                Arguments.of(RepoType.YUM, "/api/security/keypair/testkey/public"),
                Arguments.of(RepoType.Debian, "/api/security/keypair/testkey/public"),
                Arguments.of(RepoType.Opkg, "/api/security/keypair/testkey/public"),
                Arguments.of(RepoType.Maven, null)
        );
    }

    @BeforeEach
    void setup() {
        this.mockitoClosable = MockitoAnnotations.openMocks(this);
        keyPairDescriptor = new KeyPairDescriptor();
        keyPairDescriptor.setPublicKey("public");
        keyPairDescriptor.setPrivateKey("private");
        keyPairDescriptor.setKey("testkey");
        ArtifactoryContextThreadBinder.bind(context);

        when(ContextHelper.get().getRepositoryService()).thenReturn(repositoryService);
        when(ContextHelper.get().beanForType(SecurityService.class)).thenReturn(securityService);
        when(securityService.currentUsername()).thenReturn("name");
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManagerMock);
        when(addonsManagerMock.addonByType(ArtifactWatchAddon.class)).thenReturn(watchAddon);
        repoPath = RepoPathFactory.create(repoKey);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        this.mockitoClosable.close();
    }

    @ParameterizedTest
    @MethodSource("providerForRepoType")
    public void testSigningKeyForRepos(RepoType repoType, String expectedSigningKeyLink) {
        when(itemInfo.getCreated()).thenReturn(1L);
        when(repoDescriptor.getType()).thenReturn(repoType);
        when(repoDescriptor.getPrimaryKeyPairDescriptor()).thenReturn(keyPairDescriptor);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey(repoKey)).thenReturn(repoDescriptor);
        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo);
        RepositoryInfo repositoryInfo = new RepositoryInfo(repoDescriptor, repoPath);
        Assertions.assertEquals(repositoryInfo.getSigningKeyLink(), expectedSigningKeyLink);
        Assertions.assertTrue(repositoryInfo.getCreated()
                        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"),
                "created should be ISO 8601 UTC, got: " + repositoryInfo.getCreated());
    }

    @Test
    public void whenPassingEmptyKeyToRepoInfoGetSigningKeyShouldReturnNull() {
        when(itemInfo.getCreated()).thenReturn(1L);
        when(repoDescriptor.getType()).thenReturn(RepoType.YUM);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey(repoKey)).thenReturn(repoDescriptor);
        when(repositoryService.getItemInfo(repoPath)).thenReturn(itemInfo);
        RepositoryInfo repositoryInfo = new RepositoryInfo(repoDescriptor, repoPath);
        Assertions.assertNull(repositoryInfo.getSigningKeyLink());
        Assertions.assertTrue(repositoryInfo.getCreated()
                        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"),
                "created should be ISO 8601 UTC, got: " + repositoryInfo.getCreated());
    }
}