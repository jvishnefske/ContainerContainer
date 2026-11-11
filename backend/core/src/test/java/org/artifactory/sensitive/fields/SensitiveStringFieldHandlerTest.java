package org.artifactory.sensitive.fields;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.api.config.DynamicSensitiveKeyRotationCapable;
import org.artifactory.sensitive.DynamicSensitive;
import org.artifactory.sensitive.Sensitive;
import org.artifactory.sensitive.fields.SensitiveFieldDescriptor.DynamicSensitiveObjectDescriptor;
import org.artifactory.sensitive.fields.SensitiveFieldDescriptor.SensitiveStringDescriptor;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigHelper;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.jfrog.security.crypto.result.DecryptionStringResult;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused tests for {@link SensitiveStringFieldHandler} (in addition to coverage via
 * {@link SensitiveFieldServiceTest}).
 */
class SensitiveStringFieldHandlerTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void supportsSensitiveStringDescriptorOnly() throws Exception {
        SensitiveStringFieldHandler handler = newHandler();
        Field stringField = StringHolder.class.getDeclaredField("secret");
        SensitiveStringDescriptor ok = new SensitiveStringDescriptor(new StringHolder(),
                stringField, stringField.getAnnotation(Sensitive.class), "secret");
        assertTrue(handler.supports(ok));

        Field dynField = DynamicHolder.class.getDeclaredField("payload");
        DynamicSensitiveObjectDescriptor dyn = new DynamicSensitiveObjectDescriptor(new DynamicHolder(),
                dynField, dynField.getAnnotation(DynamicSensitive.class), "payload");
        assertFalse(handler.supports(dyn));
    }

    @Test
    void validateInternalAcceptsStringField() throws Exception {
        SensitiveStringFieldHandler handler = newHandler();
        Field field = StringHolder.class.getDeclaredField("secret");
        SensitiveStringDescriptor d = new SensitiveStringDescriptor(new StringHolder(), field,
                field.getAnnotation(Sensitive.class), "secret");
        handler.validateInternal(d);
    }

    @Test
    void validateInternalRejectsNonStringField() throws Exception {
        SensitiveStringFieldHandler handler = newHandler();
        Field field = BadIntegerHolder.class.getDeclaredField("bad");
        SensitiveStringDescriptor d = new SensitiveStringDescriptor(new BadIntegerHolder(), field,
                field.getAnnotation(Sensitive.class), "bad");
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> handler.validateInternal(d));
        assertTrue(ex.getMessage().contains("@Sensitive must be used only on String fields"));
        assertTrue(ex.getMessage().contains("Integer"));
    }

    @Test
    void encryptDecryptMaskAndKeyRotationRoundTrip() throws Exception {
        bindMockArtifactoryHomeForStringCrypto();
        SensitiveStringFieldHandler handler = newHandler();
        StringHolder holder = new StringHolder();
        holder.setSecret("plain");
        Field field = StringHolder.class.getDeclaredField("secret");
        SensitiveStringDescriptor d = new SensitiveStringDescriptor(holder, field,
                field.getAnnotation(Sensitive.class), "secret");

        handler.encryptInternal(d);
        assertEquals("JEencrypted", holder.getSecret());

        handler.decryptInternal(d);
        assertEquals("plain", holder.getSecret());

        holder.setSecret("secret");
        handler.maskInternal(d);
        assertEquals("***", holder.getSecret());

        holder.setSecret("plain");
        handler.encryptInternal(d);
        EncryptionWrapper rotationWrapper = mock(EncryptionWrapper.class);
        when(rotationWrapper.encryptIfNeeded(anyString())).thenReturn("rotated");
        handler.encryptForKeyRotationInternal(rotationWrapper, d);
        assertEquals("rotated", holder.getSecret());
        verify(rotationWrapper).encryptIfNeeded(anyString());
    }

    @Test
    void encryptInternalSkipsBlank() throws Exception {
        bindMockArtifactoryHomeForStringCrypto();
        SensitiveStringFieldHandler handler = newHandler();
        StringHolder holder = new StringHolder();
        holder.setSecret("   ");
        Field field = StringHolder.class.getDeclaredField("secret");
        SensitiveStringDescriptor d = new SensitiveStringDescriptor(holder, field,
                field.getAnnotation(Sensitive.class), "secret");
        handler.encryptInternal(d);
        assertEquals("   ", holder.getSecret());
    }

    @Test
    void encryptThrowsWhenDescriptorTypeWrong() throws Exception {
        SensitiveStringFieldHandler handler = newHandler();
        Field dynField = DynamicHolder.class.getDeclaredField("payload");
        DynamicSensitiveObjectDescriptor wrong = new DynamicSensitiveObjectDescriptor(new DynamicHolder(),
                dynField, dynField.getAnnotation(DynamicSensitive.class), "payload");
        assertThrows(IllegalArgumentException.class, () -> handler.encrypt(wrong));
    }

    private static SensitiveStringFieldHandler newHandler() {
        SensitiveStringFieldHandler h = new SensitiveStringFieldHandler();
        h.init();
        return h;
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

    @SuppressWarnings("unused")
    private static class StringHolder {
        @Sensitive
        private String secret = "";

        String getSecret() {
            return secret;
        }

        void setSecret(String secret) {
            this.secret = secret;
        }
    }

    @SuppressWarnings("unused")
    private static class BadIntegerHolder {
        @Sensitive
        private Integer bad = 1;
    }

    @SuppressWarnings("unused")
    private static class DynamicHolder {
        @DynamicSensitive(handler = PlaceholderHandler.class)
        private Placeholder payload;
    }

    private static class Placeholder {
    }

    /**
     * Minimal handler so {@link DynamicHolder} can carry a {@link DynamicSensitiveObjectDescriptor} for
     * {@link SensitiveStringFieldHandler#supports} tests.
     */
    public static class PlaceholderHandler implements DynamicSensitiveKeyRotationCapable<Placeholder> {
        @Override
        public void validate(Placeholder value) {
        }

        @Override
        public void encrypt(Placeholder value) {
        }

        @Override
        public void decrypt(Placeholder value) {
        }

        @Override
        public Placeholder mask(Placeholder value) {
            return value;
        }

        @Override
        public void encryptForKeyRotation(EncryptionWrapper newArtifactoryWrapper, Placeholder value) {
        }
    }
}
