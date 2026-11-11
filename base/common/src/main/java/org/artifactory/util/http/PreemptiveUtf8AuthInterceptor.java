package org.artifactory.util.http;

import org.apache.http.Consts;
import org.apache.http.auth.AuthScheme;
import org.apache.http.impl.auth.BasicScheme;
import org.jfrog.client.http.auth.PreemptiveAuthInterceptor;

/**
 * @author Oleksandr Legenkiy
 */
public class PreemptiveUtf8AuthInterceptor extends PreemptiveAuthInterceptor {

    @Override
    protected AuthScheme getAuthScheme() {
        return new BasicScheme(Consts.UTF_8);
    }
}
