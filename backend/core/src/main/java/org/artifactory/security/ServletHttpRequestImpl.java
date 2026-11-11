package org.artifactory.security;

import lombok.RequiredArgsConstructor;
import org.jfrog.access.client.auth.HttpRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

@RequiredArgsConstructor
public class ServletHttpRequestImpl implements HttpRequest {
    private final HttpServletRequest request;

    @Override
    public Optional<String> getHeader(String name) {
        return Optional.ofNullable(request.getHeader(name));
    }
}
