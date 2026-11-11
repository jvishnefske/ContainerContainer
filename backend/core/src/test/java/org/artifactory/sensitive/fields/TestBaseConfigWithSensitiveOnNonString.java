package org.artifactory.sensitive.fields;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.model.BaseConfig;
import org.artifactory.sensitive.Sensitive;

@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("unused")
class TestBaseConfigWithSensitiveOnNonString extends BaseConfig {
    @Sensitive
    private Integer badField = 1;
}
