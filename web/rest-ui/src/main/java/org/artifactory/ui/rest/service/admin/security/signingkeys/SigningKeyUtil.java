package org.artifactory.ui.rest.service.admin.security.signingkeys;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.artifactory.api.rest.keys.KeyPairModel;
import org.artifactory.ui.rest.model.admin.security.signingkey.SignKey;

import static org.artifactory.descriptor.repo.KeyPairDescriptor.GPG_TYPE;
import static org.artifactory.descriptor.repo.KeyPairDescriptor.VAULT;

/**
 * @author Michael Akushsky
 */
@UtilityClass
public class SigningKeyUtil {

    @Nonnull
    public KeyPairModel constructKeyPairModelForExternalVault(SignKey signKey) {
        KeyPairModel keyPairModel = new KeyPairModel();
        keyPairModel.setPairType(GPG_TYPE);
        keyPairModel.setVaultKey(signKey.getVaultKey());
        keyPairModel.setPrivateKey(
                VAULT + "://" + signKey.getVaultKey() + "/" +
                        signKey.getPrivateKeyPath().getPath() + "#" +
                        signKey.getPrivateKeyPath().getKey());
        keyPairModel.setPublicKey(
                VAULT + "://" + signKey.getVaultKey() + "/" +
                        signKey.getPublicKeyPath().getPath() + "#" +
                        signKey.getPublicKeyPath().getKey());
        if (signKey.getPassPhrasePath() != null) {
            keyPairModel.setPassphrase(
                    VAULT + "://" + signKey.getVaultKey() + "/" +
                            signKey.getPassPhrasePath().getPath() + "#" +
                            signKey.getPassPhrasePath().getKey());
        }
        return keyPairModel;
    }
}
