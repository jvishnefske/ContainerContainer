package com.jfrog.build.command;

import com.jfrog.build.service.BuildUrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;

@Slf4j
@RequiredArgsConstructor
public class BuildRedirectUploadCommand {

    private final BuildUrlService urlService;

    public void redirectBuildUpload(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull String path) {
        log.debug("Build upload redirect triggered with original servlet path: {}", path);
        try {
            urlService.redirectBuildUpload(request, response, path);
        } catch (IOException e) {
            log.debug("Failed to deploy build info to the path", e);
            throw badRequestException("Failed to deploy build info to the path: %s : %s", path, e.getMessage());
        }
    }
}
