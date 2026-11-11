package org.artifactory.policy;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author vovam
 */
@AllArgsConstructor
public enum PolicyType {
    CLEANUP("cleanup"),
    ARCHIVE("archive"),
    RESTORE("restore");

    @Getter
    private final String code;

    public static PolicyType findByCode(String code) {
        return Arrays.stream(values())
                .filter(typeCode -> Objects.equals(typeCode.getCode(), code))
                .findFirst()
                .orElseThrow();
    }
}
