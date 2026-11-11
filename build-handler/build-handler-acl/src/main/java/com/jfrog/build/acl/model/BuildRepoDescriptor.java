package com.jfrog.build.acl.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BuildRepoDescriptor {

    String key;
    String type;

    boolean localOrFederated;
    boolean cache;
}
