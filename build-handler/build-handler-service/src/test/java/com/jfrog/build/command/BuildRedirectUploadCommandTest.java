package com.jfrog.build.command;

import com.jfrog.build.service.BuildUrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BuildRedirectUploadCommandTest {

    private BuildRedirectUploadCommand command;

    private BuildUrlService urlService;

    @BeforeEach
    void setUp() {
        urlService = mock(BuildUrlService.class);
        command = new BuildRedirectUploadCommand(urlService);
    }

    @Test
    void redirectBuildUpload_delegatesToUrlService() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        command.redirectBuildUpload(request, response, "api/build/repo/key");

        verify(urlService).redirectBuildUpload(same(request), same(response), eq("api/build/repo/key"));
    }

    @Test
    void redirectBuildUpload_whenUrlServiceThrowsIOException_wrapsAsBadRequest() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        doThrow(new IOException("network")).when(urlService).redirectBuildUpload(
                same(request), same(response), eq("bad/path"));

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> command.redirectBuildUpload(request, response, "bad/path"));

        assertTrue(ex.getMessage().contains("bad/path"));
        assertTrue(ex.getMessage().contains("network"));
    }
}
