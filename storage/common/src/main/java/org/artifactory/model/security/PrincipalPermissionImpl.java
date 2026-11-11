package org.artifactory.model.security;

import org.artifactory.security.AceInfo;
import org.artifactory.security.PermissionTarget;
import org.artifactory.security.PrincipalPermission;

/**
 * Class represent specific user/group permission.
 *
 * @author Shay Bagants
 */
public class PrincipalPermissionImpl<T extends PermissionTarget> implements PrincipalPermission<T> {

    private T permissionTarget;
    private AceInfo aceInfo;

    private String accessIdentifier;

    public PrincipalPermissionImpl(T permissionTarget, AceInfo aceInfo, String accessIdentifier) {
        this.permissionTarget = permissionTarget;
        this.aceInfo = aceInfo;
        this.accessIdentifier = accessIdentifier;
    }

    @Override
    public T getPermissionTarget() {
        return permissionTarget;
    }

    @Override
    public AceInfo getAce() {
        return aceInfo;
    }

    @Override
    public String getAccessIdentifier() {
        return accessIdentifier;
    }

    @Override
    public String toString() {
        return "PrincipalPermissionImpl{" +
                "permissionTarget=" + permissionTarget +
                ", aceInfo=" + aceInfo +
                ", accessIdentifier='" + accessIdentifier + '\'' +
                '}';
    }
}