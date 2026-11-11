package org.artifactory.request;

import org.artifactory.checksum.ChecksumType;

import static org.artifactory.request.ArtifactoryRequest.CHECKSUM_MD5;
import static org.artifactory.request.ArtifactoryRequest.CHECKSUM_SHA1;
import static org.artifactory.request.ArtifactoryRequest.CHECKSUM_SHA256;

public class HeaderByChecksumType {

    public static String headerForChecksum(ChecksumType checksumType) {
        switch (checksumType) {
            case sha1:
                return CHECKSUM_SHA1;
            case sha256:
                return CHECKSUM_SHA256;
            case md5:
                return CHECKSUM_MD5;
            default:
                return "";
        }
    }
}
