package org.artifactory.sensitive.fields;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.model.BaseConfig;
import org.artifactory.sensitive.DynamicSensitive;

@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("unused")
class TestBaseConfigWithDynamicSensitiveOnString extends BaseConfig {
    @DynamicSensitive(handler = SensitiveFieldServiceTest.TestDynamicSensitiveHandlerForString.class)
    private String wrongField = "x";
}
