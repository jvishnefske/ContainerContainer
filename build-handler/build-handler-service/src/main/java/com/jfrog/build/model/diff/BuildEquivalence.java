package com.jfrog.build.model.diff;

import com.google.common.base.Equivalence;
import org.apache.commons.lang3.Strings;

import java.util.HashSet;
import java.util.Set;

public class BuildEquivalence extends Equivalence<BuildItemDiff> {

    @Override
    protected boolean doEquivalent(BuildItemDiff a, BuildItemDiff b) {
        return Strings.CS.equals(a.getMd5(), b.getMd5())
                && Strings.CS.equals(a.getSha1(), b.getSha1())
                && isScopeEquals(a.getScopes(), b.getScopes());
    }

    @Override
    protected int doHash(BuildItemDiff artifactDiff) {
        return artifactDiff.hashCode();
    }

    private boolean isScopeEquals(Set<String> scope1, Set<String> scope2) {
        if (scope1 == null && scope2 == null) {
            return true;
        }
        if (scope1 == null || scope2 == null) {
            return false;
        }
        return new HashSet<>(scope1).equals(new HashSet<>(scope2));
    }
}
