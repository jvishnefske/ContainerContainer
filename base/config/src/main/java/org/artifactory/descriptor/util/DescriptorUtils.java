package org.artifactory.descriptor.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.Verifier;
import org.jfrog.common.validator.NameValidator;

import java.util.Optional;

@UtilityClass
public class DescriptorUtils {

    public void assertNoSpecialCharactersAndValidXmlName(String key, String errorMessagePrefix) {
        Optional<String> nameValidationResult = NameValidator.validate(key);
        if (nameValidationResult.isPresent()) {
            throw new IllegalArgumentException(errorMessagePrefix + " is illegal: " + nameValidationResult.get());
        }
        String verifierResult = Verifier.checkXMLName(key);
        if (StringUtils.isNotBlank(verifierResult)) {
            throw new IllegalArgumentException(errorMessagePrefix + " is illegal: " + verifierResult);
        }
    }
}
