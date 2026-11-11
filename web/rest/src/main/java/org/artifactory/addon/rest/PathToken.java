package org.artifactory.addon.rest;

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
public class PathToken {
    String path;
    String sha2;
    String token;
    String repoType;
    Long size;
    boolean expirable;
}
