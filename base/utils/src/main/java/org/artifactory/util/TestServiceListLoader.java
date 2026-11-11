package org.artifactory.util;

import org.jfrog.client.http.ServiceHttpBuilder;
import org.jfrog.client.util.ServicelistLoader;

public class TestServiceListLoader implements ServicelistLoader {

    private final ServiceHttpBuilder.JFrogService service;
    private final String url;

    public TestServiceListLoader(ServiceHttpBuilder.JFrogService service, String url) {
        this.service = service;
        this.url = url;
    }

    @Override
    public String getUrl(String serviceName) {
        if (service.getService().equals(serviceName)) {
            return url;
        }
        return null;
    }

    @Override
    public boolean hasService(String serviceName) {
        return service.getService().equals(serviceName);
    }
}
