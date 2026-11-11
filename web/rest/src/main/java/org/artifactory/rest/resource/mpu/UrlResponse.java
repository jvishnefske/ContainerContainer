package org.artifactory.rest.resource.mpu;

/**
 * Model for part URL response.
 *
 * @param url A signed URL to the storage bucket for the upload.
 * @author Andrey Tashlitsky
 */
record UrlResponse(String url) {

}
