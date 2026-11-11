package com.jfrog.build.acl.model.api.permission;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Effective (aggregated) permission flags for a single principal on a build, mirroring the legacy
 * {@code org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.permission.EffectivePermission} shape.
 */
@Data
@NoArgsConstructor
public class EffectivePermission {

    private String principal;
    private boolean delete;
    private boolean deploy;
    private boolean annotate;
    private boolean read;
    private boolean distribute;
    private Boolean managed;
    private boolean managedXrayMeta;
    private Integer mask;

    /**
     * Merge another permission into this one (logical OR of each capability).
     */
    public void aggregate(EffectivePermission other) {
        read = read || other.read;
        distribute = distribute || other.distribute;
        annotate = annotate || other.annotate;
        deploy = deploy || other.deploy;
        delete = delete || other.delete;
        managedXrayMeta = managedXrayMeta || other.managedXrayMeta;
    }

    /**
     * Grant all capabilities (used for admin principals).
     */
    public void grantAdmin() {
        read = true;
        distribute = true;
        annotate = true;
        deploy = true;
        delete = true;
        managedXrayMeta = true;
    }
}
