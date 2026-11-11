package org.artifactory.storage.db.event.entity;

import com.google.common.collect.Sets;

import java.util.Set;

public class NodeEventContext {
    // TODO (Gidi) Not sure if we want context to be list of strings
    private Set<String> context = Sets.newHashSet();

    public Set<String> getContext() {
        return context;
    }

    public void setContext(Set<String> context) {
        this.context = context;
    }
}
