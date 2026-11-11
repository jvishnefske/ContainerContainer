package org.artifactory.ui.rest.model.admin.security.password;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.security.EncryptionPolicy;
import org.artifactory.descriptor.security.PasswordExpirationPolicy;
import org.artifactory.descriptor.security.PasswordResetPolicy;

import java.util.Optional;

/**
 * @author Mor Merhav
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordSettings {

    private EncryptionPolicy encryptionPolicy = EncryptionPolicy.SUPPORTED;
    private boolean encryptionRequired;
    private boolean encryptionEnabled;
    private PasswordExpirationPolicy expirationPolicy = new PasswordExpirationPolicy();
    private PasswordResetPolicy resetPolicy = new PasswordResetPolicy();

    public void setExpirationPolicy(PasswordExpirationPolicy expirationPolicy) {
        this.expirationPolicy = Optional.ofNullable(expirationPolicy).orElseGet(PasswordExpirationPolicy::new);
    }

    public void setResetPolicy(PasswordResetPolicy resetPolicy) {
        this.resetPolicy = Optional.ofNullable(resetPolicy).orElseGet(PasswordResetPolicy::new);
    }

    public org.artifactory.descriptor.security.PasswordSettings toDescriptorPasswordSettings() {
        org.artifactory.descriptor.security.PasswordSettings passwordSettings =
                new org.artifactory.descriptor.security.PasswordSettings();
        passwordSettings.setExpirationPolicy(expirationPolicy);
        passwordSettings.setResetPolicy(resetPolicy);
        return passwordSettings;
    }

    public static PasswordSettings fromDescriptorPasswordSettings(
            org.artifactory.descriptor.security.PasswordSettings passwordSettings) {
        return new PasswordSettings(null, false, false, passwordSettings.getExpirationPolicy(),
                passwordSettings.getResetPolicy());
    }
}
