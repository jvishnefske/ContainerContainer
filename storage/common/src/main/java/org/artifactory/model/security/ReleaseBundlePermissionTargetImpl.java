package org.artifactory.model.security;

import org.artifactory.security.MutableReleaseBundlePermissionTarget;
import org.artifactory.security.ReleaseBundlePermissionTarget;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * @author Inbar Tal
 */
@XmlRootElement(name = "target")
public class ReleaseBundlePermissionTargetImpl extends RepoPermissionTargetImpl implements
        MutableReleaseBundlePermissionTarget {

    public ReleaseBundlePermissionTargetImpl() {
        this("");
    }

    public ReleaseBundlePermissionTargetImpl(String name) {
        super(name, new ArrayList<>(), new ArrayList<>());
    }

    public ReleaseBundlePermissionTargetImpl(ReleaseBundlePermissionTarget copy) {
        super(copy.getName(),
                new LinkedHashSet<>(copy.getRepoKeys()),
                new ArrayList<>(copy.getIncludes()),
                new ArrayList<>(copy.getExcludes())
        );
    }

    @Override
    public String toString() {
        return "ReleaseBundlePermissionTargetImpl{" +
                "name='" + name + '\'' +
                '}';
    }
}
