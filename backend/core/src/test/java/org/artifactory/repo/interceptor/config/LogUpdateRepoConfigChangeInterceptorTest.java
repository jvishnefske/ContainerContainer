package org.artifactory.repo.interceptor.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfigCopyFactory;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.repo.remote.CustomHttpHeader;
import org.artifactory.sensitive.CustomHttpHeadersSensitiveHandler;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.DockerPackageTypeConfigMapper;
import org.artifactory.util.RepoConfigDiffUtil;
import org.jfrog.common.config.diff.DiffUtils;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogUpdateRepoConfigChangeInterceptorTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String D = DiffUtils.DELIMITER;

    private final LogUpdateRepoConfigChangeInterceptor logUpdateRepoConfigChangeInterceptor =
            spy(new LogUpdateRepoConfigChangeInterceptor());
    private LocalRepoConfig oldLocalRepoConfig;
    private LocalRepoConfig newLocalRepoConfig;
    private RemoteRepoConfig oldRemoteRepoConfig;
    private RemoteRepoConfig newRemoteRepoConfig;
    private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

    @BeforeEach
    void before() {
        bindSpringContextWithDynamicSensitiveHandlerBeans();
        setupRepos();
        setupLogger();
    }

    @AfterEach
    void after() {
        listAppender.stop();
        ArtifactoryContextThreadBinder.unbind();
    }

    /**
     * {@link SensitiveDiffUtil} resolves {@code @DynamicSensitive} handlers (e.g. {@code customHttpHeaders} on remote
     * repos) from the Spring context. In production the async {@code onAfterSave} thread has the context bound; here we
     * bind a mock that supplies the handler bean.
     */
    @SuppressWarnings("unchecked")
    private static void bindSpringContextWithDynamicSensitiveHandlerBeans() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type == CustomHttpHeadersSensitiveHandler.class) {
                CustomHttpHeadersSensitiveHandler handler = new CustomHttpHeadersSensitiveHandler();
                handler.init();
                return handler;
            }
            throw new IllegalArgumentException("Unexpected handler type for test context: " + type.getName());
        });
        ArtifactoryContextThreadBinder.bind(context);
    }

    @Test
    void testOnAfterSaveRepoUpdate() throws JSONException {
        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange("local-repo", REPO_UPDATE,
                        newLocalRepoConfig, oldLocalRepoConfig)));
        logUpdateRepoConfigChangeInterceptor.onAfterSave(changes);
        ILoggingEvent logEvent = listAppender.list.stream().findFirst().orElseThrow();
        String actualJson = logEvent.getFormattedMessage().substring(
                "Repository 'local-repo' configuration has been updated. Changes: ".length());
        String expectedJson = """
                [
                    {
                        "attributeName": "baseConfig%srepoLayoutRef",
                        "oldValue": "oldLayout",
                        "newValue": "newLayout"
                    }
                ]
                """.formatted(D);
        JSONAssert.assertEquals(expectedJson, actualJson, false);
    }

    @Test
    void testOnAfterSaveRepoBlackedOut() {
        LocalRepoConfig oldLocalRepoConfig = LocalRepoConfig.create("repoKey", Generic);
        LocalRepoConfig newLocalRepoConfig = RepoConfigCopyFactory.copy(oldLocalRepoConfig);
        newLocalRepoConfig.getRepoTypeConfig().setBlackedOut(true);
        RepoConfigChange repoConfigChange = RepoConfigChange.updateOf(oldLocalRepoConfig, newLocalRepoConfig);

        logUpdateRepoConfigChangeInterceptor.onAfterSave(new RepoConfigChanges(repoConfigChange));

        ILoggingEvent logEvent = listAppender.list.stream().skip(1L).findFirst().orElseThrow();
        String actualLog = logEvent.getFormattedMessage();
        String expectedLog = "Repository 'repoKey' has been blacked out";
        assertEquals(expectedLog, actualLog);
    }

    @Test
    void testOnAfterSaveIllegalChangeNullOld() {
        LocalRepoConfig repoConfig = LocalRepoConfig.create("repoKey", Generic);
        RepoConfigChange illegalRepoConfigChange =
                new RepoConfigChange("repoKey", REPO_UPDATE, repoConfig, null);

        logUpdateRepoConfigChangeInterceptor.onAfterSave(new RepoConfigChanges(illegalRepoConfigChange));

        ILoggingEvent logEvent = listAppender.list.stream().findFirst().orElseThrow();
        String actualLog = logEvent.getFormattedMessage();
        String expectedLog =
                "Illegal repo config change detected. oldRepoChange: 'null', newRepoConfig: 'repoKey'";
        assertEquals(expectedLog, actualLog);
    }

    @Test
    void testOnAfterSaveIllegalChangeNullNew() {
        LocalRepoConfig repoConfig = LocalRepoConfig.create("repoKey", Generic);
        RepoConfigChange illegalRepoConfigChange =
                new RepoConfigChange("repoKey", REPO_UPDATE, null, repoConfig);

        logUpdateRepoConfigChangeInterceptor.onAfterSave(new RepoConfigChanges(illegalRepoConfigChange));

        ILoggingEvent logEvent = listAppender.list.stream().findFirst().orElseThrow();
        String actualLog = logEvent.getFormattedMessage();
        String expectedLog =
                "Illegal repo config change detected. oldRepoChange: 'repoKey', newRepoConfig: 'null'";
        assertEquals(expectedLog, actualLog);
    }

    @Test
    void testOnAfterSaveNoChanges() {
        LocalRepoConfig oldAndNew = LocalRepoConfig.create("repoKey", Generic);
        RepoConfigChange repoConfigNoChange = RepoConfigChange.updateOf(oldAndNew, oldAndNew);

        logUpdateRepoConfigChangeInterceptor.onAfterSave(new RepoConfigChanges(repoConfigNoChange));

        List<ILoggingEvent> logs = listAppender.list.stream().toList();
        assertTrue(logs.isEmpty());
    }

    @Test
    void testVirtualChangedCantBeBlackedOut() throws JSONException {
        VirtualRepoConfig oldVirtualRepoConfig = VirtualRepoConfig.create("repoKey", Generic);
        VirtualRepoConfig newVirtualRepoConfig = VirtualRepoConfig.create("repoKey", Generic);
        newVirtualRepoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("newRef"));
        RepoConfigChange repoConfigChange = RepoConfigChange.updateOf(oldVirtualRepoConfig, newVirtualRepoConfig);

        logUpdateRepoConfigChangeInterceptor.onAfterSave(new RepoConfigChanges(repoConfigChange));

        long numLogs = listAppender.list.size();
        assertEquals(1, numLogs);
        ILoggingEvent logEvent = listAppender.list.stream().findFirst().orElseThrow();
        String actualJson = logEvent.getFormattedMessage().substring(
                "Repository 'repoKey' configuration has been updated. Changes: ".length());
        String expectedJson = """
                [
                  {
                    "attributeName": "repoTypeConfig%srepositoryRefs",
                    "oldValue": [],
                    "newValue": ["newRef"]
                  }
                ]
                """.formatted(D);
        JSONAssert.assertEquals(expectedJson, actualJson, false);
    }

    @Test
    void testOnAfterSaveMasksSensitiveCustomHttpHeaderValue() throws JSONException {
        RemoteRepoConfig oldRemoteRepoConfig = RemoteRepoConfig.create("repoKey", Generic);
        oldRemoteRepoConfig.getRepoTypeConfig().setCustomHttpHeaders(
                List.of(new CustomHttpHeader("Authorization", "oldToken", true)));
        RemoteRepoConfig newRemoteRepoConfig = RepoConfigCopyFactory.copy(oldRemoteRepoConfig);
        newRemoteRepoConfig.getRepoTypeConfig().setCustomHttpHeaders(
                List.of(new CustomHttpHeader("Authorization", "newToken", true)));
        RepoConfigChange repoConfigChange = RepoConfigChange.updateOf(oldRemoteRepoConfig, newRemoteRepoConfig);

        logUpdateRepoConfigChangeInterceptor.onAfterSave(new RepoConfigChanges(repoConfigChange));

        ILoggingEvent logEvent = listAppender.list.stream().findFirst().orElseThrow();
        String actualJson = logEvent.getFormattedMessage().substring(
                "Repository 'repoKey' configuration has been updated. Changes: ".length());
        String expectedJson = """
                [
                  {
                    "attributeName": "repoTypeConfig%scustomHttpHeaders%sauthorization%svalue",
                    "oldValue": "***",
                    "newValue": "***"
                  }
                ]
                """.formatted(D, D, D);
        JSONAssert.assertEquals(expectedJson, actualJson, false);
        assertFalse(actualJson.contains("oldToken"));
        assertFalse(actualJson.contains("newToken"));
    }

    @ParameterizedTest
    @MethodSource("nonUpdateConfigAction")
    void testOnAfterSaveRepoNonUpdate(RepoConfigChange.ConfigAction action) {
        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(RepoDetailsType.LOCAL,
                List.of(new RepoConfigChange("local-repo", action,
                        newLocalRepoConfig, oldLocalRepoConfig)));
        logUpdateRepoConfigChangeInterceptor.onAfterSave(changes);
        verify(logUpdateRepoConfigChangeInterceptor, never()).logRepoConfigChange(any());
    }

    @Test
    void testLogRepoConfigChangeException() {
        try (MockedStatic<RepoConfigDiffUtil> mockedDiffUtil = mockStatic(RepoConfigDiffUtil.class)) {
            mockedDiffUtil.when(() -> RepoConfigDiffUtil.maskedDiff(any(), any()))
                    .thenThrow(new RuntimeException("Test exception"));

            RepoConfigChange repoConfigChange =
                    new RepoConfigChange("local-repo", REPO_UPDATE, newLocalRepoConfig, oldLocalRepoConfig);
            logUpdateRepoConfigChangeInterceptor.logRepoConfigChange(repoConfigChange);

            ILoggingEvent logEvent = listAppender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .findFirst()
                    .orElseThrow();

            assertTrue(logEvent.getFormattedMessage()
                    .contains("Failed to calculate repository 'local-repo' configuration diffs"));
        }
    }

    @Test
    void testOnAfterSaveRepoUpdateMultipleRepos() throws JSONException {
        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(
                RepoDetailsType.LOCAL, List.of(
                        new RepoConfigChange("local-repo", REPO_UPDATE, newLocalRepoConfig, oldLocalRepoConfig)
                ),
                RepoDetailsType.REMOTE, List.of(
                        new RepoConfigChange("remote-repo", REPO_UPDATE, newRemoteRepoConfig, oldRemoteRepoConfig)
                )
        );

        logUpdateRepoConfigChangeInterceptor.onAfterSave(changes);

        List<ILoggingEvent> logEvents = listAppender.list;
        assertEquals(2, logEvents.size());

        String localRepoPrefix = "Repository 'local-repo' configuration has been updated. Changes: ";
        String remoteRepoPrefix = "Repository 'remote-repo' configuration has been updated. Changes: ";
        assertTrue(logEvents.stream().anyMatch(event -> event.getFormattedMessage().startsWith(localRepoPrefix)));
        assertTrue(logEvents.stream().anyMatch(event -> event.getFormattedMessage().startsWith(remoteRepoPrefix)));
        String expectedLocalJson = """
                [
                    {
                        "attributeName": "baseConfig%srepoLayoutRef",
                        "oldValue": "oldLayout",
                        "newValue": "newLayout"
                    }
                ]
                """.formatted(D);

        // packageTypeConfig is diffed per-key: each changed entry is its own packageTypeConfig»<key> diff.
        String expectedRemoteJson = """
                [
                    {
                        "attributeName": "packageTypeConfig%sblockPushingSchema1",
                        "oldValue": "true",
                        "newValue": "false"
                    }
                ]
                """.formatted(D);

        for (ILoggingEvent logEvent : logEvents) {
            if (logEvent.getFormattedMessage().startsWith(localRepoPrefix)) {
                String actualLocalJson = logEvent.getFormattedMessage().substring(localRepoPrefix.length());
                JSONAssert.assertEquals(expectedLocalJson, actualLocalJson, false);
            } else if (logEvent.getFormattedMessage().startsWith(remoteRepoPrefix)) {
                String actualRemoteJson = logEvent.getFormattedMessage().substring(remoteRepoPrefix.length());
                JSONAssert.assertEquals(expectedRemoteJson, actualRemoteJson, false);
            }
        }
    }

    private void setupRepos() {
        oldLocalRepoConfig = LocalRepoConfig.create("local-repo", Generic);
        newLocalRepoConfig = LocalRepoConfig.create("local-repo", Generic);
        oldRemoteRepoConfig = RemoteRepoConfig.create("remote-repo", Docker);
        newRemoteRepoConfig = RemoteRepoConfig.create("remote-repo", Docker);

        oldLocalRepoConfig.getBaseConfig().setRepoLayoutRef("oldLayout");
        newLocalRepoConfig.getBaseConfig().setRepoLayoutRef("newLayout");
        newRemoteRepoConfig.getPackageTypeConfig()
                .put(DockerPackageTypeConfigMapper.BLOCK_PUSHING_SCHEMA_1, String.valueOf(false));
    }

    private void setupLogger() {
        Logger logger = (Logger) LoggerFactory.getLogger(LogUpdateRepoConfigChangeInterceptor.class);
        logger.setLevel(Level.INFO);
        listAppender.start();
        logger.addAppender(listAppender);
    }

    private static Stream<Arguments> nonUpdateConfigAction() {
        return Arrays.stream(RepoConfigChange.ConfigAction.values())
                .filter(action -> action != REPO_UPDATE)
                .map(Arguments::of);
    }
}