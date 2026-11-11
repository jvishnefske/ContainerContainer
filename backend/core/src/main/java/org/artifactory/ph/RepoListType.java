package org.artifactory.ph;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RepoListType {
    AVAILABLE("available"), SELECTED("selected");

    private final String listType;
}
