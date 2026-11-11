package org.artifactory.repo.interceptor.config;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.crypto.CryptoService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.sensitive.CustomHttpHeadersSensitiveHandler;
import org.artifactory.sensitive.fields.CryptoServiceImpl;
import org.artifactory.sensitive.fields.DynamicSensitiveFieldHandler;
import org.artifactory.sensitive.fields.SensitiveStringFieldHandler;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RepoConfigSensitiveFieldChangeInterceptor}.
 * Uses a real {@link CryptoServiceImpl} with a no-op {@link EncryptionWrapper} (returns values unchanged)
 * so that the encrypt path runs fully without requiring a real master key.
 *
 * @author Fawaz Atwa
 */
class RepoConfigSensitiveFieldChangeInterceptorTest {

    private CryptoServiceImpl cryptoServiceImpl;
    private RepoConfigSensitiveFieldChangeInterceptor interceptor;

    @BeforeEach
    void bindArtifactoryHome() {
        ArtifactoryHome home = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties props = mock(ArtifactorySystemProperties.class);
        File keyFile = mock(File.class);
        // No-op wrapper: returns values unchanged so the encrypt path runs without a real master key.
        EncryptionWrapper encryptionWrapper = mock(EncryptionWrapper.class);
        when(encryptionWrapper.encryptIfNeeded(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(props.getLongProperty(ConstantValues.sensitiveRepoConfigMaxRecursionDepth)).thenReturn(20L);
        when(home.getArtifactoryProperties()).thenReturn(props);
        when(home.getArtifactoryKey()).thenReturn(keyFile);
        when(home.getMasterEncryptionWrapper()).thenReturn(encryptionWrapper);
        when(keyFile.exists()).thenReturn(true);
        ArtifactoryHome.bind(home);

        SensitiveStringFieldHandler sensitiveStringFieldHandler = new SensitiveStringFieldHandler();
        sensitiveStringFieldHandler.init();
        cryptoServiceImpl =
                new CryptoServiceImpl(List.of(sensitiveStringFieldHandler, new DynamicSensitiveFieldHandler()));

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.beanForType(CustomHttpHeadersSensitiveHandler.class)).thenAnswer(invocation -> {
            CustomHttpHeadersSensitiveHandler handler = new CustomHttpHeadersSensitiveHandler();
            handler.init();
            return handler;
        });
        when(context.beanForType(CryptoService.class)).thenReturn(cryptoServiceImpl);
        ArtifactoryContextThreadBinder.bind(context);

        interceptor = new RepoConfigSensitiveFieldChangeInterceptor(cryptoServiceImpl);
    }

    @AfterEach
    void unbindArtifactoryHome() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void onBeforeSaveWithCreateDoesNotThrow() {
        RemoteRepoConfig newConfig = RemoteRepoConfig.create("remote-repo", RepoType.Generic);
        newConfig.getRepoTypeConfig().setPassword("plain");
        RepoConfigChange change = new RepoConfigChange("remote-repo", REPO_CREATE, newConfig, null);
        RepoConfigChanges changes = new RepoConfigChanges(Map.of(RepoDetailsType.REMOTE, List.of(change)));

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
        assertNotNull(newConfig.getRepoTypeConfig().getPassword());
    }

    @Test
    void onBeforeSaveWithUpdateDoesNotThrow() {
        LocalRepoConfig oldConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        LocalRepoConfig newConfig = LocalRepoConfig.create("local-repo", RepoType.Generic);
        RepoConfigChange change = new RepoConfigChange("local-repo", REPO_UPDATE, newConfig, oldConfig);
        RepoConfigChanges changes = new RepoConfigChanges(Map.of(RepoDetailsType.LOCAL, List.of(change)));

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void onBeforeSaveWithNullNewConfigDoesNotThrow() {
        RemoteRepoConfig oldConfig = RemoteRepoConfig.create("remote-repo", RepoType.Generic);
        RepoConfigChange change = new RepoConfigChange("remote-repo", REPO_UPDATE, null, oldConfig);
        RepoConfigChanges changes = new RepoConfigChanges(Map.of(RepoDetailsType.REMOTE, List.of(change)));

        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }
}
