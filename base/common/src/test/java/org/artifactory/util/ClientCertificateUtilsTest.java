package org.artifactory.util;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.addon.webstart.KeyStoreNotFoundException;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.jfrog.client.util.KeyStoreProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class ClientCertificateUtilsTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactWebstartAddon webstartAddon;
    @Mock
    private KeyStore keyStore;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(AddonsManager.class))
                .thenReturn(addonsManager);
        when(addonsManager.addonByType(ArtifactWebstartAddon.class))
                .thenReturn(webstartAddon);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void returnsProviderWhenKeystoreExists() throws Exception {
        when(webstartAddon.getExistingKeyStore()).thenReturn(keyStore);
        when(webstartAddon.getKeystorePassword()).thenReturn("pass");

        KeyStoreProvider result =
                ClientCertificateUtils.getKeyStoreProvider();

        assertNotNull(result);
    }

    @Test
    void returnsNullWhenKeystoreNotFound() throws Exception {
        when(webstartAddon.getExistingKeyStore())
                .thenThrow(new KeyStoreNotFoundException("no store"));

        KeyStoreProvider result =
                ClientCertificateUtils.getKeyStoreProvider();

        assertNull(result);
    }

    @Test
    void returnsNullWhenKeystoreIsNull() throws Exception {
        when(webstartAddon.getExistingKeyStore()).thenReturn(null);

        KeyStoreProvider result =
                ClientCertificateUtils.getKeyStoreProvider();

        assertNull(result);
    }

    @Test
    void parameterOverloadReturnsProvider() throws Exception {
        when(webstartAddon.getExistingKeyStore()).thenReturn(keyStore);
        when(webstartAddon.getKeystorePassword()).thenReturn("pass");

        KeyStoreProvider result =
                ClientCertificateUtils.getKeyStoreProvider(
                        webstartAddon);

        assertNotNull(result);
    }
}
