package org.artifactory.security;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@Value
@EqualsAndHashCode(callSuper=false)
public class EncryptedPasswordAuthenticationToken extends UsernamePasswordAuthenticationToken {

    String encryptedPassword;

    public EncryptedPasswordAuthenticationToken(Object principal, Object credentials, String encryptedPassword) {
        super(principal, credentials);
        this.encryptedPassword = encryptedPassword;
    }
}
