package org.artifactory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ValidatorFactory;
import java.util.HashSet;
import java.util.Set;

/**
 * Uses hibernate validator factory to validate the sent object and throw if constraints are violated
 *
 * @author Tomer Slutsky
 */
public class ConfigValidation {
    private static final Logger log = LoggerFactory.getLogger(ConfigValidation.class);

    private final ValidatorFactory validatorFactory;

    public ConfigValidation(ValidatorFactory factory) {
        this.validatorFactory = factory;
    }

    public void validateCollection(Iterable itr) {
        if (itr != null) {
            for (Object obj : itr) {
                this.validate(obj);
            }
        }
    }

    public ValidatorFactory getValidatorFactory() {
        return this.validatorFactory;
    }

    public void validate(Object object) {
        if (object != null) {
            if (object instanceof Iterable) {
                this.validateCollection((Iterable) object);
            }

            long timer = System.currentTimeMillis();
            Set<ConstraintViolation<Object>> violations = this.validatorFactory.getValidator().validate(object);
            log.trace("validated Diff in {} milliseconds",
                    System.currentTimeMillis() - timer);

            if (violations.size() > 0) {
                Set<String> violationMessages = new HashSet<>();
                for (ConstraintViolation<Object> violation : violations) {
                    violationMessages.add(violation.getMessage());
                }

                throw new IllegalStateException("Found config violations: " + String.join(", ", violationMessages));
            }
        }
    }
}
