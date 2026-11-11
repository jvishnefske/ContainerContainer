package org.artifactory.sensitive.fields;

import lombok.Data;
import org.artifactory.sensitive.DynamicSensitive;
import org.artifactory.sensitive.Sensitive;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import java.util.List;

/**
 * Synthetic {@code @GenerateDiffFunction} root for {@link SensitiveDiffUtilTest}. Carries one non-sensitive field, one
 * {@code @Sensitive} String, and one {@code @DynamicSensitive} collection whose element values are runtime-sensitive,
 * so the generic masking behaviour of {@link SensitiveDiffUtil} can be tested without any repo-config model.
 */
@Data
@GenerateDiffFunction
class TestSensitiveDiffRoot {

    private String url;

    @Sensitive
    private String password;

    @DynamicSensitive(handler = TestSensitiveHeaderListHandler.class)
    private List<TestSensitiveHeader> headers;
}
