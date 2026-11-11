package org.artifactory.keys;

import lombok.experimental.UtilityClass;
import org.artifactory.api.rest.keys.KeyPairModel;
import org.artifactory.descriptor.repo.KeyPairDescriptor;

import static org.artifactory.descriptor.repo.KeyPairDescriptor.VAULT;

/**
 * @author Yuval Reches
 * @author Michael Akushsky
 */
@UtilityClass
public class KeyPairsUtil {

    public KeyPairDescriptor modelToDescriptor(KeyPairModel request) {
        KeyPairDescriptor descriptor = new KeyPairDescriptor();
        if (request.getVaultKey() != null) {
            parseKeyPairModelExternalVaultFields(request, descriptor);
            descriptor.setVaultType(VAULT);
        } else {
            descriptor.setPrivateKey(request.getPrivateKey());
            descriptor.setPublicKey(request.getPublicKey());
            descriptor.setPassphrase(request.getPassphrase());
        }

        descriptor.setKey(request.getPairName());
        descriptor.setKeyType(request.getPairType().toUpperCase());
        descriptor.setAlias(request.getAlias());
        return descriptor;
    }

    public KeyPairModel toResponse(KeyPairDescriptor keyPairDescriptor) {
        KeyPairModel keyPairModel = toKeyPairModel(keyPairDescriptor);
        keyPairModel.setPrivateKey(null);
        keyPairModel.setPassphrase(null);
        return keyPairModel;
    }

    public KeyPairModel toKeyPairModel(KeyPairDescriptor keyPairDescriptor) {
        return KeyPairModel.builder()
                .pairName(keyPairDescriptor.getKey())
                .vaultKey(keyPairDescriptor.getVaultType())
                .pairType(keyPairDescriptor.getKeyType())
                .alias(keyPairDescriptor.getAlias())
                .publicKey(keyPairDescriptor.getPublicKey())
                .privateKey(keyPairDescriptor.getPrivateKey())
                .passphrase(keyPairDescriptor.getPassphrase())
                .unavailable(keyPairDescriptor.getUnavailable())
                .build();
    }

    private void parseKeyPairModelExternalVaultFields(KeyPairModel keyPairModel,
            KeyPairDescriptor descriptor) {
        descriptor.setPrivateKey(
                VAULT + "://" + keyPairModel.getVaultKey() + "/" +
                        keyPairModel.getVaultPrivateKey().getPath() + "#" +
                        keyPairModel.getVaultPrivateKey().getKey());
        descriptor.setPublicKey(
                VAULT + "://" + keyPairModel.getVaultKey() + "/" +
                        keyPairModel.getVaultPublicKey().getPath() + "#" +
                        keyPairModel.getVaultPublicKey().getKey());
        if (keyPairModel.getVaultPassphrase() != null) {
            descriptor.setPassphrase(
                    VAULT + "://" + keyPairModel.getVaultKey() + "/" +
                            keyPairModel.getVaultPassphrase().getPath() + "#" +
                            keyPairModel.getVaultPassphrase().getKey());
        }
    }
}