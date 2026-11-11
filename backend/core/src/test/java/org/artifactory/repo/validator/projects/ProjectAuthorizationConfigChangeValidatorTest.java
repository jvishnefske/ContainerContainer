package org.artifactory.repo.validator.projects;

import jakarta.annotation.Nonnull;
import org.artifactory.api.security.SecurityService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.LocalRepositoryRepoTypeConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectAuthorizationConfigChangeValidatorTest {

    private static final String TEST_USER = "testUser";
    private static final String REPO_KEY = "repoKey";

    @Mock
    private SecurityService securityService;
    @InjectMocks
    private ProjectAuthorizationConfigChangeValidator validator;
    private AutoCloseable mocks;

    private static Stream<Arguments> disallowedRepoTypes() {
        return Stream.of(Arguments.of(FEDERATED));
    }

    private static Stream<Arguments> allowedRepoTypes() {
        return Stream.of(
                Arguments.of(LOCAL),
                Arguments.of(REMOTE),
                Arguments.of(VIRTUAL),
                Arguments.of(RELEASE_BUNDLE)
        );
    }

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(securityService.currentUsername()).thenReturn(TEST_USER);
        setDefaultPermissions();
    }

    private void setDefaultPermissions() {
        givenUserAdmin(false);
        givenUserProjectAdmin(true, RepoPathFactory.create(REPO_KEY));
        givenUserCanManage(true, REPO_KEY);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testValidateAsAdmin() {
        givenUserAdmin(true);

        validator.validate(new RepoConfigChanges(Map.of()));

        verify(securityService, never()).isAnyProjectAdmin();
    }

    @ParameterizedTest
    @MethodSource("allowedRepoTypes")
    void testValidateCreateAllowedRepo(RepoDetailsType repoType) {
        testValidateAllowedRepo(repoType, REPO_CREATE);
    }

    @ParameterizedTest
    @MethodSource("allowedRepoTypes")
    void testValidateUpdateAllowedRepo(RepoDetailsType repoType) {
        testValidateAllowedRepo(repoType, REPO_UPDATE);
    }

    @ParameterizedTest
    @MethodSource("allowedRepoTypes")
    void testValidateDeleteAllowedRepo(RepoDetailsType allowedRepoType) {
        testValidateAllowedRepo(allowedRepoType, REPO_DELETE);
    }

    private void testValidateAllowedRepo(RepoDetailsType repoType, RepoConfigChange.ConfigAction configAction) {
        RepoPath repoPath = RepoPathFactory.create(REPO_KEY);
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges =
                createRepoConfigChanges(repoType, configAction);

        validator.validate(new RepoConfigChanges(repoConfigChanges));

        verify(securityService).isProjectAdmin(repoPath);
    }

    @ParameterizedTest
    @MethodSource("disallowedRepoTypes")
    void testValidateCreateDisallowedRepo(RepoDetailsType repoType) {
        testValidateDisallowedRepo(repoType, REPO_CREATE, "create");
    }

    @ParameterizedTest
    @MethodSource("disallowedRepoTypes")
    void testValidateUpdateDisallowedRepo(RepoDetailsType repoType) {
        testValidateDisallowedRepo(repoType, REPO_UPDATE, "update");
    }

    @ParameterizedTest
    @MethodSource("disallowedRepoTypes")
    void testValidateDeleteDisallowedRepo(RepoDetailsType disallowedRepoType) {
        testValidateDisallowedRepo(disallowedRepoType, REPO_DELETE, "delete");
    }

    private void testValidateDisallowedRepo(RepoDetailsType repoType, RepoConfigChange.ConfigAction configAction,
            String action) {
        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges =
                createRepoConfigChanges(repoType, configAction);
        ConfigurationException configurationException = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(repoConfigChanges)));
        assertEquals(
                "Project admin: ('%s') is not allowed to %s repositories: %s".formatted(TEST_USER, action, REPO_KEY),
                configurationException.getMessage());
    }

    @ParameterizedTest
    @MethodSource("allowedRepoTypes")
    void testValidateCreateUnauthorizedNotProjectAdmin(RepoDetailsType repoDetailsType) {
        testUnauthorizedNotProjectAdmin(repoDetailsType, REPO_CREATE, "create");
    }

    @ParameterizedTest
    @MethodSource("allowedRepoTypes")
    void testValidateUpdateUnauthorizedNotProjectAdmin(RepoDetailsType repoDetailsType) {
        testUnauthorizedNotProjectAdmin(repoDetailsType, REPO_UPDATE, "update");
    }

    @ParameterizedTest
    @MethodSource("allowedRepoTypes")
    void testValidateDeleteUnauthorizedNotProjectAdmin(RepoDetailsType repoDetailsType) {
        testUnauthorizedNotProjectAdmin(repoDetailsType, REPO_DELETE, "delete");
    }

    private void testUnauthorizedNotProjectAdmin(RepoDetailsType repoDetailsType,
            RepoConfigChange.ConfigAction configAction, String action) {
        givenUserProjectAdmin(false, RepoPathFactory.create(REPO_KEY));
        givenUserCanManage(true, REPO_KEY);

        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges =
                createRepoConfigChanges(repoDetailsType, configAction);
        ConfigurationException configurationException = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(repoConfigChanges)));
        assertEquals(
                "User: ('%s') has insufficient permission to %s repositories: %s".formatted(TEST_USER, action,
                        REPO_KEY),
                configurationException.getMessage());
    }

    @ParameterizedTest
    @MethodSource("allowedRepoTypes")
    void testValidateCreateUnauthorizedCannotManage(RepoDetailsType repoDetailsType) {
        testUnauthorizedCannotManage(repoDetailsType, REPO_CREATE, "create");
    }

    @ParameterizedTest
    @MethodSource("allowedRepoTypes")
    void testValidateUpdateUnauthorizedCannotManage(RepoDetailsType repoDetailsType) {
        testUnauthorizedCannotManage(repoDetailsType, REPO_UPDATE, "update");
    }

    @ParameterizedTest
    @MethodSource("allowedRepoTypes")
    void testValidateDeleteUnauthorizedCannotManage(RepoDetailsType repoDetailsType) {
        testUnauthorizedCannotManage(repoDetailsType, REPO_DELETE, "delete");
    }

    private void testUnauthorizedCannotManage(RepoDetailsType repoDetailsType,
            RepoConfigChange.ConfigAction configAction, String action) {
        givenUserProjectAdmin(true, RepoPathFactory.create(REPO_KEY));
        givenUserCanManage(false, REPO_KEY);

        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges =
                createRepoConfigChanges(repoDetailsType, configAction);
        ConfigurationException configurationException = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(repoConfigChanges)));
        assertEquals(
                "User: ('%s') has insufficient permission to %s repositories: %s".formatted(TEST_USER, action,
                        REPO_KEY),
                configurationException.getMessage());
    }

    @Test
    void testOnBeforeDeleteUnauthorizedNotProjectAdminMultiChangesSameType() {
        givenUserProjectAdmin(false, RepoPathFactory.create("repoKey1"));
        givenUserProjectAdmin(false, RepoPathFactory.create("repoKey2"));

        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChangesMap = Map.of(LOCAL, List.of(
                new RepoConfigChange("repoKey1", REPO_DELETE, null, createRepoConfig("repoKey1", LOCAL)),
                new RepoConfigChange("repoKey2", REPO_DELETE, null, createRepoConfig("repoKey2", LOCAL))
        ));

        ConfigurationException configurationException = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(repoConfigChangesMap)));
        assertMultiRepoErrorMessage(configurationException.getMessage(), "delete", "repoKey1", "repoKey2");
    }

    @Test
    void testOnBeforeDeleteUnauthorizedNotProjectAdminMultiChangesDiffType() {
        givenUserProjectAdmin(false, RepoPathFactory.create("repoKey1"));
        givenUserProjectAdmin(false, RepoPathFactory.create("repoKey2"));

        Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChangesMap = Map.of(
                LOCAL, List.of(
                        new RepoConfigChange("repoKey1", REPO_DELETE, null, createRepoConfig("repoKey1", LOCAL))),
                REMOTE, List.of(new RepoConfigChange("repoKey2", REPO_DELETE, null, createRepoConfig("repoKey2", LOCAL))
                ));

        ConfigurationException configurationException = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(repoConfigChangesMap)));
        assertMultiRepoErrorMessage(configurationException.getMessage(), "delete", "repoKey1", "repoKey2");
    }

    private static void assertMultiRepoErrorMessage(String actual, String action, String key1, String key2) {
        String prefix = "User: ('%s') has insufficient permission to %s repositories: ".formatted(TEST_USER, action);
        assertTrue(
                actual.equals(prefix + key1 + "," + key2) || actual.equals(prefix + key2 + "," + key1),
                "Unexpected error message format: <" + actual + ">");
    }

    @Test
    void testValidateUpdateXrayOnlyChange_xrayPermits_passes_andManageNotConsulted() {
        // Diff-first: xray-only change requires ONLY the xray-index permission; the general manage check is
        // not part of the required set and therefore must not be invoked.
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(createXrayIndexChange(REPO_KEY, LOCAL, false, true)));

        validator.validate(new RepoConfigChanges(changes));

        verify(securityService).canXrayIndexProjectRepoResource(REPO_KEY);
        verify(securityService, never()).canDoToRepoProjectResource(any(), eq(REPO_KEY), any());
    }

    @Test
    void testValidateUpdateXrayOnlyChange_manageFails_xrayDenies_throws() {
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(false, REPO_KEY);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(createXrayIndexChange(REPO_KEY, LOCAL, false, true)));

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        assertEquals(
                "User: ('%s') has insufficient permission to update repositories: %s"
                        .formatted(TEST_USER, REPO_KEY),
                ex.getMessage());
    }

    @Test
    void testValidateUpdateMixedBatch_xrayOnlyRelaxes_nonXrayMustPassManage() {
        String noXrayKey = "noChangeRepo";
        givenUserProjectAdmin(true, RepoPathFactory.create(noXrayKey));
        givenUserCanManage(true, noXrayKey);
        // Manage fails for REPO_KEY (xray-only change) but xray index is permitted -> overall pass.
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        RepoConfigChange xrayChange = createXrayIndexChange(REPO_KEY, LOCAL, false, true);
        RepoConfigChange noXrayChange = new RepoConfigChange(noXrayKey, REPO_UPDATE,
                createRepoConfig(noXrayKey, LOCAL), createRepoConfig(noXrayKey, LOCAL));

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL, List.of(xrayChange, noXrayChange));

        validator.validate(new RepoConfigChanges(changes));

        verify(securityService).canDoToRepoProjectResource(any(), eq(noXrayKey), any());
        verify(securityService).canXrayIndexProjectRepoResource(REPO_KEY);
    }

    @Test
    void testValidateUpdateXrayAndOtherChange_requiresBothChecks() {
        // When xray changes alongside another field, both checks must pass: xray-index AND manage.
        // Manage fails -> overall fails.
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(createXrayIndexAndOtherChange(REPO_KEY, LOCAL, false, true)));

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        assertEquals(
                "User: ('%s') has insufficient permission to update repositories: %s"
                        .formatted(TEST_USER, REPO_KEY),
                ex.getMessage());
    }

    @Test
    void testValidateUpdateXrayAndOtherChange_xrayDenies_failsBeforeManage() {
        givenUserCanManage(true, REPO_KEY);
        givenUserCanXrayIndex(false, REPO_KEY);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(createXrayIndexAndOtherChange(REPO_KEY, LOCAL, false, true)));

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        assertEquals(
                "User: ('%s') has insufficient permission to update repositories: %s"
                        .formatted(TEST_USER, REPO_KEY),
                ex.getMessage());
    }

    @Test
    void testValidateUpdateXrayAndOtherChange_bothPermit_passes() {
        givenUserCanManage(true, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(createXrayIndexAndOtherChange(REPO_KEY, LOCAL, false, true)));

        validator.validate(new RepoConfigChanges(changes));

        verify(securityService).canXrayIndexProjectRepoResource(REPO_KEY);
        verify(securityService).canDoToRepoProjectResource(any(), eq(REPO_KEY), any());
    }

    @Test
    void testValidateUpdateNoXrayChange_failsManage_throwsManageError() {
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY); // must NOT save the day — no xray toggle.

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(new RepoConfigChange(REPO_KEY, REPO_UPDATE, createRepoConfig(REPO_KEY, LOCAL),
                        createRepoConfig(REPO_KEY, LOCAL))));

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        assertEquals(
                "User: ('%s') has insufficient permission to update repositories: %s"
                        .formatted(TEST_USER, REPO_KEY),
                ex.getMessage());
        verify(securityService, never()).canXrayIndexProjectRepoResource(any());
    }

    /**
     * When a non-xray sub-config field changes alongside an xray toggle, manage permission is required
     * (in addition to xray-index). With manage denied, the operation must fail.
     * <p>
     * Cases cover LOCAL and REMOTE so both {@code repoTypeConfig} shapes are exercised through the relaxation path.
     */
    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("nonXraySubConfigChanges")
    void testValidateUpdate_nonXraySubConfigChange_requiresManage(String description, RepoDetailsType repoType,
            BiConsumer<RepoConfig, RepoConfig> applyDifference) {
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        RepoConfig oldCfg = createRepoConfigWithXrayIndex(REPO_KEY, repoType, false);
        RepoConfig newCfg = createRepoConfigWithXrayIndex(REPO_KEY, repoType, true);
        applyDifference.accept(oldCfg, newCfg);
        RepoConfigChange change = new RepoConfigChange(REPO_KEY, REPO_UPDATE, newCfg, oldCfg);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(repoType, List.of(change));

        assertThrows(ConfigurationException.class,
                () -> validator.validate(new RepoConfigChanges(changes)),
                "non-xray sub-config diff (" + description + ") must require manage permission");
    }

    private static Stream<Arguments> nonXraySubConfigChanges() {
        BiConsumer<RepoConfig, RepoConfig> baseConfigDiff = (oldCfg, newCfg) -> {
            oldCfg.getBaseConfig().setDescription("old");
            newCfg.getBaseConfig().setDescription("new");
        };
        BiConsumer<RepoConfig, RepoConfig> localRepoTypeConfigDiff = (oldCfg, newCfg) -> {
            ((LocalRepositoryRepoTypeConfig) oldCfg.getRepoTypeConfig()).setBlackedOut(false);
            ((LocalRepositoryRepoTypeConfig) newCfg.getRepoTypeConfig()).setBlackedOut(true);
        };
        BiConsumer<RepoConfig, RepoConfig> remoteRepoTypeConfigDiff = (oldCfg, newCfg) -> {
            ((RemoteRepositoryRepoTypeConfig) oldCfg.getRepoTypeConfig()).setBlackedOut(false);
            ((RemoteRepositoryRepoTypeConfig) newCfg.getRepoTypeConfig()).setBlackedOut(true);
        };
        BiConsumer<RepoConfig, RepoConfig> packageTypeConfigDiff = (oldCfg, newCfg) -> {
            oldCfg.getPackageTypeConfig().put("custom-marker", "old-value");
            newCfg.getPackageTypeConfig().put("custom-marker", "new-value");
        };
        BiConsumer<RepoConfig, RepoConfig> securityNonXrayDiff = (oldCfg, newCfg) -> {
            oldCfg.getSecurityConfig().setHideUnauthorizedResources(false);
            newCfg.getSecurityConfig().setHideUnauthorizedResources(true);
        };
        return Stream.of(
                Arguments.of("BaseConfig differs", LOCAL, baseConfigDiff),
                Arguments.of("RepoTypeConfig differs", LOCAL, localRepoTypeConfigDiff),
                Arguments.of("PackageTypeConfig differs", LOCAL, packageTypeConfigDiff),
                Arguments.of("SecurityConfig non-xray field differs", LOCAL, securityNonXrayDiff),
                Arguments.of("BaseConfig differs", REMOTE, baseConfigDiff),
                Arguments.of("RepoTypeConfig differs", REMOTE, remoteRepoTypeConfigDiff),
                Arguments.of("PackageTypeConfig differs", REMOTE, packageTypeConfigDiff),
                Arguments.of("SecurityConfig non-xray field differs", REMOTE, securityNonXrayDiff)
        );
    }

    @Test
    void testValidateUpdate_nullNewRepoConfig_failsClosed() {
        // Defensively reject changes whose target config is missing — there is nothing to authorize against.
        RepoConfigChange change = new RepoConfigChange(REPO_KEY, REPO_UPDATE, null,
                createRepoConfigWithXrayIndex(REPO_KEY, LOCAL, false));
        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL, List.of(change));

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        assertEquals(
                "User: ('%s') has insufficient permission to update repositories: %s"
                        .formatted(TEST_USER, REPO_KEY),
                ex.getMessage());
        verify(securityService, never()).canXrayIndexProjectRepoResource(any());
        verify(securityService, never()).canDoToRepoProjectResource(any(), eq(REPO_KEY), any());
    }

    /**
     * Regression: a first-time enable of xrayIndex starts from a state where {@code SecurityConfig.xrayConfig == null}.
     * Toggling via {@link org.artifactory.model.SecurityConfig#setXrayIndex(boolean)} lazily allocates a default
     * {@link org.artifactory.model.XrayRepoConfig} on the new side. The relaxation must still apply — the validator
     * MUST NOT misclassify the null-vs-default-instance asymmetry as a non-xray field change.
     */
    @Test
    void testValidateUpdateXrayOnlyChange_firstTimeEnable_oldXrayConfigNull_relaxationApplies() {
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        RepoConfig oldCfg = LocalRepoConfig.create(REPO_KEY, RepoType.Generic);
        RepoConfig newCfg = LocalRepoConfig.create(REPO_KEY, RepoType.Generic);
        newCfg.getSecurityConfig().setXrayIndex(true);
        RepoConfigChange change = new RepoConfigChange(REPO_KEY, REPO_UPDATE, newCfg, oldCfg);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL, List.of(change));

        validator.validate(new RepoConfigChanges(changes));

        verify(securityService).canXrayIndexProjectRepoResource(REPO_KEY);
        verify(securityService, never()).canDoToRepoProjectResource(any(), eq(REPO_KEY), any());
    }

    /**
     * Lockdown: bumping {@code xrayDataTtl} alongside an xrayIndex toggle is NOT part of the relaxation surface.
     * The validator must require manage permission and fail when manage is denied.
     */
    @Test
    void testValidateUpdateXrayIndexAndDataTtlChange_requiresManage() {
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        RepoConfig oldCfg = createRepoConfigWithXrayIndex(REPO_KEY, LOCAL, false);
        RepoConfig newCfg = createRepoConfigWithXrayIndex(REPO_KEY, LOCAL, true);
        oldCfg.getSecurityConfig().setXrayDataTtl(90);
        newCfg.getSecurityConfig().setXrayDataTtl(30);
        RepoConfigChange change = new RepoConfigChange(REPO_KEY, REPO_UPDATE, newCfg, oldCfg);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL, List.of(change));

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        assertEquals(
                "User: ('%s') has insufficient permission to update repositories: %s"
                        .formatted(TEST_USER, REPO_KEY),
                ex.getMessage());
    }

    /**
     * Lockdown: changing {@code xrayNotAvailableMode} alongside an xrayIndex toggle is NOT part of the relaxation
     * surface. The validator must require manage permission and fail when manage is denied.
     */
    @Test
    void testValidateUpdateXrayIndexAndNotAvailableModeChange_requiresManage() {
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        RepoConfig oldCfg = createRepoConfigWithXrayIndex(REPO_KEY, LOCAL, false);
        RepoConfig newCfg = createRepoConfigWithXrayIndex(REPO_KEY, LOCAL, true);
        newCfg.getSecurityConfig().setXrayNotAvailableMode(XrayNotAvailableMode.FAIL_CLOSE);
        RepoConfigChange change = new RepoConfigChange(REPO_KEY, REPO_UPDATE, newCfg, oldCfg);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL, List.of(change));

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        assertEquals(
                "User: ('%s') has insufficient permission to update repositories: %s"
                        .formatted(TEST_USER, REPO_KEY),
                ex.getMessage());
    }

    @Test
    void testValidateUpdateBatch_partialFailures_errorListsOnlyFailedRepos() {
        // Three repos in one submission:
        //   - xrayOnlyOk: xray-only toggle, manage denied but xray-index permitted -> relaxation passes
        //   - failKey1:   no xray change, manage denied                             -> fails manage
        //   - failKey2:   no xray change, manage denied                             -> fails manage
        // The thrown error must list ONLY failKey1 and failKey2, never xrayOnlyOk.
        String xrayOnlyOk = "xrayOnlyOk";
        String failKey1 = "failKey1";
        String failKey2 = "failKey2";
        givenUserProjectAdmin(true, RepoPathFactory.create(xrayOnlyOk));
        givenUserProjectAdmin(true, RepoPathFactory.create(failKey1));
        givenUserProjectAdmin(true, RepoPathFactory.create(failKey2));
        givenUserCanManage(false, xrayOnlyOk);
        givenUserCanXrayIndex(true, xrayOnlyOk);
        givenUserCanManage(false, failKey1);
        givenUserCanManage(false, failKey2);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL, List.of(
                createXrayIndexChange(xrayOnlyOk, LOCAL, false, true),
                new RepoConfigChange(failKey1, REPO_UPDATE, createRepoConfig(failKey1, LOCAL),
                        createRepoConfig(failKey1, LOCAL)),
                new RepoConfigChange(failKey2, REPO_UPDATE, createRepoConfig(failKey2, LOCAL),
                        createRepoConfig(failKey2, LOCAL))));

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        assertMultiRepoErrorMessage(ex.getMessage(), "update", failKey1, failKey2);
        assertFalse(ex.getMessage().contains(xrayOnlyOk),
                "Relaxed xray-only repo must not appear in error: <" + ex.getMessage() + ">");
    }

    @Test
    void testValidateUpdateXrayOnlyChange_userIsNotProjectAdmin_throws() {
        // Defense in depth: xray-only relaxation must also require local isProjectAdmin, even if the per-repo
        // TRIGGER_SECURITY check passes via Access.
        givenUserProjectAdmin(false, RepoPathFactory.create(REPO_KEY));
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(createXrayIndexChange(REPO_KEY, LOCAL, false, true)));

        assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
    }

    /**
     * The relaxation is direction-symmetric: disabling xrayIndex (true → false) must follow the same path
     * as enabling it. Manage denied + xray-index permitted → relaxation passes; manage MUST NOT be consulted.
     */
    @Test
    void testValidateUpdateXrayOnlyChange_disableDirection_relaxationApplies() {
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(createXrayIndexChange(REPO_KEY, LOCAL, true, false)));

        validator.validate(new RepoConfigChanges(changes));

        verify(securityService).canXrayIndexProjectRepoResource(REPO_KEY);
        verify(securityService, never()).canDoToRepoProjectResource(any(), eq(REPO_KEY), any());
    }

    /**
     * Lockdown for the disable direction: manage denied + xray-index denied → throws. Mirrors the enable-direction
     * test, ensuring the failure path is symmetric too.
     */
    @Test
    void testValidateUpdateXrayOnlyChange_disableDirection_xrayDenies_throws() {
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(false, REPO_KEY);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(createXrayIndexChange(REPO_KEY, LOCAL, true, false)));

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        assertEquals(
                "User: ('%s') has insufficient permission to update repositories: %s"
                        .formatted(TEST_USER, REPO_KEY),
                ex.getMessage());
    }

    /**
     * Defensive guard: an UPDATE whose old config is missing has nothing to diff against and must fail closed,
     * regardless of the user's xray-index permission. The xray fallback MUST NOT be consulted.
     */
    @Test
    void testValidateUpdate_nullOldRepoConfig_failsClosed() {
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        RepoConfigChange change = new RepoConfigChange(REPO_KEY, REPO_UPDATE,
                createRepoConfigWithXrayIndex(REPO_KEY, LOCAL, true), null);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL, List.of(change));

        ConfigurationException ex = assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        assertEquals(
                "User: ('%s') has insufficient permission to update repositories: %s"
                        .formatted(TEST_USER, REPO_KEY),
                ex.getMessage());
        verify(securityService, never()).canXrayIndexProjectRepoResource(any());
        verify(securityService, never()).canDoToRepoProjectResource(any(), eq(REPO_KEY), any());
    }

    /**
     * Branch coverage for {@code oldCfg.getSecurityConfig() == null}: the validator can't reason about the xray
     * diff without both SecurityConfigs, so it must fall straight back to the original manage check. With manage
     * denied → throws; xray-index check must not be consulted.
     */
    @Test
    void testValidateUpdate_nullOldSecurityConfig_fallsBackToManage() {
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        RepoConfig oldCfg = mock(RepoConfig.class);
        when(oldCfg.getKey()).thenReturn(REPO_KEY);
        when(oldCfg.getRepoType()).thenReturn(LOCAL);
        when(oldCfg.getSecurityConfig()).thenReturn(null);
        RepoConfig newCfg = createRepoConfigWithXrayIndex(REPO_KEY, LOCAL, true);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(new RepoConfigChange(REPO_KEY, REPO_UPDATE, newCfg, oldCfg)));

        assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        verify(securityService, never()).canXrayIndexProjectRepoResource(any());
        verify(securityService).canDoToRepoProjectResource(any(), eq(REPO_KEY), any());
    }

    /**
     * Branch coverage for {@code newCfg.getSecurityConfig() == null}: same fall-back behavior as the null-old case.
     */
    @Test
    void testValidateUpdate_nullNewSecurityConfig_fallsBackToManage() {
        givenUserCanManage(false, REPO_KEY);
        givenUserCanXrayIndex(true, REPO_KEY);

        RepoConfig oldCfg = createRepoConfigWithXrayIndex(REPO_KEY, LOCAL, false);
        RepoConfig newCfg = mock(RepoConfig.class);
        when(newCfg.getKey()).thenReturn(REPO_KEY);
        when(newCfg.getRepoType()).thenReturn(LOCAL);
        when(newCfg.getSecurityConfig()).thenReturn(null);

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(new RepoConfigChange(REPO_KEY, REPO_UPDATE, newCfg, oldCfg)));

        assertThrows(ConfigurationException.class, () ->
                validator.validate(new RepoConfigChanges(changes)));
        verify(securityService, never()).canXrayIndexProjectRepoResource(any());
        verify(securityService).canDoToRepoProjectResource(any(), eq(REPO_KEY), any());
    }

    /**
     * Happy path for the unchanged-xray branch: when xrayIndex did not change and manage is permitted, the update
     * passes WITHOUT consulting the xray-index permission. This locks down that the manage fallback is the
     * sole authority for non-xray updates.
     */
    @Test
    void testValidateUpdateNoXrayChange_managePermits_passes_andXrayNotConsulted() {
        givenUserCanManage(true, REPO_KEY);
        givenUserCanXrayIndex(false, REPO_KEY);

        RepoConfig oldCfg = createRepoConfigWithXrayIndex(REPO_KEY, LOCAL, true);
        RepoConfig newCfg = createRepoConfigWithXrayIndex(REPO_KEY, LOCAL, true);
        newCfg.getBaseConfig().setDescription("new-description");

        Map<RepoDetailsType, List<RepoConfigChange>> changes = Map.of(LOCAL,
                List.of(new RepoConfigChange(REPO_KEY, REPO_UPDATE, newCfg, oldCfg)));

        validator.validate(new RepoConfigChanges(changes));

        verify(securityService).canDoToRepoProjectResource(any(), eq(REPO_KEY), any());
        verify(securityService, never()).canXrayIndexProjectRepoResource(any());
    }

    private void givenUserCanManage(boolean canManage, String repoKey) {
        when(securityService.canDoToRepoProjectResource(any(), eq(repoKey), any())).thenReturn(canManage);
    }

    private void givenUserCanXrayIndex(boolean canIndex, String repoKey) {
        when(securityService.canXrayIndexProjectRepoResource(eq(repoKey))).thenReturn(canIndex);
    }

    private void givenUserAdmin(boolean value) {
        when(securityService.isAdmin()).thenReturn(value);
    }

    private static RepoConfig createRepoConfig(String repoKey, RepoDetailsType repoType) {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repoConfig.getRepoType()).thenReturn(repoType);
        return repoConfig;
    }

    /**
     * Builds a real {@link RepoConfig} (so {@link org.artifactory.model.RepoConfigCopyFactory} can deep-copy it
     * and the sub-config equality used by the validator works as intended). Supports LOCAL and REMOTE — both flow
     * through the relaxation path and have distinct {@code repoTypeConfig} types worth covering in the lockdown.
     */
    private static RepoConfig createRepoConfigWithXrayIndex(String repoKey, RepoDetailsType repoType,
            boolean xrayIndex) {
        RepoConfig cfg;
        switch (repoType) {
            case LOCAL -> cfg = LocalRepoConfig.create(repoKey, RepoType.Generic);
            case REMOTE -> cfg = RemoteRepoConfig.create(repoKey, RepoType.Generic);
            default -> throw new IllegalArgumentException("Unsupported repo type for fixture: " + repoType);
        }
        cfg.getSecurityConfig().setXrayIndex(xrayIndex);
        return cfg;
    }

    private static RepoConfigChange createXrayIndexChange(String repoKey, RepoDetailsType repoType,
            boolean oldXrayIndex, boolean newXrayIndex) {
        return new RepoConfigChange(repoKey, REPO_UPDATE,
                createRepoConfigWithXrayIndex(repoKey, repoType, newXrayIndex),
                createRepoConfigWithXrayIndex(repoKey, repoType, oldXrayIndex));
    }

    private static RepoConfigChange createXrayIndexAndOtherChange(String repoKey, RepoDetailsType repoType,
            boolean oldXrayIndex, boolean newXrayIndex) {
        RepoConfig oldConfig = createRepoConfigWithXrayIndex(repoKey, repoType, oldXrayIndex);
        RepoConfig newConfig = createRepoConfigWithXrayIndex(repoKey, repoType, newXrayIndex);
        oldConfig.getBaseConfig().setDescription("old-description");
        newConfig.getBaseConfig().setDescription("new-description");
        return new RepoConfigChange(repoKey, REPO_UPDATE, newConfig, oldConfig);
    }

    private void givenUserProjectAdmin(boolean projectAdmin, RepoPath repoPath) {
        when(securityService.isProjectAdmin(repoPath)).thenReturn(projectAdmin);
    }

    @Nonnull
    private static Map<RepoDetailsType, List<RepoConfigChange>> createRepoConfigChanges(RepoDetailsType repoType,
            RepoConfigChange.ConfigAction configAction) {
        RepoConfigChange change = switch (configAction) {
            case REPO_DELETE -> new RepoConfigChange(REPO_KEY, configAction, null,
                    createRepoConfig(REPO_KEY, repoType));
            case REPO_UPDATE -> new RepoConfigChange(REPO_KEY, configAction,
                    createRepoConfig(REPO_KEY, repoType), createRepoConfig(REPO_KEY, repoType));
            default -> new RepoConfigChange(REPO_KEY, configAction,
                    createRepoConfig(REPO_KEY, repoType), null);
        };
        return Map.of(repoType, List.of(change));
    }
}