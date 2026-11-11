package com.jfrog.build.acl.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BuildSecurityDescriptor {

    boolean buildGlobalBasicReadAllowed;
    boolean buildGlobalBasicReadForAnonymous;
    boolean hideUnauthorizedResources;
}
