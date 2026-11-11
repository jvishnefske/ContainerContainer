package org.artifactory.request;

/**
 * This can be used internally to indicate the client type that is running the external request.
 *
 * @author Eli Skoran 03/11/2020
 */
public enum RequestClientType {
    // Add any additional client types you work with and verified the exact behaviour
    DEBIAN,
    WHARF_IVY,
    APACHE_IVY,
    UNKNOWN
}
