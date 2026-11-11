package org.artifactory.util;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.addon.webstart.KeyStoreNotFoundException;
import org.artifactory.api.context.ContextHelper;
import org.jfrog.client.util.KeyStoreProvider;
import org.jfrog.client.util.KeyStoreProviderException;
import org.jfrog.client.util.KeyStoreProviderFactory;

import java.security.KeyStore;

/**
 * Shared utility for obtaining a {@link KeyStoreProvider} backed by the
 * Artifactory keystore. Used when configuring client TLS certificates
 * on outbound HTTP connections (e.g. remote repository resolution,
 * upstream capability checks, connection tests).
 */
@Slf4j
public final class ClientCertificateUtils {

    private ClientCertificateUtils() {
    }

    /**
     * Resolves the {@link ArtifactWebstartAddon} from the application
     * context and returns a {@link KeyStoreProvider} for the Artifactory
     * keystore, or {@code null} if the keystore does not exist or
     * cannot be loaded.
     */
    @Nullable
    public static KeyStoreProvider getKeyStoreProvider() {
        AddonsManager addonsManager =
                ContextHelper.get().beanForType(AddonsManager.class);
        ArtifactWebstartAddon webstartAddon =
                addonsManager.addonByType(ArtifactWebstartAddon.class);
        return getKeyStoreProvider(webstartAddon);
    }

    /**
     * Returns a {@link KeyStoreProvider} for the Artifactory keystore,
     * or {@code null} if the keystore does not exist or cannot be loaded.
     */
    @Nullable
    public static KeyStoreProvider getKeyStoreProvider(
            ArtifactWebstartAddon webstartAddon) {
        KeyStore existingKeyStore;
        try {
            existingKeyStore = webstartAddon.getExistingKeyStore();
        } catch (KeyStoreNotFoundException e) {
            return null;
        }
        if (existingKeyStore == null) {
            return null;
        }
        String keystorePass = webstartAddon.getKeystorePassword();
        try {
            return KeyStoreProviderFactory.getProvider(
                    existingKeyStore, keystorePass);
        } catch (KeyStoreProviderException e) {
            log.warn("Cannot set client certificate on repo. {}",
                    e.getMessage());
            log.debug("Cannot set client certificate on repo.", e);
            return null;
        }
    }
}
