package org.artifactory.rest.resource.token;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;

/**
 * @author barh
 */
@Value
@AllArgsConstructor
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE)
public class FilePathRequest {
    String path;
    Long expiresIn;
}
