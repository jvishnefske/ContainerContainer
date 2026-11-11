package org.artifactory.repo.virtual;


import org.artifactory.api.config.VirtualRepoConfigResolverService;
import org.artifactory.descriptor.repo.EmptyVirtualResolverFilter;
import org.artifactory.descriptor.repo.VirtualResolverFilter;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;

import static org.artifactory.util.CollectionUtils.newLinkedHashSet;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Vova Voloshin
 */

@ExtendWith(MockitoExtension.class)
public class VirtualRepositoryConfigResolverImplTest {

    @Mock
    private VirtualRepoConfigResolverService virtualRepoConfigResolverService;

    @Mock(answer = RETURNS_DEEP_STUBS)
    private VirtualRepoConfig virtualRepoConfig;

    @Nested
    @DisplayName("Tests for getRepoConfigs")
    class GetRepoConfigsTest {

        @Test
        void empty() {
            VirtualRepositoryConfigResolverImpl virtualRepositoryConfigResolver =
                    new VirtualRepositoryConfigResolverImpl(virtualRepoConfigResolverService);
            when(virtualRepoConfigResolverService.getVirtualRepoConfigByKey(any())).thenReturn(virtualRepoConfig);
            when(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs()).thenReturn(List.of("a", "b", "c"));
            LinkedHashSet<RepoConfig> expected = new LinkedHashSet<>();
            LinkedHashSet<RepoConfig> actual =
                    virtualRepositoryConfigResolver.getRepoConfigs("key", newLinkedHashSet(RepoDetailsType.LOCAL),
                            1000);
            Assertions.assertEquals(expected, actual);
        }

        @Test
        void justTheRoot() {
            String rootKey = "RootKey";
            VirtualRepositoryConfigResolverImpl virtualRepositoryConfigResolver =
                    new VirtualRepositoryConfigResolverImpl(virtualRepoConfigResolverService);
            when(virtualRepoConfigResolverService.getVirtualRepoConfigByKey(any())).thenReturn(virtualRepoConfig);
            when(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs()).thenReturn(List.of("a", "b", "c"));
            when(virtualRepoConfig.getKey()).thenReturn(rootKey);
            LinkedHashSet<RepoConfig> expected = new LinkedHashSet<>();
            LinkedHashSet<RepoConfig> actual =
                    virtualRepositoryConfigResolver.getRepoConfigs(rootKey, newLinkedHashSet(RepoDetailsType.LOCAL));
            Assertions.assertEquals(expected, actual);
        }

        @Test
        void oneChildLevelAskForLocalConfig() {
            String rootKey = "RootKey";
            String childA = "a";
            String childB = "b";
            String childC = "c";
            RepoConfig repoConfigChildA = Mockito.mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildB = Mockito.mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildC = Mockito.mock(VirtualRepoConfig.class, RETURNS_DEEP_STUBS);
            VirtualRepositoryConfigResolverImpl virtualRepositoryConfigResolver =
                    new VirtualRepositoryConfigResolverImpl(virtualRepoConfigResolverService);
            when(virtualRepoConfigResolverService.getVirtualRepoConfigByKey(anyString())).thenAnswer(invocationOnMock ->
            {
                if (rootKey.equals(invocationOnMock.getArgument(0))) {
                    return virtualRepoConfig;
                } else if (childA.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildA;
                } else if (childB.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildB;
                } else if (childC.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildC;
                } else {
                    return null;
                }
            });
            when(virtualRepoConfigResolverService.getRepoConfigsByKeysInSpecificOrder(any(), any())).thenAnswer(
                    invocationOnMock ->
                    {
                        if (virtualRepoConfig.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet(repoConfigChildA, repoConfigChildB, repoConfigChildC);
                        } else if (repoConfigChildC.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet();
                        } else {
                            return null;
                        }
                    });
            when(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs()).thenReturn(List.of("a", "b", "c"));
            when(virtualRepoConfig.getKey()).thenReturn(rootKey);
            when(repoConfigChildC.getKey()).thenReturn(childC);
            LinkedHashSet<RepoConfig> expected = new LinkedHashSet<>();
            expected.add(repoConfigChildA);
            LinkedHashSet<RepoConfig> actual =
                    virtualRepositoryConfigResolver.getRepoConfigs(rootKey, newLinkedHashSet(RepoDetailsType.LOCAL));
            Assertions.assertEquals(expected, actual);
        }

        @Test
        void oneChildLevelAskForRemoteConfig() {
            String rootKey = "RootKey";
            String childA = "a";
            String childB = "b";
            String childC = "c";
            RepoConfig repoConfigChildA = Mockito.mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildB = Mockito.mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildC = Mockito.mock(VirtualRepoConfig.class, RETURNS_DEEP_STUBS);
            VirtualRepositoryConfigResolverImpl virtualRepositoryConfigResolver =
                    new VirtualRepositoryConfigResolverImpl(virtualRepoConfigResolverService);
            when(virtualRepoConfigResolverService.getVirtualRepoConfigByKey(anyString())).thenAnswer(invocationOnMock ->
            {
                if (rootKey.equals(invocationOnMock.getArgument(0))) {
                    return virtualRepoConfig;
                } else if (childA.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildA;
                } else if (childB.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildB;
                } else if (childC.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildC;
                } else {
                    return null;
                }
            });
            when(virtualRepoConfigResolverService.getRepoConfigsByKeysInSpecificOrder(any(), any())).thenAnswer(
                    invocationOnMock ->
                    {
                        if (virtualRepoConfig.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet(repoConfigChildA, repoConfigChildB, repoConfigChildC);
                        } else if (repoConfigChildC.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet();
                        } else {
                            return null;
                        }
                    });
            when(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs()).thenReturn(List.of("a", "b", "c"));
            when(virtualRepoConfig.getKey()).thenReturn(rootKey);
            when(repoConfigChildC.getKey()).thenReturn(childC);
            LinkedHashSet<RepoConfig> expected = new LinkedHashSet<>();
            expected.add(repoConfigChildB);
            LinkedHashSet<RepoConfig> actual =
                    virtualRepositoryConfigResolver.getRepoConfigs(rootKey, newLinkedHashSet(RepoDetailsType.REMOTE));
            Assertions.assertEquals(expected, actual);
        }

        @Test
        void oneChildLevelAskForVirtualConfig() {
            String rootKey = "RootKey";
            String childA = "a";
            String childB = "b";
            String childC = "c";
            RepoConfig repoConfigChildA = Mockito.mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildB = Mockito.mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildC = Mockito.mock(VirtualRepoConfig.class, RETURNS_DEEP_STUBS);
            VirtualRepositoryConfigResolverImpl virtualRepositoryConfigResolver =
                    new VirtualRepositoryConfigResolverImpl(virtualRepoConfigResolverService);
            when(virtualRepoConfigResolverService.getVirtualRepoConfigByKey(anyString())).thenAnswer(invocationOnMock ->
            {
                if (rootKey.equals(invocationOnMock.getArgument(0))) {
                    return virtualRepoConfig;
                } else if (childA.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildA;
                } else if (childB.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildB;
                } else if (childC.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildC;
                } else {
                    return null;
                }
            });
            when(virtualRepoConfigResolverService.getRepoConfigsByKeysInSpecificOrder(any(), any())).thenAnswer(
                    invocationOnMock ->
                    {
                        if (virtualRepoConfig.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet(repoConfigChildA, repoConfigChildB, repoConfigChildC);
                        } else if (repoConfigChildC.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet();
                        } else {
                            return null;
                        }
                    });
            when(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs()).thenReturn(List.of("a", "b", "c"));
            when(virtualRepoConfig.getKey()).thenReturn(rootKey);
            when(repoConfigChildC.getKey()).thenReturn(childC);
            LinkedHashSet<RepoConfig> expected = new LinkedHashSet<>();
            expected.add(repoConfigChildC);
            LinkedHashSet<RepoConfig> actual = virtualRepositoryConfigResolver.getRepoConfigs(rootKey,
                    newLinkedHashSet(RepoDetailsType.VIRTUAL));
            Assertions.assertEquals(expected, actual);
        }

        @Test
        void oneChildLevelAskForAllConfig() {
            String rootKey = "RootKey";
            String childA = "a";
            String childB = "b";
            String childC = "c";
            RepoConfig repoConfigChildA = Mockito.mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildB = Mockito.mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildC = Mockito.mock(VirtualRepoConfig.class, RETURNS_DEEP_STUBS);
            VirtualRepositoryConfigResolverImpl virtualRepositoryConfigResolver =
                    new VirtualRepositoryConfigResolverImpl(virtualRepoConfigResolverService);
            when(virtualRepoConfigResolverService.getVirtualRepoConfigByKey(anyString())).thenAnswer(invocationOnMock ->
            {
                if (rootKey.equals(invocationOnMock.getArgument(0))) {
                    return virtualRepoConfig;
                } else if (childA.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildA;
                } else if (childB.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildB;
                } else if (childC.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildC;
                } else {
                    return null;
                }
            });
            when(virtualRepoConfigResolverService.getRepoConfigsByKeysInSpecificOrder(any(), any())).thenAnswer(
                    invocationOnMock ->
                    {
                        if (virtualRepoConfig.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet(repoConfigChildA, repoConfigChildB, repoConfigChildC);
                        } else if (repoConfigChildC.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet();
                        } else {
                            return null;
                        }
                    });
            when(virtualRepoConfig.getRepoTypeConfig().getRepositoryRefs()).thenReturn(List.of("a", "b", "c"));
            when(virtualRepoConfig.getKey()).thenReturn(rootKey);
            when(repoConfigChildC.getKey()).thenReturn(childC);
            LinkedHashSet<RepoConfig> expected = new LinkedHashSet<>();
            expected.add(repoConfigChildA);
            expected.add(repoConfigChildB);
            expected.add(repoConfigChildC);
            LinkedHashSet<RepoConfig> actual =
                    virtualRepositoryConfigResolver.getRepoConfigs(rootKey, newLinkedHashSet(RepoDetailsType.values()));
            Assertions.assertEquals(expected, actual);
        }

        @Test
        void twoChildLevelAskForAllConfig() {
            String rootKey = "RootKey";
            String childA = "a";
            String childB = "b";
            String childC = "c";
            String childD = "d";
            RepoConfig repoConfigChildA = Mockito.mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildB = Mockito.mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildC = Mockito.mock(VirtualRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildD = Mockito.mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
            VirtualRepositoryConfigResolverImpl virtualRepositoryConfigResolver =
                    new VirtualRepositoryConfigResolverImpl(virtualRepoConfigResolverService);
            when(virtualRepoConfigResolverService.getVirtualRepoConfigByKey(anyString())).thenAnswer(invocationOnMock ->
            {
                if (rootKey.equals(invocationOnMock.getArgument(0))) {
                    return virtualRepoConfig;
                } else if (childA.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildA;
                } else if (childB.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildB;
                } else if (childC.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildC;
                } else if (childD.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildD;
                } else {
                    return null;
                }
            });
            when(virtualRepoConfigResolverService.getRepoConfigsByKeysInSpecificOrder(any(), any())).thenAnswer(
                    invocationOnMock ->
                    {
                        if (virtualRepoConfig.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet(repoConfigChildA, repoConfigChildB, repoConfigChildC);
                        } else if (repoConfigChildC.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet(repoConfigChildD);
                        } else {
                            return null;
                        }
                    });
            when(virtualRepoConfig.getKey()).thenReturn(rootKey);
            when(repoConfigChildC.getKey()).thenReturn(childC);
            LinkedHashSet<RepoConfig> expected = new LinkedHashSet<>();
            expected.add(repoConfigChildA);
            expected.add(repoConfigChildB);
            expected.add(repoConfigChildC);
            expected.add(repoConfigChildD);
            VirtualResolverFilter virtualResolverFilter = new EmptyVirtualResolverFilter();
            LinkedHashSet<RepoConfig> actual =
                    virtualRepositoryConfigResolver.getRepoConfigs(rootKey, newLinkedHashSet(RepoDetailsType.values()),
                            virtualResolverFilter);
            Assertions.assertEquals(expected, actual);
        }

        @Test
        void twoChildLevelAskForRemoteConfig() {
            String rootKey = "RootKey";
            String childA = "a";
            String childB = "b";
            String childC = "c";
            String childD = "d";
            RepoConfig repoConfigChildA = Mockito.mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildB = Mockito.mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildC = Mockito.mock(VirtualRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildD = Mockito.mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
            VirtualRepositoryConfigResolverImpl virtualRepositoryConfigResolver =
                    new VirtualRepositoryConfigResolverImpl(virtualRepoConfigResolverService);
            when(virtualRepoConfigResolverService.getVirtualRepoConfigByKey(anyString())).thenAnswer(invocationOnMock ->
            {
                if (rootKey.equals(invocationOnMock.getArgument(0))) {
                    return virtualRepoConfig;
                } else if (childA.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildA;
                } else if (childB.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildB;
                } else if (childC.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildC;
                } else if (childD.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildD;
                } else {
                    return null;
                }
            });
            when(virtualRepoConfigResolverService.getRepoConfigsByKeysInSpecificOrder(any(), any())).thenAnswer(
                    invocationOnMock ->
                    {
                        if (virtualRepoConfig.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet(repoConfigChildA, repoConfigChildB, repoConfigChildC);
                        } else if (repoConfigChildC.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet(repoConfigChildD);
                        } else {
                            return null;
                        }
                    });
            when(virtualRepoConfig.getKey()).thenReturn(rootKey);
            when(repoConfigChildC.getKey()).thenReturn(childC);
            LinkedHashSet<RepoConfig> expected = new LinkedHashSet<>();
            expected.add(repoConfigChildB);
            expected.add(repoConfigChildD);
            LinkedHashSet<RepoConfig> actual = virtualRepositoryConfigResolver.getRepoConfigs(rootKey,
                    newLinkedHashSet(RepoDetailsType.REMOTE));
            Assertions.assertEquals(expected, actual);
        }

        @Test
        void threeChildLevelAskForRemoteConfig() {
            String rootKey = "RootKey";
            String childA = "a";
            String childB = "b";
            String childC = "c";
            String childD = "d";
            String childE = "e";
            String childF = "f";
            RepoConfig repoConfigChildA = Mockito.mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildB = Mockito.mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildC = Mockito.mock(VirtualRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildD = Mockito.mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildE = Mockito.mock(VirtualRepoConfig.class, RETURNS_DEEP_STUBS);
            RepoConfig repoConfigChildF = Mockito.mock(LocalRepoConfig.class, RETURNS_DEEP_STUBS);
            VirtualRepositoryConfigResolverImpl virtualRepositoryConfigResolver =
                    new VirtualRepositoryConfigResolverImpl(virtualRepoConfigResolverService);
            when(virtualRepoConfigResolverService.getVirtualRepoConfigByKey(anyString())).thenAnswer(invocationOnMock ->
            {
                if (rootKey.equals(invocationOnMock.getArgument(0))) {
                    return virtualRepoConfig;
                } else if (childA.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildA;
                } else if (childB.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildB;
                } else if (childC.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildC;
                } else if (childD.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildD;
                } else if (childE.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildE;
                } else if (childF.equals(invocationOnMock.getArgument(0))) {
                    return repoConfigChildF;
                } else {
                    return null;
                }
            });
            when(virtualRepoConfigResolverService.getRepoConfigsByKeysInSpecificOrder(any(), any())).thenAnswer(
                    invocationOnMock ->
                    {
                        if (virtualRepoConfig.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet(repoConfigChildA, repoConfigChildB, repoConfigChildC);
                        } else if (repoConfigChildC.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet(repoConfigChildD, repoConfigChildE);
                        } else if (repoConfigChildE.equals(invocationOnMock.getArgument(0))) {
                            return newLinkedHashSet(repoConfigChildF);
                        } else {
                            return null;
                        }
                    });
            when(virtualRepoConfig.getKey()).thenReturn(rootKey);
            when(repoConfigChildC.getKey()).thenReturn(childC);
            when(repoConfigChildE.getKey()).thenReturn(childE);
            LinkedHashSet<RepoConfig> expected = new LinkedHashSet<>();
            expected.add(repoConfigChildA);
            expected.add(repoConfigChildF);
            LinkedHashSet<RepoConfig> actual =
                    virtualRepositoryConfigResolver.getRepoConfigs(rootKey, newLinkedHashSet(RepoDetailsType.LOCAL));
            Assertions.assertEquals(expected, actual);
        }
    }
}
