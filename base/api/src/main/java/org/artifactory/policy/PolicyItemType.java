package org.artifactory.policy;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Getter
public enum PolicyItemType {

    PACKAGE("package"),
    ARTIFACT("artifact"),
    BUILD("build"),
    RELEASE_BUNDLE("releaseBundle");

    private final String code;

    private static final Map<String, PolicyItemType> CODE_TO_ENUM = new HashMap<>();

    static {
        for (PolicyItemType type : values()) {
            CODE_TO_ENUM.put(type.code, type);
        }
    }

    public static PolicyItemType lookup(String code) {
        return CODE_TO_ENUM.get(code);
    }
}
