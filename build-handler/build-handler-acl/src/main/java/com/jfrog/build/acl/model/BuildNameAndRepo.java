package com.jfrog.build.acl.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

/**
 * @author BarakH
 */
@AllArgsConstructor
@Data
public class BuildNameAndRepo implements Comparable<BuildNameAndRepo> {

    private String name;
    private String repo;

    @Override
    public int compareTo(@NonNull BuildNameAndRepo o) {
        int nameDiff = getName().compareTo(o.getName());
        if (nameDiff != 0) {
            return nameDiff;
        }
        return getRepo().compareTo(o.getRepo());
    }
}
