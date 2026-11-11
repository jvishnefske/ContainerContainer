package org.artifactory.throttling.lh.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.throttling.lh.model.ActivityConfig;
import org.artifactory.util.AolUtils;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.loadHealerActivitiesList;
import static org.artifactory.throttling.lh.model.ReactiveActivityState.IDLE;

/**
 * Configures dynamic activity properties for the Load Healer.
 * This class loads activity-related configurations from a YAML file, resolves dynamic values using Spring Expression
 * Language (SpEL), and provides access to the configured activities.
 *
 * @author Hussein Alshqerat
 */
@Getter
@Slf4j
@Component
public class LoadHealerDynamicActivitiesConfig {

    private static final String LH_CONFIG_PREFIX = "artifactory.lh.";
    private static final int PROPERTY_SEGMENT_LENGTH = 5;
    private static final Set<String> TEST_ONLY_ACTIVITIES = Set.of("example", "test");

    private final Map<String, ActivityConfig> activitiesMap = new HashMap<>();
    private final Environment environment;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    private final BiConsumer<String, Object> logFunction = AolUtils.isAol() ? log::info : log::debug;
    @Getter
    private final long maxThreads;
    @Getter(lazy = true)
    private final Set<String> registeredActivitiesList = initializeActivitiesList();

    public LoadHealerDynamicActivitiesConfig(Environment environment) {
        this.environment = environment;
        maxThreads = Long.parseLong(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200"));
        initializeActivities();
    }

    /**
     * Loads and initializes activities by reading from the YAML configuration and resolving dynamic values.
     */
    void initializeActivities() {
        try {
            Properties yamlProperties = loadYamlProperties();
            if (yamlProperties == null) {
                log.warn("No properties found in YAML configuration.");
                return;
            }

            Map<String, ActivityPropertiesBuilder> builderMap = processYamlProperties(yamlProperties);
            populateActivitiesMap(builderMap);
            logLoadedActivities();
        } catch (Exception e) {
            log.error("Error initializing activities from YAML properties", e);
        }
    }

    /**
     * Loads the YAML configuration file and converts it into a {@link Properties} object.
     *
     * @return Properties loaded from the YAML file, or null if loading fails.
     */
    Properties loadYamlProperties() {
        YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
        yamlFactory.setResources(new ClassPathResource("application.yaml"));
        return yamlFactory.getObject();
    }

    /**
     * Processes YAML properties to group them by activity and prepare for configuration.
     *
     * @param yamlProperties Properties loaded from the YAML file.
     * @return A map of activity names to their respective {@link ActivityPropertiesBuilder} instances.
     */
    Map<String, ActivityPropertiesBuilder> processYamlProperties(Properties yamlProperties) {
        Map<String, ActivityPropertiesBuilder> builderMap = new HashMap<>();
        yamlProperties.forEach((key, value) -> {
            String keyStr = key.toString();
            if (keyStr.startsWith(LH_CONFIG_PREFIX)) {
                processActivityProperty(builderMap, keyStr, value.toString());
            }
        });
        log.info("Processed {} activity properties from YAML configuration.", builderMap.size());
        return builderMap;
    }

    /**
     * Processes a single activity property and assigns it to the appropriate builder.
     *
     * @param builderMap Map of activity names to their property builders.
     * @param key        The configuration key.
     * @param value      The raw value of the property.
     */
    private void processActivityProperty(Map<String, ActivityPropertiesBuilder> builderMap, String key, String value) {
        String[] parts = key.split("\\.");
        String activityName = parts[2];
        if (!getRegisteredActivitiesList().contains(activityName)) {
            if (!TEST_ONLY_ACTIVITIES.contains(activityName)) {
                log.info("Skipping activity '{}' because it is not in the activities list.", activityName);
            }
            return;
        }
        if (parts.length >= PROPERTY_SEGMENT_LENGTH) {
            // Handle categorized properties like thresholds
            String category = parts[3];
            String property = parts[4];
            String resolvedValue = resolveDynamicValue(key, value);
            builderMap.computeIfAbsent(activityName, ActivityPropertiesBuilder::new)
                    .setProperty(category, property, resolvedValue);
        } else {
            log.warn("Invalid property format: {}", key);
        }
    }

    private Set<String> initializeActivitiesList() {
        String activities = loadHealerActivitiesList.getString();
        return Arrays.stream(StringUtils.split(activities, ",")).map(String::trim).collect(Collectors.toSet());
    }

    /**
     * Populates the activities map by building activity instances from their builders.
     *
     * @param builderMap A map of activity names to their respective builders.
     */
    private void populateActivitiesMap(Map<String, ActivityPropertiesBuilder> builderMap) {
        builderMap.forEach((activityName, builder) -> activitiesMap.put(activityName, builder.build()));
    }

    private void logLoadedActivities() {
        logFunction.accept("Loaded {} activities:", activitiesMap.size());
        activitiesMap.forEach((name, activity) -> logFunction.accept("{}", activity));
    }

    /**
     * Resolves dynamic property values using the environment and SpEL.
     *
     * @param key          The property key.
     * @param defaultValue The default value if no override is found.
     * @return The resolved property value.
     */
    String resolveDynamicValue(String key, String defaultValue) {
        try {
            String overriddenValue = environment.getProperty(key, defaultValue);
            String resolvedValue = environment.resolvePlaceholders(overriddenValue);
            return spelParser.parseExpression(resolvedValue).getValue(String.class);
        } catch (Exception e) {
            if (AolUtils.isAol()) {
                log.warn("Failed to resolve dynamic value for key '{}'. Using default '{}'", key, defaultValue);
            } else {
                log.debug("Failed to resolve dynamic value for key '{}'. Using default '{}'", key, defaultValue);
            }
            return defaultValue;
        }
    }

    /**
     * Builder class for constructing {@link ActivityConfig} instances based on properties.
     */
    static class ActivityPropertiesBuilder {
        public static final String THRESHOLD_HEALTHY_TIMEOUT = "threshold.healthyTimeout";
        public static final String THRESHOLD_MAX = "threshold.max";
        public static final String THRESHOLD_MIN = "threshold.min";

        private final String activityName;
        private long minThreshold = 0;
        private long maxThreshold = 0;
        private long healthyTimeout = 0;

        /**
         * Initializes the builder for a specific activity.
         *
         * @param activityName The name of the activity.
         */
        public ActivityPropertiesBuilder(String activityName) {
            this.activityName = activityName;
        }

        /**
         * Sets a property value in the builder.
         *
         * @param category The property category (e.g., "threshold").
         * @param property The specific property (e.g., "max").
         * @param value    The value to set.
         */
        public void setProperty(String category, String property, String value) {
            try {
                double parsedValue = Double.parseDouble(value);
                switch (category + "." + property) {
                    case THRESHOLD_MAX -> maxThreshold = (long) parsedValue;
                    case THRESHOLD_MIN -> minThreshold = (long) parsedValue;
                    case THRESHOLD_HEALTHY_TIMEOUT -> healthyTimeout = (long) parsedValue;
                    default -> log.warn("Unknown property: {}.{}", category, property);
                }
            } catch (NumberFormatException e) {
                if (AolUtils.isAol()) {
                    log.warn("Invalid number format for {}.{}: {}", category, property, value);
                } else {
                    log.debug("Invalid number format for {}.{}: {}", category, property, value);
                }
            }
        }

        /**
         * Builds an {@link ActivityConfig} instance with the configured properties.
         *
         * @return The constructed {@link ActivityConfig}.
         */
        public ActivityConfig build() {
            return new ActivityConfig(activityName, minThreshold, maxThreshold, healthyTimeout, IDLE);
        }
    }
}
