package org.artifactory.api.request;

import jakarta.annotation.Nonnull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.request.HeadersMultiMap;
import org.jfrog.common.StreamSupportUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;

@Getter
@FieldDefaults(makeFinal=true, level= AccessLevel.PRIVATE)
public class LifecycleIndependentArtifactoryRequest extends ArtifactoryRequestBase {
    @Getter(AccessLevel.NONE)
    ArtifactoryRequest originalRequest;
    RepoPath repoPath;
    String repoKey;
    String path;
    String clientAddress;
    boolean metadata;
    boolean recursive;
    long modificationTime;
    String name;
    boolean directoryRequest;
    boolean checksum;
    boolean fromAnotherArtifactory;
    boolean headOnly;
    long lastModified;
    long ifModifiedSince;
    @Getter(AccessLevel.NONE)
    boolean hasIfModifiedSince;
    @Getter(AccessLevel.NONE)
    HeadersMultiMap headers;
    String servletContextUrl;
    String uri;
    Properties properties;
    @Getter(AccessLevel.NONE)
    boolean hasProperties;
    Map<String, String[]> parameters;
    String zipResourcePath;
    boolean zipResourceRequest;
    InputStream inputStream;
    long contentLength;
    @Getter(AccessLevel.NONE)
    boolean hasIfNoneMatch;
    @Getter(AccessLevel.NONE)
    boolean shouldSkipPackageUsageTrack;

    public LifecycleIndependentArtifactoryRequest(ArtifactoryRequest originalRequest) throws IOException {
        this.originalRequest = originalRequest;
        this.repoPath = originalRequest.getRepoPath();
        this.repoKey = originalRequest.getRepoKey();
        this.path = originalRequest.getPath();
        this.clientAddress = originalRequest.getClientAddress();
        this.metadata = originalRequest.isMetadata();
        this.recursive = originalRequest.isRecursive();
        this.modificationTime = originalRequest.getModificationTime();
        this.name = originalRequest.getName();
        this.directoryRequest = originalRequest.isDirectoryRequest();
        this.checksum = originalRequest.isChecksum();
        this.fromAnotherArtifactory = originalRequest.isFromAnotherArtifactory();
        this.headOnly = originalRequest.isHeadOnly();
        this.lastModified = originalRequest.getLastModified();
        this.ifModifiedSince = originalRequest.getIfModifiedSince();
        this.hasIfModifiedSince = originalRequest.hasIfModifiedSince();
        this.servletContextUrl = originalRequest.getServletContextUrl();
        this.uri = originalRequest.getUri();
        this.properties = originalRequest.getProperties();
        this.hasProperties = originalRequest.hasProperties();
        this.parameters = originalRequest.getParameters();
        this.zipResourcePath = originalRequest.getZipResourcePath();
        this.zipResourceRequest = originalRequest.isZipResourceRequest();
        this.inputStream = originalRequest.getInputStream();
        this.contentLength = originalRequest.getContentLength();
        this.hasIfNoneMatch = originalRequest.hasIfNoneMatch();
        this.shouldSkipPackageUsageTrack = originalRequest.shouldSkipPackageUsageTrack();
        this.headers = new HeadersMultiMap();
        StreamSupportUtils.enumerationToStream(originalRequest.getHeadersKeys()).forEach(
                key -> this.headers.addAll(key, originalRequest.getHeaderValues(key))
        );
    }

    @Override
    public boolean shouldSkipPackageUsageTrack() {
        return shouldSkipPackageUsageTrack;
    }

    @Override
    public Enumeration<String> getHeadersKeys() {
        return headers.getHeadersKeys();
    }

    @Override
    public Enumeration<String> getHeaderValues(@Nonnull String headerName) {
        return headers.getHeaderValues(headerName);
    }

    @Override
    public boolean hasIfModifiedSince() {
        return hasIfModifiedSince;
    }

    @Override
    public boolean hasProperties() {
        return hasProperties;
    }

    @Override
    public String getParameter(String name) {
        // InternalArtifactoryRequest has implementation of getParameters that returns an empty map. Using original
        // implementation instead.
        if (originalRequest instanceof InternalArtifactoryRequest) {
            return originalRequest.getParameter(name);
        }
        String[] value = parameters.get(name);
        if (value == null || value.length == 0) {
            return null;
        }
        return value[0];
    }

    @Override
    public String[] getParameterValues(String name) {
        // InternalArtifactoryRequest has implementation of getParameters that returns an empty map. Using original
        // implementation instead.
        if (originalRequest instanceof InternalArtifactoryRequest) {
            return originalRequest.getParameterValues(name);
        }
        return parameters.get(name);
    }

    @Override
    public boolean hasIfNoneMatch() {
        return hasIfNoneMatch;
    }
}
