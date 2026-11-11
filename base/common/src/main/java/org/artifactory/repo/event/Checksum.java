package org.artifactory.repo.event;

import lombok.Builder;
import lombok.Getter;


/**
 * Represents a checksum for a file.
 * @author Daniel Simantov
 */

@Getter
@Builder
public class Checksum {
    private String sha1;
    private String sha256;
    private String md5;

    /**
     * Constructs a new Checksum object with the specified checksum values.
     *
     * @param sha1   The SHA-1 checksum.
     * @param sha256 The SHA-256 checksum.
     * @param md5    The MD5 checksum.
     */
    public Checksum(String sha1, String sha256, String md5) {
        this.sha1 = sha1;
        this.sha256 = sha256;
        this.md5 = md5;
    }
}

