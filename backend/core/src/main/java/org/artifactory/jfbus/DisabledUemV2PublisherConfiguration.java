package org.artifactory.jfbus;

import org.artifactory.spring.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the no-op {@link DisabledUemV2Publisher} when the active publisher is not enabled
 * (the negation of {@link UemV2PublisherConfiguration#ENABLED_EXPRESSION}). Declaring the condition
 * at the class level keeps it out of the {@code REGISTER_BEAN} phase that crashes the full-distribution
 * context refresh; see {@link UemV2PublisherConfiguration} for details.
 */
@Configuration
@ConditionalOnExpression("!(" + UemV2PublisherConfiguration.ENABLED_EXPRESSION + ")")
public class DisabledUemV2PublisherConfiguration {

    @Bean
    public UemV2Publisher disabledUemV2Publisher() {
        return new DisabledUemV2Publisher();
    }
}
