package org.artifactory.addon.bridge;

import com.jfrog.bh.acl.consumer.bridge.CryptoArtifactoryConsumerBridge;
import com.jfrog.bh.acl.consumer.model.CryptoKeyPair;
import com.jfrog.bh.acl.consumer.model.CryptoTrustedKey;
import com.jfrog.bh.acl.error.base.BundleException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Empty implementation of the {@link CryptoArtifactoryConsumerBridge} for Artifactory OSS.
 * <p>
 * The non-empty implementation resides in the Artifactory Pro, and has a higher priority
 * than this bean (in Artifactory Pro deployments this empty implementation is suppressed).
 */
@Slf4j
@Component
public class CryptoArtifactoryConsumerBridgeEmptyImpl implements CryptoArtifactoryConsumerBridge {

    private static final UnsupportedOperationException UNSUPPORTED_OPERATION =
            new UnsupportedOperationException("Crypto service is available in Artifactory Pro");

    @NonNull
    @Override
    public List<CryptoKeyPair> getKeys() {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public CryptoKeyPair getKeyPair(@NonNull String keyPairName) {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public CryptoKeyPair getOrCreateDefaultKeyPair() throws BundleException {
        throw UNSUPPORTED_OPERATION;
    }

    @NonNull
    @Override
    public CryptoTrustedKey getPublicKeyByAlias(@NonNull String keyId) throws BundleException  {
        throw UNSUPPORTED_OPERATION;
    }
}