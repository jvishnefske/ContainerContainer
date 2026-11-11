package org.artifactory.sensitive.fields;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.model.BaseConfig;
import org.artifactory.sensitive.DynamicSensitive;
import org.jfrog.common.config.diff.DiffIgnore;

@Data
@EqualsAndHashCode(callSuper = true)
@SuppressWarnings("unused")
class TestBaseConfigWithDynamicSensitiveDto extends BaseConfig {
    @DiffIgnore
    @DynamicSensitive(handler = SensitiveFieldServiceTest.TestDtoHandler.class)
    private SensitiveFieldServiceTest.TestDto dto;
}
