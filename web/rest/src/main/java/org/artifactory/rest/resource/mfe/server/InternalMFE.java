package org.artifactory.rest.resource.mfe.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.resource.mfe.MfeName;
import org.artifactory.rest.resource.mfe.MfeProjectConfig;
import org.artifactory.rest.resource.mfe.MfeProjectRegistry;
import org.artifactory.spring.ConditionalOnProperty;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.ResourceUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * This class is responsible for serving MFE files from the classpath. Meaning the MFE files which are bundled with the
 * current version of artifactory service.
 * Supports multiple MFE projects configured via MfeProjectResolver.
 *
 * @author nirt
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "artifactory.core.mfe.external.enabled", havingValue = "false", matchIfMissing = true)
public class InternalMFE implements MFEFileServer {
    private final MfeProjectRegistry mfeProjectRegistry;

    @Override
    public void serveFile(HttpServletResponse httpResponse, String resourcePath, String contentType)
            throws NotFoundException {
        log.debug("InternalMFE Serving MFE file: {}, contentType: {}", resourcePath, contentType);
        if (Strings.isNullOrEmpty(resourcePath)) {
            throw new NotFoundException("Failed to serve empty MFE file");
        }
        URL resource = getUrlFromResource(resourcePath);
        if (resource == null) {
            throw new NotFoundException("Failed to serve MFE file: " + resourcePath);
        }
        try (InputStream is = resource.openStream()) {
            httpResponse.setContentType(contentType);
            httpResponse.setStatus(200);
            IOUtils.copy(is, httpResponse.getOutputStream());
            log.debug("InternalMFE Served MFE file: {} with contentType: {}", resourcePath, contentType);
        } catch (IOException e) {
            throw new NotFoundException("Failed to serve MFE file: " + resourcePath);
        }
    }

    @Override
    public String readFileContent(String resourcePath) throws NotFoundException {
        if (Strings.isNullOrEmpty(resourcePath)) {
            throw new NotFoundException("Failed to read empty MFE file");
        }
        log.debug("InternalMFE Reading MFE file: {}", resourcePath);
        try {
            return ResourceUtils.getResourceAsString(resourcePath);
        } catch (Exception e) {
            throw new NotFoundException("Failed to read MFE file: " + resourcePath);
        }
    }

    @Override
    public String getMfeVersion(String mfeName) throws NotFoundException {
        MfeName mfeNameEnum = MfeName.fromValue(mfeName);
        if (mfeNameEnum == null) {
            throw new NotFoundException("Invalid MFE name: " + mfeName);
        }
        MfeProjectConfig project = mfeProjectRegistry.findProjectByMfeName(mfeNameEnum);
        if (project == null) {
            throw new NotFoundException("MFE project not found: " + mfeName);
        }
        CompoundVersionDetails details = ArtifactoryHome.get().getRunningArtifactoryServiceVersion();
        String version = details.getMfeVersion(mfeName);

        if (version == null) {
            throw new NotFoundException("Version not found for MFE: " + mfeName);
        }
        return version;
    }

    @VisibleForTesting
    URL getUrlFromResource(@Nonnull String resourcePath) {
        return this.getClass().getResource(resourcePath);
    }
}
