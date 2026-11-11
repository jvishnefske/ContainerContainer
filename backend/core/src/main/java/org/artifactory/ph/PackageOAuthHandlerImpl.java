package org.artifactory.ph;

import com.jfrog.ph.acl.model.sso.PackageSsoLoginModel;
import com.jfrog.ph.acl.service.PackageOAuthHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.oauth.OAuthHandler;
import org.artifactory.addon.oauth.SsoLoginModel;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * PackageOAuthHandler expose {@link OAuthHandler} abilities to the Package handler module.
 */
public class PackageOAuthHandlerImpl implements PackageOAuthHandler {

    private final OAuthHandler oAuthHandler;

    public PackageOAuthHandlerImpl(OAuthHandler oAuthHandler) {
        this.oAuthHandler = oAuthHandler;
    }

    @Override
    public Response handleLogin(String method, String name, String path, HttpServletRequest request,
            @Nullable PackageSsoLoginModel packageSsoLoginModel) {
        SsoLoginModel loginModel = Optional.ofNullable(packageSsoLoginModel)
                .map(pkg -> {
                    SsoLoginModel model = new SsoLoginModel();
                    model.setName(pkg.getName());
                    model.setPassword(pkg.getPassword());
                    return model;
                })
                .orElse(null);

        return (Response) oAuthHandler.handleLogin(method, name, path, request, loginModel);
    }


    @Override
    public Optional<String> getNpmLoginHandler() {
        return oAuthHandler.getNpmLoginHandler();
    }
}
