package org.artifactory.authentication;

import org.artifactory.authentication.context.AuthenticationContext;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

/**
 * The interface is an extension of the Filter interface.
 * A class should implement this interface for working and getting information by the AuthenticationContext.
 * This interface helps us to work with services outside Artifactory and to point them once, for example
 * package-handler.
 *
 * @author shahafg
 */
public interface ArtifactoryAuthenticationContextFilter extends ArtifactoryAuthenticationFilterBase {

    /**
     * @return an object with the extracted data of the request.
     */
    AuthenticationContext getAuthenticationContext(ServletRequest request, ServletResponse response) throws IOException;
}
