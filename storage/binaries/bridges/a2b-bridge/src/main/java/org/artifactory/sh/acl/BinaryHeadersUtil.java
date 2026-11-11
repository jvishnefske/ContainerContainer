package org.artifactory.sh.acl;

import com.google.common.collect.Maps;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.binary.headers.BinaryHeaders;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.common.ConstantValues;
import org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders;

import jakarta.annotation.Nullable;
import java.util.Map;

import static org.artifactory.checksum.ChecksumInfo.TRUSTED_FILE_MARKER;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CHECKSUM_MD5;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CHECKSUM_SHA1;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CHECKSUM_SHA256;

/**
 * @author TamirH
 */
@UtilityClass
public class BinaryHeadersUtil {

    /**
     * Incoming checksum info for new files can either be client checksum populated by the upload mechanism,
     * trusted if required by the uploader (i.e. ui uploads) where no checksum validation is done on the client side,
     * or empty if no such information was provided.
     * The {@param info} is used to indicate expected checksums for the Binarystore which will fail to save a binary
     * on validation error.
     * {@see ConstantValues.failUploadOnChecksumValidationError} controls whether validation information is passed to
     * the Binarystore
     */
    @NonNull
    public Map<String, String> getExpectedHeaders(@Nullable BinaryHeaders binaryHeaders) {
        Map<String, String> headers = Maps.newHashMap();
        //This flag should only be set to false if I messed up and users need to skip validations in an emergency :(

        if (binaryHeaders != null && binaryHeaders.getChecksumsInfo() != null &&
                ConstantValues.failUploadOnChecksumValidationError.getBoolean()) {
            ChecksumsInfo info = binaryHeaders.getChecksumsInfo();
            addExpectedChecksumHeader(CHECKSUM_SHA1.getHeaderName(), info.getChecksumInfo(ChecksumType.sha1), headers);
            addExpectedChecksumHeader(CHECKSUM_SHA256.getHeaderName(), info.getChecksumInfo(ChecksumType.sha256),
                    headers);
            addExpectedChecksumHeader(CHECKSUM_MD5.getHeaderName(), info.getChecksumInfo(ChecksumType.md5), headers);
        }
        if (binaryHeaders != null && binaryHeaders.getContentLength() != null) {
            String contentLength = binaryHeaders.getContentLength();
            if (!StringUtils.isEmpty(contentLength)) {
                headers.put(HttpHeaders.CONTENT_LENGTH, contentLength);
            }
        }
        if (binaryHeaders != null && binaryHeaders.getRepoKey() != null) {
            headers.put(BinaryElementHeaders.REPOSITORY_KEY.getHeaderName(), binaryHeaders.getRepoKey());

        }
        return headers;
    }

    /**
     * Adds {@param headerName} to the headers map if the {@param checksumInfo} contains data,
     * and the original (client / expected) checksum is not {@see ChecksumInfo.TRUSTED_FILE_MARKER}, which cannot be
     * used
     * for validating the actual checksum of a binary
     */
    private void addExpectedChecksumHeader(String headerName, @Nullable ChecksumInfo checksumInfo,
            Map<String, String> headers) {
        String expectedChecksum = checksumInfo == null ? null : checksumInfo.getOriginal();
        if (StringUtils.isNotBlank(expectedChecksum) && !TRUSTED_FILE_MARKER.equals(expectedChecksum)) {
            headers.put(headerName, expectedChecksum);
        }
    }
}
