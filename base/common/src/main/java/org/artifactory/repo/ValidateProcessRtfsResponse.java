package org.artifactory.repo;

import lombok.Builder;
import lombok.Getter;


/**
 * Represents the response received from validating a remote server's RTFS (Artifactory federation Service)
 * Contains the status code and validation message.
 * Author: Daniel Simantov
 */

@Getter
@Builder
public class ValidateProcessRtfsResponse {
    private int statusCode;
    private String validationMessage;
}