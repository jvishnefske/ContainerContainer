package org.artifactory.security.authentication;

import org.artifactory.addon.oauth.OAuthHandler;
import org.artifactory.common.security.RealmAwareAuthentication;
import org.artifactory.security.props.auth.model.OauthModel;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Objects;

/**
 * @author Alexei Vainshtein
 */
public class OauthAuthentication extends UsernamePasswordAuthenticationToken implements RealmAwareAuthentication {

    private OauthModel model;

    public OauthAuthentication(Object principal, Object credentials) {
        super(principal, credentials);
    }

    public OauthAuthentication(Object principal, Object credentials,
            Collection<? extends GrantedAuthority> authorities, OauthModel oauthModel) {
        super(principal, credentials, authorities);
        this.model = oauthModel;
    }

    @Override
    public String getRealm() {
        return OAuthHandler.REALM;
    }

    public OauthModel getModel() {
        return model;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        OauthAuthentication that = (OauthAuthentication) o;
        return model.equals(that.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), model);
    }
}
