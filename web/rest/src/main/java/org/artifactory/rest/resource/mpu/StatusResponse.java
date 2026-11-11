package org.artifactory.rest.resource.mpu;


import javax.annotation.Nullable;

/**
 * Model for mpu status response.
 *
 * @author Idan Dadon
 */
public record StatusResponse(String status, String error, int progress, @Nullable String checksumToken) {
    // Constructor for the case where error is not empty
    public StatusResponse(String status, String error) {
        this(status, error, 0, null);
    }

    // Constructor for the case where error is empty
    public StatusResponse(String status, int progress, String checksumToken) {
        this(status, null, progress, checksumToken);
    }
}
