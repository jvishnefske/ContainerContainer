package org.artifactory.model.security;

import org.artifactory.security.AceInfo;
import org.artifactory.security.Acl;
import org.artifactory.security.MutableReleaseBundleAcl;
import org.artifactory.security.ReleaseBundlePermissionTarget;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.Set;

/**
 * @author Inbar Tal
 */
@XmlRootElement(name = "releaseBundleAcl")
public class MutableReleaseBundleAclImpl extends MutableBaseRepoAclImpl<ReleaseBundlePermissionTarget> implements
        MutableReleaseBundleAcl {
    protected ReleaseBundlePermissionTargetImpl permissionTarget;

    public MutableReleaseBundleAclImpl() {
        // for JAXB
    }

    public MutableReleaseBundleAclImpl(Acl<ReleaseBundlePermissionTarget> copy) {
        super(copy.getLastUpdated(), copy.getUpdatedBy(), copy.getAccessIdentifier(), copy.getAces());
        this.permissionTarget = new ReleaseBundlePermissionTargetImpl(copy.getPermissionTarget());
    }

    public MutableReleaseBundleAclImpl(ReleaseBundlePermissionTarget permissionTarget, Set<AceInfo> aces,
            String updatedBy,
            long lastUpdated) {
        super(aces, updatedBy, lastUpdated);
        this.permissionTarget = new ReleaseBundlePermissionTargetImpl(permissionTarget);
    }

    public MutableReleaseBundleAclImpl(ReleaseBundlePermissionTarget permissionTarget, Set<AceInfo> aces,
            String updatedBy,
            long lastUpdated, String accessIdentifier) {
        super(lastUpdated, updatedBy, accessIdentifier, aces);
        this.permissionTarget = new ReleaseBundlePermissionTargetImpl(permissionTarget);
    }

    @Override
    public ReleaseBundlePermissionTarget getPermissionTarget() {
        return permissionTarget;
    }

    @Override
    public void setPermissionTarget(ReleaseBundlePermissionTarget permissionTarget) {
        this.permissionTarget = new ReleaseBundlePermissionTargetImpl(permissionTarget);
    }
}