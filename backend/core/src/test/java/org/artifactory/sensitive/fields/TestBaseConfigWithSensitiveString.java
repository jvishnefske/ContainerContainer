package org.artifactory.sensitive.fields;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.model.BaseConfig;
import org.artifactory.sensitive.Sensitive;

@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("unused")
class TestBaseConfigWithSensitiveString extends BaseConfig {
    @Sensitive
    private String secret = "";
}
