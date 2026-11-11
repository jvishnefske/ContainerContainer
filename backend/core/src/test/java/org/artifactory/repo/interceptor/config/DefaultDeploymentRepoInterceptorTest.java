package org.artifactory.repo.interceptor.config;

import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultDeploymentRepoInterceptorTest {

    private DefaultDeploymentRepoInterceptor interceptor;

    @BeforeEach
    public void setUp() {
        interceptor = spy(new DefaultDeploymentRepoInterceptor());
    }

    @ParameterizedTest
    @ValueSource(strings = {"validRepoRef1", "validRepoRef2"})
    @NullAndEmptySource
    void testOnBeforeSaveOnCreateWithValidRepoConfig(String defaultDeploymentRepoRef) {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef1", "validRepoRef2"));
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(defaultDeploymentRepoRef);
        RepoConfigChange repoConfigChange = mock(RepoConfigChange.class);
        when(repoConfigChange.getConfigAction()).thenReturn(REPO_CREATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(null);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(virtualRepoConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = Map.of(
                RepoDetailsType.VIRTUAL, List.of(repoConfigChange)
        );

        assertDoesNotThrow(() -> interceptor.onBeforeSave(mutableRepoConfigChanges));
    }

    @ParameterizedTest
    @ValueSource(strings = {"validRepoRef1", "validRepoRef2"})
    @NullAndEmptySource
    void testOnBeforeSaveOnUpdateWithValidDefaultDeploymentRepoRef(String oldDefaultDeploymentRepoRef) {
        VirtualRepoConfig oldVirtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        oldVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef1", "validRepoRef2"));
        oldVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(oldDefaultDeploymentRepoRef);

        VirtualRepoConfig newVirtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        newVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef1", "validRepoRef2"));
        newVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("validRepoRef2");

        RepoConfigChange repoConfigChange = mock(RepoConfigChange.class);
        when(repoConfigChange.getConfigAction()).thenReturn(REPO_UPDATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldVirtualRepoConfig);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newVirtualRepoConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = Map.of(
                RepoDetailsType.VIRTUAL, List.of(repoConfigChange)
        );

        assertDoesNotThrow(() -> interceptor.onBeforeSave(mutableRepoConfigChanges));
    }

    @ParameterizedTest
    @ValueSource(strings = {"validRepoRef1"})
    @NullAndEmptySource
    void testOnBeforeSaveOnUpdateWithValidRepoRefRemoval(String defaultDeploymentRepoRef) {
        VirtualRepoConfig oldVirtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        oldVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef1", "validRepoRef2"));
        oldVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(defaultDeploymentRepoRef);

        VirtualRepoConfig newVirtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        newVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef1"));
        newVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef(defaultDeploymentRepoRef);

        RepoConfigChange repoConfigChange = mock(RepoConfigChange.class);
        when(repoConfigChange.getConfigAction()).thenReturn(REPO_UPDATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldVirtualRepoConfig);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newVirtualRepoConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = Map.of(
                RepoDetailsType.VIRTUAL, List.of(repoConfigChange)
        );

        assertDoesNotThrow(() -> interceptor.onBeforeSave(mutableRepoConfigChanges));
    }

    @ParameterizedTest
    @MethodSource("repoRefsProvider")
    void testOnBeforeSaveOnCreateWithInvalidRepoConfig(List<String> repoRefs) {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(repoRefs);
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("invalidRepoRef");
        RepoConfigChange repoConfigChange = mock(RepoConfigChange.class);
        when(repoConfigChange.getConfigAction()).thenReturn(REPO_CREATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(null);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(virtualRepoConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = Map.of(
                RepoDetailsType.VIRTUAL, List.of(repoConfigChange)
        );

        assertThrows(IllegalArgumentException.class, () -> interceptor.onBeforeSave(mutableRepoConfigChanges));
    }

    @Test
    void testOnBeforeSaveOnUpdateWithInvalidDefaultDeploymentRepoRef() {
        VirtualRepoConfig oldVirtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        oldVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef1", "validRepoRef2"));
        oldVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("validRepoRef1");

        VirtualRepoConfig newVirtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        newVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef1", "validRepoRef2"));
        newVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("invalidRepoRef");

        RepoConfigChange repoConfigChange = mock(RepoConfigChange.class);
        when(repoConfigChange.getConfigAction()).thenReturn(REPO_UPDATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldVirtualRepoConfig);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newVirtualRepoConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = Map.of(
                RepoDetailsType.VIRTUAL, List.of(repoConfigChange)
        );

        assertThrows(IllegalArgumentException.class, () -> interceptor.onBeforeSave(mutableRepoConfigChanges));
    }

    @Test
    void testOnBeforeSaveOnUpdateWithInvalidRepoRefRemoval() {
        VirtualRepoConfig oldVirtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        oldVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef1", "validRepoRef2"));
        oldVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("validRepoRef1");

        VirtualRepoConfig newVirtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        newVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef2"));
        newVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("validRepoRef1");

        RepoConfigChange repoConfigChange = mock(RepoConfigChange.class);
        when(repoConfigChange.getConfigAction()).thenReturn(REPO_UPDATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldVirtualRepoConfig);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newVirtualRepoConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = Map.of(
                RepoDetailsType.VIRTUAL, List.of(repoConfigChange)
        );

        assertThrows(IllegalArgumentException.class, () -> interceptor.onBeforeSave(mutableRepoConfigChanges));
    }

    @Test
    void testOnBeforeSaveOnUpdateWithExistingInvalidRepoRefRemoval() {
        VirtualRepoConfig oldVirtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        oldVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef1", "validRepoRef2"));
        oldVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("validRepoRef3");

        VirtualRepoConfig newVirtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        newVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef2"));
        newVirtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("validRepoRef3");

        RepoConfigChange repoConfigChange = mock(RepoConfigChange.class);
        when(repoConfigChange.getConfigAction()).thenReturn(REPO_UPDATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(oldVirtualRepoConfig);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(newVirtualRepoConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = Map.of(
                RepoDetailsType.VIRTUAL, List.of(repoConfigChange)
        );

        assertDoesNotThrow(() -> interceptor.onBeforeSave(mutableRepoConfigChanges));
    }

    @Test
    void testOnBeforeSaveOnInvalidRepoConfigUpdateChange() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtualRepoKey", Generic);
        virtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("validRepoRef1"));
        virtualRepoConfig.getRepoTypeConfig().setDefaultDeploymentRepoRef("validRepoRef1");
        RepoConfigChange repoConfigChange = mock(RepoConfigChange.class);
        when(repoConfigChange.getConfigAction()).thenReturn(REPO_UPDATE);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(null);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(virtualRepoConfig);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = Map.of(
                RepoDetailsType.VIRTUAL, List.of(repoConfigChange)
        );

        assertDoesNotThrow(() -> interceptor.onBeforeSave(mutableRepoConfigChanges));

        when(repoConfigChange.getOldRepoConfig()).thenReturn(virtualRepoConfig);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(null);

        assertDoesNotThrow(() -> interceptor.onBeforeSave(mutableRepoConfigChanges));
    }

    @ParameterizedTest
    @MethodSource("createAndUpdateConfigActions")
    void testOnBeforeSaveOnInvalidRepoConfigChange(RepoConfigChange.ConfigAction configAction) {
        RepoConfigChange repoConfigChange = mock(RepoConfigChange.class);
        when(repoConfigChange.getConfigAction()).thenReturn(configAction);
        when(repoConfigChange.getOldRepoConfig()).thenReturn(null);
        when(repoConfigChange.getNewRepoConfig()).thenReturn(null);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = Map.of(
                RepoDetailsType.VIRTUAL, List.of(repoConfigChange)
        );

        assertDoesNotThrow(() -> interceptor.onBeforeSave(mutableRepoConfigChanges));
    }

    @ParameterizedTest
    @MethodSource("allConfigActionsExcludingCreateAndUpdate")
    void testOnBeforeSaveOnAllConfigActionsExcludingCreateAndUpdate(RepoConfigChange.ConfigAction configAction) {
        RepoConfigChange repoConfigChange = mock(RepoConfigChange.class);
        when(repoConfigChange.getConfigAction()).thenReturn(configAction);
        Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges = Map.of(
                RepoDetailsType.VIRTUAL, List.of(repoConfigChange)
        );

        assertDoesNotThrow(() -> interceptor.onBeforeSave(mutableRepoConfigChanges));
        verify(interceptor).validateDefaultDeploymentRepoIncludesInRepoRefs(List.of());
    }

    private static Stream<Arguments> repoRefsProvider() {
        return Stream.of(
                Arguments.of(List.of()),
                Arguments.of(List.of("repoRef1")),
                Arguments.of(List.of("repoRef2", "repoRef2"))
        );
    }

    private static Stream<Arguments> createAndUpdateConfigActions() {
        return Stream.of(
                Arguments.of(REPO_CREATE),
                Arguments.of(REPO_UPDATE)
        );
    }

    private static Stream<Arguments> allConfigActionsExcludingCreateAndUpdate() {
        return Arrays.stream(RepoConfigChange.ConfigAction.values())
                .filter(action -> action != REPO_CREATE)
                .filter(action -> action != REPO_UPDATE)
                .map(Arguments::of);
    }
}