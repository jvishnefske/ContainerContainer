package org.artifactory.rest.resource.mpu;

/**
 * Model for is config response.
 *
 * @param supported is the multipart upload feature enabled for this customer.
 * @author Idan Dadon
 */
record ConfigResponse(boolean supported) {
}
