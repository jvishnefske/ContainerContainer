package org.artifactory.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public class DiffEntry {
    private final String attributeName;
    private final Object oldValue;
    private final Object newValue;
}
