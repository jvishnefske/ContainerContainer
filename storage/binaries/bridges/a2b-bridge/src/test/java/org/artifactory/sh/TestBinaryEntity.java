package org.artifactory.sh;

import lombok.Value;

@Value
public class TestBinaryEntity {

    String sha1;
    String sha2;
    String md5;
    long length;
}
