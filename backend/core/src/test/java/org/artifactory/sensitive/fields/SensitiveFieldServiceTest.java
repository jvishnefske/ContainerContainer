package org.artifactory.sensitive.fields;

import org.artifactory.api.config.DynamicSensitiveKeyRotationCapable;
import org.artifactory.api.config.DynamicSensitiveValueHandler;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.SecurityConfig;
import org.artifactory.model.SpecificTypeRepositoryConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.remote.CustomHttpHeader;
import org.artifactory.sensitive.CustomHttpHeadersSensitiveHandler;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigHelper;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.result.DecryptionStringResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration-style tests for {@link CryptoServiceImpl} with real {@link SensitiveStringFieldHandler} and
 * {@link DynamicSensitiveFieldHandler} wiring. Covers discovery on {@link RemoteRepoConfig} (including
 * {@code customHttpHeaders}), {@link org.artifactory.sensitive.Sensitive} String fields via test holders, and
 * {@link org.artifactory.sensitive.DynamicSensitive} delegation including key rotation.
 *
 * @author Fawaz Atwa
 */
class SensitiveFieldServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private CryptoServiceImpl handler;
    private SensitiveFieldHandler sensitiveFieldHandlerMock;

    @BeforeEach
    void setUp() {
        bindArtifactoryContextWithDynamicSensitiveHandlers();
        sensitiveFieldHandlerMock = mock(SensitiveFieldHandler.class);
        SensitiveStringFieldHandler sensitiveStringFieldHandler = new SensitiveStringFieldHandler();
        sensitiveStringFieldHandler.init();
        handler = new CryptoServiceImpl(
                List.of(sensitiveStringFieldHandler, new DynamicSensitiveFieldHandler(), sensitiveFieldHandlerMock));
    }

    /**
     * {@link DynamicSensitiveFieldHandler} resolves handlers only from Spring; tests register no-arg instances here.
     */
    @SuppressWarnings("unchecked")
    private static void bindArtifactoryContextWithDynamicSensitiveHandlers() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type == TestDtoHandler.class) {
                return new TestDtoHandler();
            }
            if (type == TestDtoHandlerWithoutKeyRotation.class) {
                return new TestDtoHandlerWithoutKeyRotation();
            }
            if (type == TestDynamicSensitiveHandlerForString.class) {
                return new TestDynamicSensitiveHandlerForString();
            }
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
    void discoverSensitiveFieldsOnRemoteRepoConfigFindsDynamicSensitiveHeaders() {
        RemoteRepoConfig config = RemoteRepoConfig.create("repo", RepoType.Generic);
        config.getRepoTypeConfig().setPassword("secret");

        List<SensitiveFieldDescriptor> descriptors = handler.discoverSensitiveFields(config);

        assertTrue(descriptors.stream().anyMatch(
                descriptor -> "customHttpHeaders".equals(descriptor.getField().getName())));
    }

    @Test
    void validateOnConfigWithoutSensitiveFieldsDoesNotThrow() {
        RemoteRepoConfig config = RemoteRepoConfig.create("repo", RepoType.Generic);
        config.getRepoTypeConfig().setPassword("plain");

        assertDoesNotThrow(() -> handler.validate(config));
    }

    @Test
    void encryptSensitiveFieldsOnConfigWithoutSensitiveFieldsDoesNotThrow() {
        // A local repo config has no @Sensitive/@DynamicSensitive fields, so encryption is a no-op and never
        // touches the key (remote password is now @Sensitive, so a remote config no longer fits this case).
        LocalRepoConfig config = LocalRepoConfig.create("repo", RepoType.Generic);

        assertDoesNotThrow(() -> handler.encryptSensitiveFields(config));
    }

    @Test
    void decryptSensitiveFieldsWhenNoKeyDoesNotThrow() {
        RemoteRepoConfig config = RemoteRepoConfig.create("repo", RepoType.Generic);
        config.getRepoTypeConfig().setPassword("plain");

        assertDoesNotThrow(() -> handler.decryptSensitiveFields(config));
    }

    @Test
    void maskSensitiveFieldsOnConfigWithoutSensitiveFieldsDoesNotThrow() {
        RemoteRepoConfig config = RemoteRepoConfig.create("repo", RepoType.Generic);
        config.getRepoTypeConfig().setPassword("plain");

        assertDoesNotThrow(() -> handler.maskSensitiveFields(config));
    }

    /**
     * Design: @Sensitive must be used only on String fields. Discovery throws if used on non-String.
     * Uses concrete TestRepoConfig so reflection can traverse from root into baseConfig.
     */
    @Test
    void discoverSensitiveFieldsThrowsWhenSensitiveAnnotationIsOnNonStringField() {
        RepoConfig config = new TestRepoConfig(new TestBaseConfigWithSensitiveOnNonString());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handler.discoverSensitiveFields(config));
        assertTrue(ex.getMessage().contains("@Sensitive must be used only on String fields"));
        assertTrue(ex.getMessage().contains("Integer"));
    }

    // ---------- @Sensitive on String: encrypt, mask, decrypt behavior ----------

    @Test
    void encryptSensitiveFieldsOnStringFieldWithKeyEncryptsValue() {
        bindMockArtifactoryHomeForStringCrypto();
        CryptoServiceImpl localHandler = newHandlerWithFreshStringHandler();
        RepoConfig config = configWithSensitiveString("plain");
        localHandler.encryptSensitiveFields(config);
        assertEquals("JEencrypted", getSensitiveStringFromConfig(config));
    }

    @Test
    void maskSensitiveFieldsOnStringFieldReplacesWithMask() {
        RepoConfig config = configWithSensitiveString("secret");
        handler.maskSensitiveFields(config);
        assertEquals("***", getSensitiveStringFromConfig(config));
    }

    @Test
    void decryptSensitiveFieldsOnStringFieldWithKeyDecryptsValue() {
        bindMockArtifactoryHomeForStringCrypto();
        CryptoServiceImpl localHandler = newHandlerWithFreshStringHandler();
        // Same shape as EncryptRepoConfigInterceptorTest (Artifactory-key-encrypted value)
        RepoConfig config = configWithSensitiveString("JE2f7DvoFNZ77qa4udE4LxVi8zsZ3q9tEzk5kaYJLgoLJYwdPm");
        localHandler.decryptSensitiveFields(config);
        assertEquals("plain", getSensitiveStringFromConfig(config));
    }

    /**
     * Re-encrypt = decrypt then encrypt (same sequence as {@link org.artifactory.api.crypto.CryptoService#reEncrypt}).
     * Verifies that after re-encrypt the value is encrypted again and round-trips to plain.
     */
    @Test
    void reEncryptFlowDecryptThenEncryptOnStringField() {
        bindMockArtifactoryHomeForStringCrypto();
        CryptoServiceImpl localHandler = newHandlerWithFreshStringHandler();
        RepoConfig config = configWithSensitiveString("JE2f7DvoFNZ77qa4udE4LxVi8zsZ3q9tEzk5kaYJLgoLJYwdPm");
        localHandler.decryptSensitiveFields(config);
        assertEquals("plain", getSensitiveStringFromConfig(config));
        localHandler.encryptSensitiveFields(config);
        assertEquals("JEencrypted", getSensitiveStringFromConfig(config));
        localHandler.decryptSensitiveFields(config);
        assertEquals("plain", getSensitiveStringFromConfig(config));
    }

    // ---------- @DynamicSensitive on String: warning and skip (no throw, field ignored) ----------

    @Test
    void validateThrowsWhenDynamicSensitiveOnStringField() {
        RepoConfig config = new TestRepoConfig(new TestBaseConfigWithDynamicSensitiveOnString());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> handler.validate(config));
        assertTrue(ex.getMessage().contains("@DynamicSensitive"));
        assertTrue(ex.getMessage().contains("String"));
    }

    // ---------- @DynamicSensitive on object: handler validate/encrypt/decrypt/mask ----------

    @Test
    void dynamicSensitiveObjectFieldDelegatesToHandler() {
        bindArtifactoryHomeWithKey();
        TestDto dto = new TestDto("plain");
        RepoConfig config = configWithDynamicSensitiveDto(dto);
        handler.validate(config);
        handler.encryptSensitiveFields(config);
        assertEquals("encrypted", dto.getValue());
        handler.decryptSensitiveFields(config);
        assertEquals("decrypted", dto.getValue());
        handler.maskSensitiveFields(config);
        assertEquals("***", ((TestBaseConfigWithDynamicSensitiveDto) config.getBaseConfig()).getDto().getValue());
    }

    @Test
    void encryptSensitiveFieldsForKeyRotationDelegatesToKeyRotationCapableDynamicHandler() {
        EncryptionWrapper newWrapper = mock(EncryptionWrapper.class);
        when(newWrapper.encryptIfNeeded(anyString())).thenReturn("art-new");
        TestDto dto = new TestDto("plain");
        RepoConfig config = configWithDynamicSensitiveDto(dto);
        handler.reEncryptSensitiveFields(config, newWrapper);
        assertEquals("key-rotation-reencrypted", dto.getValue());
        verify(newWrapper).encryptIfNeeded(anyString());
    }

    @Test
    void encryptSensitiveFieldsForKeyRotationThrowsWhenDynamicHandlerDoesNotSupportRotation() {
        EncryptionWrapper newWrapper = mock(EncryptionWrapper.class);
        TestDto dto = new TestDto("x");
        RepoConfig config = configWithDynamicSensitiveDtoWithoutRotation(dto);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> handler.reEncryptSensitiveFields(config, newWrapper));
        assertTrue(ex.getMessage().contains("Key rotation is not supported"));
    }

    @Test
    void encryptSensitiveFieldsSkipsEncryptionWhenKeyMissing() {
        bindArtifactoryHomeWithoutKey();
        CryptoServiceImpl localHandler = newHandlerWithFreshStringHandler();

        RepoConfig config = configWithSensitiveString("secret");
        assertDoesNotThrow(() -> localHandler.encryptSensitiveFields(config));
        assertEquals("secret", getSensitiveStringFromConfig(config));
    }

    @Test
    void encryptSensitiveFieldsSkipsDynamicSensitiveEncryptionWhenKeyMissing() {
        bindArtifactoryHomeWithoutKey();
        CryptoServiceImpl localHandler = newHandlerWithFreshStringHandler();

        RemoteRepoConfig config = RemoteRepoConfig.create("repo", RepoType.Generic);
        config.getRepoTypeConfig().setPassword("secret-password");
        config.getRepoTypeConfig().setCustomHttpHeaders(
                List.of(new CustomHttpHeader("x-auth", "header-secret", true)));

        assertDoesNotThrow(() -> localHandler.encryptSensitiveFields(config));
        assertEquals("secret-password", config.getRepoTypeConfig().getPassword());
        assertEquals("header-secret", config.getRepoTypeConfig().getCustomHttpHeaders().getFirst().getValue());
    }

    private void bindArtifactoryHomeWithoutKey() {
        ArtifactoryHome home = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties props = mock(ArtifactorySystemProperties.class);
        File keyFile = mock(File.class);
        when(props.getLongProperty(ConstantValues.sensitiveRepoConfigMaxRecursionDepth)).thenReturn(20L);
        when(home.getArtifactoryProperties()).thenReturn(props);
        when(home.getArtifactoryKey()).thenReturn(keyFile);
        when(keyFile.exists()).thenReturn(false);
        ArtifactoryHome.bind(home);
    }

    private void bindArtifactoryHomeWithKey() {
        ArtifactoryHome home = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties props = mock(ArtifactorySystemProperties.class);
        File keyFile = mock(File.class);
        when(props.getLongProperty(ConstantValues.sensitiveRepoConfigMaxRecursionDepth)).thenReturn(20L);
        when(home.getArtifactoryProperties()).thenReturn(props);
        when(home.getArtifactoryKey()).thenReturn(keyFile);
        when(keyFile.exists()).thenReturn(true);
        ArtifactoryHome.bind(home);
    }

    private void bindMockArtifactoryHomeForStringCrypto() {
        ArtifactoryHome home = mock(ArtifactoryHome.class);
        ArtifactoryHome.bind(home);
        File key = mock(File.class);
        when(home.getArtifactoryKey()).thenReturn(key);
        when(key.exists()).thenReturn(true);
        ArtifactorySystemProperties props = mock(ArtifactorySystemProperties.class);
        when(props.getLongProperty(ConstantValues.sensitiveRepoConfigMaxRecursionDepth)).thenReturn(20L);
        when(home.getArtifactoryProperties()).thenReturn(props);
        EncryptionWrapper encryptionWrapper = mock(EncryptionWrapper.class);
        when(encryptionWrapper.encryptIfNeeded(anyString())).thenReturn("JEencrypted");
        DecryptionStringResult decryptionStringResult = mock(DecryptionStringResult.class);
        when(decryptionStringResult.getDecryptedData()).thenReturn("plain");
        when(encryptionWrapper.decryptIfNeeded(anyString())).thenReturn(decryptionStringResult);
        when(home.getMasterEncryptionWrapper()).thenReturn(encryptionWrapper);
        when(home.getArtifactoryEncryptionWrapper()).thenReturn(encryptionWrapper);
        SysConfig sysConfig = mock(SysConfig.class);
        SysConfigHelper sysConfigHelper = mock(SysConfigHelper.class);
        when(sysConfigHelper.getMasterKey()).thenReturn(Optional.of(""));
        when(sysConfig.helper()).thenReturn(sysConfigHelper);
        when(home.getSysConfig()).thenReturn(sysConfig);
    }

    private CryptoServiceImpl newHandlerWithFreshStringHandler() {
        SensitiveStringFieldHandler sensitiveStringFieldHandler = new SensitiveStringFieldHandler();
        sensitiveStringFieldHandler.init();
        return new CryptoServiceImpl(
                List.of(sensitiveStringFieldHandler, new DynamicSensitiveFieldHandler(), sensitiveFieldHandlerMock));
    }

    private RepoConfig configWithSensitiveString(String value) {
        TestBaseConfigWithSensitiveString base = new TestBaseConfigWithSensitiveString();
        base.setSecret(value);
        return new TestRepoConfig(base);
    }

    private String getSensitiveStringFromConfig(RepoConfig config) {
        return ((TestBaseConfigWithSensitiveString) config.getBaseConfig()).getSecret();
    }

    private RepoConfig configWithDynamicSensitiveDto(TestDto dto) {
        TestBaseConfigWithDynamicSensitiveDto base = new TestBaseConfigWithDynamicSensitiveDto();
        base.setDto(dto);
        return new TestRepoConfig(base);
    }

    private RepoConfig configWithDynamicSensitiveDtoWithoutRotation(TestDto dto) {
        when(sensitiveFieldHandlerMock.supports(any(SensitiveFieldDescriptor.class))).thenReturn(true);
        TestBaseConfigWithDynamicSensitiveNoRotation base = new TestBaseConfigWithDynamicSensitiveNoRotation();
        base.setDto(dto);
        return new TestRepoConfig(base);
    }

    /**
     * Minimal RepoConfig implementation with real fields so reflection can traverse from root
     * into baseConfig and discover @Sensitive / @DynamicSensitive fields.
     */
    private static class TestRepoConfig implements RepoConfig {
        private final BaseConfig baseConfig;

        TestRepoConfig(BaseConfig baseConfig) {
            this.baseConfig = baseConfig;
        }

        @Override
        public String getKey() {
            return "test-repo";
        }

        @Override
        public BaseConfig getBaseConfig() {
            return baseConfig;
        }

        @Override
        public SpecificTypeRepositoryConfig getRepoTypeConfig() {
            return null;
        }

        @Override
        public Map<String, String> getPackageTypeConfig() {
            return Collections.emptyMap();
        }

        @Override
        public SecurityConfig getSecurityConfig() {
            return null;
        }

        @Override
        public boolean isReal() {
            return true;
        }

        @Override
        public RepoType getPackageType() {
            return RepoType.Generic;
        }

        @Override
        public RepoDetailsType getRepoType() {
            return RepoDetailsType.LOCAL;
        }

        @Override
        public long getModification() {
            return 0L;
        }

        @Override
        public void setModification(long date) {
        }

        @Override
        public long getCreation() {
            return 0L;
        }

        @Override
        public void setCreation(long date) {
        }
    }

    /**
     * Handler for testing @DynamicSensitive on String (wrong usage); discovery throws so this is never invoked.
     */
    public static class TestDynamicSensitiveHandlerForString implements DynamicSensitiveValueHandler<String> {
        @Override
        public void validate(String value) {
        }

        @Override
        public void encrypt(String value) {
        }

        @Override
        public void decrypt(String value) {
        }

        @Override
        public String mask(String value) {
            return value;
        }
    }

    /**
     * Simple DTO for testing @DynamicSensitive on object with handler.
     */
    static class TestDto {
        private String value;

        TestDto(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }

        void setValue(String value) {
            this.value = value;
        }
    }

    /**
     * Handler for TestDto: encrypt -> "encrypted", decrypt -> "decrypted", mask -> "***".
     */
    public static class TestDtoHandler implements DynamicSensitiveKeyRotationCapable<TestDto> {
        @Override
        public void validate(TestDto value) {
        }

        @Override
        public void encrypt(TestDto value) {
            if (value != null) {
                value.setValue("encrypted");
            }
        }

        @Override
        public void decrypt(TestDto value) {
            if (value != null) {
                value.setValue("decrypted");
            }
        }

        @Override
        public TestDto mask(TestDto value) {
            return value == null ? null : new TestDto("***");
        }

        @Override
        public void encryptForKeyRotation(EncryptionWrapper newArtifactoryWrapper, TestDto value) {
            if (value != null) {
                newArtifactoryWrapper.encryptIfNeeded(value.getValue());
                value.setValue("key-rotation-reencrypted");
            }
        }
    }

    /**
     * Handler without {@link DynamicSensitiveKeyRotationCapable} — used to assert fail-fast on rotation.
     */
    public static class TestDtoHandlerWithoutKeyRotation implements DynamicSensitiveValueHandler<TestDto> {
        @Override
        public void validate(TestDto value) {
        }

        @Override
        public void encrypt(TestDto value) {
        }

        @Override
        public void decrypt(TestDto value) {
        }

        @Override
        public TestDto mask(TestDto value) {
            return value;
        }
    }
}
