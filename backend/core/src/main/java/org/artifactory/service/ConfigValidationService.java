package org.artifactory.service;

import org.artifactory.common.ConstantValues;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.springframework.stereotype.Service;

import jakarta.validation.Validation;

/**
 * This class is used to access {@link ConfigValidation} and to initiate the validation factory
 *
 * @author Tomer Slutsky
 */
@Service
public class ConfigValidationService {
    private final ConfigValidation configValidation;

    public ConfigValidationService() {
        this.configValidation = new ConfigValidation(Validation.byProvider(HibernateValidator.class)
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory());
    }

    public void validate(Object object) {
        if (ConstantValues.centralConfigValidation.getBoolean()) {
            this.configValidation.validate(object);
        }
    }

    public void validateCollection(Iterable itr) {
        if (ConstantValues.centralConfigValidation.getBoolean()) {
            this.configValidation.validateCollection(itr);
        }
    }
}
