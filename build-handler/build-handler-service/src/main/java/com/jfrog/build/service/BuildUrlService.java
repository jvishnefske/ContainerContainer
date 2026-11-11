package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

import static org.apache.commons.codec.net.URLCodec.encodeUrl;
import static org.apache.http.util.EncodingUtils.getAsciiString;
import static org.apache.http.util.EncodingUtils.getBytes;
import static org.jfrog.common.encoding.URI.allowed_query;

@RequiredArgsConstructor
public class BuildUrlService {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    public void redirectBuildUpload(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull String originalServletPath) throws IOException {
        bridgeToArtifactory.redirectBuildUpload(request, response, originalServletPath);
    }

    @NonNull
    public String getServletContextUrl(@NonNull HttpServletRequest request) {
        return bridgeToArtifactory.getServletContextUrl(request);
    }

    @NonNull
    public String getBaseBuildsHref(@NonNull HttpServletRequest request) {
        return getServletContextUrl(request) + "/api/build";
    }

    @NonNull
    public static String getBuildRelativeHref(@NonNull String buildName) {
        return "/" + encodeQuery(buildName);
    }

    @NonNull
    public static String getBuildNumberRelativeHref(@NonNull String buildNumber) {
        return "/" + encodeQuery(buildNumber);
    }

    @NonNull
    public static String getBuildRepoRelativeHref(@NonNull String buildRepo) {
        return "?buildRepo=" + encodeQuery(buildRepo);
    }

    @NonNull
    public String getBuildInfoHref(
            @NonNull HttpServletRequest request,
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildRepo) {
        return getBaseBuildsHref(request)
                + getBuildRelativeHref(buildName)
                + getBuildNumberRelativeHref(buildNumber)
                + getBuildRepoRelativeHref(buildRepo);
    }

    @NonNull
    private static String encodeQuery(@NonNull String unescaped) {
        byte[] rawUnescaped = getBytes(unescaped, "UTF-8");
        byte[] rawEncoded = encodeUrl(allowed_query, rawUnescaped);
        return getAsciiString(rawEncoded);
    }
}
