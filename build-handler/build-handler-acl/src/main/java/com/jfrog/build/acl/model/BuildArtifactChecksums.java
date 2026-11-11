package com.jfrog.build.acl.model;

import lombok.Value;

/**
 * The SHA1/MD5 checksums of an artifact, used to look up the builds that produced or depend on it.
 */
@Value
public class BuildArtifactChecksums {

    String sha1;
    String md5;
}
