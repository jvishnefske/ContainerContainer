/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.service.admin.configuration.general;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.http.HttpStatus;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.utils.FileUpload;
import org.artifactory.ui.rest.service.admin.configuration.general.exception.LogoImageHasVulnerabilityException;
import org.artifactory.ui.rest.service.admin.configuration.general.exception.LogoNotImageTypeException;
import org.artifactory.ui.rest.service.admin.configuration.general.exception.LogoSizeExceededException;
import org.artifactory.ui.utils.MultiPartUtils;
import org.jfrog.config.watch.FileEventType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.UUID;

import static org.artifactory.common.ConstantValues.uiLogoMaxSizeBytes;

/**
 * @author Chen keinan
 */
@Slf4j
@Component
public class UploadLogoService implements RestService {

    @Autowired
    CentralConfigService centralConfigService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        File tempLogoFile = null;
        try {
            tempLogoFile = getTempLogoFile(request);
            verifyImage(tempLogoFile);
            String logoDir = ContextHelper.get().getArtifactoryHome().getLogoDir().getAbsolutePath();
            File finalLogoFile = new File(logoDir, "logo");
            Files.move(tempLogoFile.toPath(), finalLogoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            propagateLogoChanges(finalLogoFile);
            response.info("Logo Uploaded Successfully");

        } catch (LogoSizeExceededException see) {
            logAndUpdateResponseForFileTooBig(response, see.getMessage());
        } catch (LogoNotImageTypeException nite) {
            logAndUpdateResponseIfNotImage(response);
        } catch (LogoImageHasVulnerabilityException ihve) {
            logAndUpdateResponseForMalicious(response, ihve.getMessage());
        } catch (Exception e) {
            log.error("The cause for uploading file failure is: {}", e.getMessage(), e);
            response.error("error uploading file to server");
        } finally {
            if (tempLogoFile != null) {
                boolean isPassed = tempLogoFile.delete();
                if (!isPassed) {
                    log.error("Failed delete temp logo file from path: {}", tempLogoFile.getAbsolutePath());
                }
            }
        }
    }

    private void propagateLogoChanges(@NonNull File logoFile) {
        try {
            ContextHelper.get().getConfigurationManager().forceFileChanged(logoFile,
                    logoFile.exists() ? FileEventType.MODIFY : FileEventType.CREATE);
        } catch (Exception e) {
            log.debug("Failed to propagate logo change", e);
            log.warn("Failed to propagate logo change");
        }
    }

    @NonNull
    private File getTempLogoFile(ArtifactoryRestRequest<Object> request) {
        File tempWorkingDir = ContextHelper.get().getArtifactoryHome().getTempWorkDir();
        String tempFileName = UUID.randomUUID().toString();
        saveFileToTempLocation(request, tempWorkingDir, tempFileName);
        return new File(tempWorkingDir, tempFileName);
    }

    /**
     * Save logo to temp folder
     *
     * @param artifactoryRequest - encapsulate data related to request
     */
    private void saveFileToTempLocation(ArtifactoryRestRequest<Object> artifactoryRequest, File tempWorkingDir,
            String tempFileName) {
        FileUpload fileUpload = (FileUpload) artifactoryRequest.getImodel();
        if (!tempWorkingDir.exists()) {
            try {
                FileUtils.forceMkdir(tempWorkingDir);
            } catch (IOException e) {
                log.error("Failed to create temp directory");
                log.debug("Failed to create temp directory:", e);
            }
        }
        MultiPartUtils.saveSpecificFile(centralConfigService, fileUpload.getFormDataMultiPart(),
                tempWorkingDir.getAbsolutePath(),
                tempFileName);
    }

    /**
     * Check if the image has fake/unrecognized format
     * Also checks exif security issue
     */
    void verifyImage(File file) throws Exception {
        verifyFromImageType(file);
        imageContainsVulnerability(file);
    }

    private void verifyFromImageType(File file) throws IOException {
        try (ByteArrayInputStream byteArrayInputStream = getImageBytes(file)) {
            ImageInputStream imageInputStream = ImageIO.createImageInputStream(byteArrayInputStream);
            Iterator<ImageReader> iter = ImageIO.getImageReaders(imageInputStream);
            if (!iter.hasNext()) {
                throw new LogoNotImageTypeException();
            }
        }
    }

    private void logAndUpdateResponseForFileTooBig(@NonNull RestResponse<?> response, @Nullable String error) {
        log.error(error);
        response.error(error);
        response.responseCode(HttpStatus.SC_BAD_REQUEST);
    }

    private void logAndUpdateResponseIfNotImage(RestResponse<?> response) {
        String msg = "Uploaded logo is not an image file";
        log.error(msg);
        response.error(msg);
        response.responseCode(HttpStatus.SC_BAD_REQUEST);
    }

    private void logAndUpdateResponseForMalicious(@NonNull RestResponse<?> response, @Nullable String error) {
        String msg = "Image contains potentially malicious code, check logs for details.";
        log.error("{} Reason: {}", msg, error);
        response.error(msg);
        response.responseCode(HttpStatus.SC_BAD_REQUEST);
    }

    private void imageContainsVulnerability(File file) {
        try (ByteArrayInputStream byteArrayInputStream = getImageBytes(file)) {
            isExifMalicious(byteArrayInputStream, file);
        } catch (ImageReadException | IOException e) {
            throw new LogoImageHasVulnerabilityException(e.getMessage());
        }
    }

    @NonNull
    @VisibleForTesting
    ByteArrayInputStream getImageBytes(File file) throws IOException {
        Path path = Paths.get(file.getCanonicalPath());
        byte[] data = readFileData(path);
        return new ByteArrayInputStream(data);
    }

    private byte[] readFileData(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            long logoMaxSize = uiLogoMaxSizeBytes.getLong();
            BoundedInputStream bis = new BoundedInputStream(inputStream, logoMaxSize + 1);
            byte[] data = IOUtils.toByteArray(bis);
            if (data.length == logoMaxSize + 1) {
                throw new LogoSizeExceededException("Logo file size must not exceed " + logoMaxSize + " bytes");
            }
            return data;
        }
    }

    @VisibleForTesting
    void isExifMalicious(ByteArrayInputStream imageInputStream, File file) throws ImageReadException, IOException {
        Imaging.getMetadata(imageInputStream, file.getName());
    }
}
