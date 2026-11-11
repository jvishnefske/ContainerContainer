package org.artifactory.probes.model;

import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.ServiceUnavailableException;
import lombok.Getter;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;

@Getter
enum ReadinessStatusEnum {
    NOT_RUNNING(SC_INTERNAL_SERVER_ERROR, "Artifactory is not running", new InternalServerErrorException()),
    OFFLINE(SC_INTERNAL_SERVER_ERROR, "Artifactory is in offline mode", new InternalServerErrorException()),
    CANNOT_WRITE(SC_INTERNAL_SERVER_ERROR, "Artifactory cannot write to disk", new InternalServerErrorException()),
    DB_NOT_HEALTHY(SC_INTERNAL_SERVER_ERROR, "The internal DB service is not ready",
            new InternalServerErrorException()),
    TOMCAT_NOT_READY(SC_INTERNAL_SERVER_ERROR, "Tomcat server is not ready", new InternalServerErrorException()),
    ACCESS_SERVICE_NOT_READY(SC_SERVICE_UNAVAILABLE, "The Access service is not ready",
            new ServiceUnavailableException()),
    STORAGE_SERVICE_NOT_READY(SC_SERVICE_UNAVAILABLE, "The storage service is not ready",
            new ServiceUnavailableException());

    static final String READINESS_SUCCESS_MESSAGE = "All readiness checks passed successfully";

    private final int statusCode;
    private final String message;
    private final ServerErrorException exception;

    ReadinessStatusEnum(int statusCode, String message, ServerErrorException exception) {
        this.statusCode = statusCode;
        this.message = message;
        this.exception = exception;
    }
}