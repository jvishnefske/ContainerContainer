package org.artifactory.security.access.migration.populator;


import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

import static org.artifactory.config.AccessConfigKeys.SECURITY;
import static org.artifactory.config.AccessConfigKeys.SECURITY_ANON_ACCESS_ENABLED;
import static org.artifactory.config.AccessConfigKeys.SECURITY_BASIC_AUTH_ENABLED;
import static org.artifactory.config.AccessConfigKeys.SECURITY_USER_LOCK_POLICY;
import static org.artifactory.config.AccessConfigKeys.USER_LOCK_POLICY_ENABLED;
import static org.artifactory.config.AccessConfigKeys.USER_LOCK_POLICY_LOGIN_ATTEMPTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class PlatformConfigMigrationHandlerTest {

    @Mock
    private ArtifactoryApplicationContext context;
    @Mock
    private AccessService accessService;

    private PlatformConfigMigrationHandler populator;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        populator = new PlatformConfigMigrationHandler(accessService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void moveTwoValuesConfigResources() {
        Map<String, Object> yamlConfigMapMutable = toMutableMap(Map.of(SECURITY, Map.of(
                        SECURITY_USER_LOCK_POLICY, Map.of(
                                USER_LOCK_POLICY_ENABLED, true,
                                USER_LOCK_POLICY_LOGIN_ATTEMPTS, 3
                        ),
                        SECURITY_ANON_ACCESS_ENABLED, true
                )
        ));

        int movedResources = populator.migrate(yamlConfigMapMutable);

        assertEquals(2, movedResources);
        assertTrue(((Map<?, ?>) yamlConfigMapMutable.get(SECURITY)).isEmpty());
    }

    @Test
    void moveOneValuesConfigResourcesAndKeepOne() {
        Map<String, Object> yamlConfigMapMutable = toMutableMap(Map.of(
                "urlBase", "someStuff",
                SECURITY, Map.of(
                        SECURITY_USER_LOCK_POLICY, Map.of(
                                USER_LOCK_POLICY_ENABLED, true,
                                USER_LOCK_POLICY_LOGIN_ATTEMPTS, 3
                        )
                )
        ));

        int movedResources = populator.migrate(yamlConfigMapMutable);

        assertEquals(1, movedResources);
        assertEquals(2, yamlConfigMapMutable.size());
        assertEquals("someStuff", yamlConfigMapMutable.get("urlBase"));
        verify(accessService).updateConfigurationModel(any());
    }

    @Test
    void moveUserLockPolicyEnableOnly() {
        Map<String, Object> yamlConfigMapMutable = toMutableMap(Map.of(
                SECURITY, Map.of(
                        SECURITY_USER_LOCK_POLICY, Map.of(
                                USER_LOCK_POLICY_ENABLED, true
                        )
                )
        ));
        int movedResources = populator.migrate(yamlConfigMapMutable);
        assertEquals(0, movedResources);
        assertEquals(1, yamlConfigMapMutable.size());
        verify(accessService, never()).updateConfigurationModel(any());
    }

    @Test
    void moveUserLockPolicyZeroAttemptsOnly() {
        Map<String, Object> yamlConfigMapMutable = toMutableMap(Map.of(
                SECURITY, Map.of(
                        SECURITY_USER_LOCK_POLICY, Map.of(
                                USER_LOCK_POLICY_LOGIN_ATTEMPTS, 0
                        )
                )
        ));
        int movedResources = populator.migrate(yamlConfigMapMutable);
        assertEquals(1, movedResources);
        assertEquals(1, yamlConfigMapMutable.size());
        assertTrue(((Map<?, ?>) yamlConfigMapMutable.get(SECURITY)).isEmpty());
    }

    @Test
    void moveUserLockPolicyFiveAttemptsOnly() {
        Map<String, Object> yamlConfigMapMutable = toMutableMap(Map.of(
                SECURITY, Map.of(
                        SECURITY_USER_LOCK_POLICY, Map.of(
                                USER_LOCK_POLICY_LOGIN_ATTEMPTS, 5
                        )
                )
        ));
        int movedResources = populator.migrate(yamlConfigMapMutable);
        assertEquals(0, movedResources);
        assertEquals(1, yamlConfigMapMutable.size());
        assertTrue(((Map<?, ?>) yamlConfigMapMutable.get(SECURITY)).isEmpty());
    }

    @Test
    void moveUserLockPolicyDisableOnly() {
        Map<String, Object> yamlConfigMapMutable = toMutableMap(Map.of(
                SECURITY, Map.of(
                        SECURITY_USER_LOCK_POLICY, Map.of(
                                USER_LOCK_POLICY_ENABLED, false
                        )
                )
        ));
        int movedResources = populator.migrate(yamlConfigMapMutable);
        assertEquals(1, movedResources);
        assertEquals(1, yamlConfigMapMutable.size());
        assertTrue(((Map<?, ?>) yamlConfigMapMutable.get(SECURITY)).isEmpty());
    }

    @Test
    void moveOneValueConfigResources() {
        Map<String, Object> yamlConfigMapMutable = toMutableMap(Map.of(SECURITY, Map.of(
                        SECURITY_ANON_ACCESS_ENABLED, true
                )
        ));

        int movedResources = populator.migrate(yamlConfigMapMutable);
        assertEquals(1, movedResources);
    }

    @Disabled("the test case is flaky")
    @Test
    void moveBasicAuthDisabledWhenNoExternalAuthProvidersFails() {
        Map<String, Object> yamlConfigMapMutable = toMutableMap(Map.of(SECURITY, Map.of(
                        SECURITY_BASIC_AUTH_ENABLED, false
                )
        ));

        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class, RETURNS_DEEP_STUBS);
        when(centralConfigDescriptor.getSecurity().isAnyExternalAuthEnabled()).thenReturn(false);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(accessService.isAnyExternalAuthEnabled()).thenReturn(false);
        when(context.beanForType(AccessService.class)).thenReturn(accessService);
        int configResourcesMoved = populator.migrate(yamlConfigMapMutable);
        assertEquals(0, configResourcesMoved,
                "Should not be able to turn off basic auth when no external authentication providers exist");
    }

    @Nonnull
    private static Map<String, Object> toMutableMap(Map<String, Object> yamlConfigMap) {
        return JsonUtils.getInstance().readValue(
                JsonUtils.getInstance().valueToString(yamlConfigMap), HashMap.class);
    }
}