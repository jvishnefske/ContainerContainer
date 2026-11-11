package org.artifactory.spring;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * A missing bean condition matcher
 * Check if a bean has {@link ConditionalOnMissingBean} annotation and look for a bean from the type that specified in
 * the annotation value
 */
public class MissingBeanCondition implements ConfigurationCondition {
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.REGISTER_BEAN;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var targetBeanType = metadata.getAnnotations()
                .get(ConditionalOnMissingBean.class)
                .getValue("value", Class.class)
                .orElseThrow(() -> new RuntimeException("Failed to evaluate MissingBeanCondition"));
        // Resolve by bean definition only; do not call getBean() here — that can trigger
        // eager instantiation (e.g. @Component classes with constructor injection) while
        // @ConditionalOnMissingBean is still being evaluated.
        var beanNames = context.getBeanFactory().getBeanNamesForType(targetBeanType, true, false);
        return beanNames.length == 0;
    }
}
