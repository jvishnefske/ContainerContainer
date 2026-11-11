package org.artifactory.spring;

import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;


/**
 * {@link Condition} that checks if properties are defined in environment. Inspired by the corresponding
 * Spring Boot class.
 *
 * @see ConditionalOnProperty
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class OnPropertyCondition implements Condition {
    @Override
    public boolean matches(@Nonnull ConditionContext context, @Nonnull AnnotatedTypeMetadata metadata) {
        List<ConditionalOnProperty> conditionAnnotations;
        if (metadata.isAnnotated(ConditionalOnProperties.class.getName())) {
            conditionAnnotations = getConditionalOnProperties(metadata);
        } else {
            conditionAnnotations = getConditionalOnProperty(metadata);
        }

        for (ConditionalOnProperty annotation : conditionAnnotations) {
            if (!matches(annotation, context.getEnvironment())) {
                return false;
            }
        }

        return true;
    }

    boolean matches(ConditionalOnProperty annotation, Environment environment) {
        String prefix = annotation.prefix().trim();
        if (StringUtils.hasText(prefix) && !prefix.endsWith(".")) {
            prefix += ".";
        }

        String[] names = annotation.value().length == 0 ? annotation.name() : annotation.value();
        for (String name : names) {
            String key = prefix + name;
            if (environment.containsProperty(key)) {
                if (!isMatch(environment.getProperty(key), annotation.havingValue())) {
                    return false;
                }
            } else if (!annotation.matchIfMissing()) {
                return false;
            }
        }

        return true;
    }

    static boolean isMatch(String actualValue, String expectedValue) {
        if (StringUtils.hasLength(expectedValue)) {
            return expectedValue.equalsIgnoreCase(actualValue);
        }
        return !"false".equalsIgnoreCase(actualValue);
    }

    @Nonnull
    private static List<ConditionalOnProperty> getConditionalOnProperty(@Nonnull AnnotatedTypeMetadata metadata) {
        return metadata
                .getAnnotations()
                .stream(ConditionalOnProperty.class)
                .map(MergedAnnotation::synthesize)
                .toList();
    }

    @Nonnull
    private static List<ConditionalOnProperty> getConditionalOnProperties(@Nonnull AnnotatedTypeMetadata metadata) {
        return metadata
                .getAnnotations()
                .stream(ConditionalOnProperties.class)
                .map(MergedAnnotation::synthesize)
                .map(ConditionalOnProperties::value)
                .flatMap(Arrays::stream)
                .toList();
    }
}
