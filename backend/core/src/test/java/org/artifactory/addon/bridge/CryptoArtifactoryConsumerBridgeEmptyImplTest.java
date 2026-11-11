package org.artifactory.addon.bridge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoArtifactoryConsumerBridgeEmptyImplTest {

    private CryptoArtifactoryConsumerBridgeEmptyImpl bridgeToArtifactory;

    @BeforeEach
    public void setUp() {
        bridgeToArtifactory = new CryptoArtifactoryConsumerBridgeEmptyImpl();
    }

    @Test
    void createRepositories_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getPublicKeyByAlias("keyId");
        assertThrows(UnsupportedOperationException.class, target);
    }
}