package org.artifactory.ph.mapper;

import com.jfrog.ph.acl.model.KeyPair;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;

/**
 * Mapper from Artifactory KeyPair descriptor to PH KeyPair configuration
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PhKeyPairConfigurationMapper  {
    public static KeyPair from(KeyPairDescriptor keyPairDescriptor) {
        KeyPair keyPair = new KeyPair();
        keyPair.setKey(keyPairDescriptor.getKey());
        keyPair.setKeyType(keyPairDescriptor.getKeyType());
        keyPair.setPrivateKey(keyPairDescriptor.getPrivateKey());
        keyPair.setPublicKey(keyPairDescriptor.getPublicKey());
        keyPair.setPassphrase(keyPairDescriptor.getPassphrase());
        keyPair.setAlias(keyPairDescriptor.getAlias());
        return keyPair;
    }
}
