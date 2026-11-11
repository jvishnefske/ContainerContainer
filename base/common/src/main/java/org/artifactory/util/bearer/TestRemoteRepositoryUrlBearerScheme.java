package org.artifactory.util.bearer;

import org.apache.http.HttpRequest;
import org.apache.http.auth.Credentials;
import org.apache.http.protocol.HttpContext;
import org.jfrog.client.http.auth.BearerScheme;

/**
 * This scheme is used for remote repository connectivity instead of ArtifactoryBearerScheme. It is necessary because
 * the remote repository config may not exist at the time of testing, such as when a user is still in the
 * process of creating it.
 *
 * @author Igor Usenko
 */
public class TestRemoteRepositoryUrlBearerScheme extends BearerScheme {

    private final String token;

    public TestRemoteRepositoryUrlBearerScheme(String token) {
        super(null);
        this.token = token;
    }

    @Override
    public String getToken(Credentials credentials, HttpRequest request, HttpContext context) {
        return token;
    }
}
