package org.artifactory.sensitive.fields;

import lombok.Data;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * Synthetic {@code @GenerateDiffFunction} element type for {@link SensitiveDiffUtilTest}: a header whose {@code value}
 * is the secret and whose {@code name} is the (visible) diff key. Mirrors the real {@code CustomHttpHeader} shape but
 * carries no repo-config coupling, so the generic masking behaviour can be exercised in the infra module alone.
 */
@Data
@GenerateDiffFunction
class TestSensitiveHeader {

    @DiffKey
    private String name;
    private String value;
    private boolean sensitive;

    TestSensitiveHeader() {
    }

    TestSensitiveHeader(String name, String value, boolean sensitive) {
        this.name = name;
        this.value = value;
        this.sensitive = sensitive;
    }
}
