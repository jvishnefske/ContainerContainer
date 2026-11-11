package org.artifactory.descriptor.repo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyPairDescriptorTest {
    @Test
    void getAliasWithExtension_formatsAliasCorrectly() {
        KeyPairDescriptor keyPairDescriptor = new KeyPairDescriptor();
        keyPairDescriptor.setKey("key");
        keyPairDescriptor.setAlias("alias");
        keyPairDescriptor.setKeyType("ECDSA");
        assertEquals("alias.ecdsa.pub", keyPairDescriptor.getAliasWithExtension());
    }
}