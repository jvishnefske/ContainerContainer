package org.artifactory.throttling.lh.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throttling.lh.model.ActivityConfig;
import org.artifactory.throttling.lh.model.ReactiveActivityState;
import org.artifactory.util.AolUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
@ExtendWith(MockitoExtension.class)
class LoadHealerDynamicActivitiesConfigTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private Environment environment;
    private MemoryAppender memoryAppender;

    @BeforeEach
    void setUp() {
        memoryAppender = MemoryAppenderUtil.createMemoryAppender(LoadHealerDynamicActivitiesConfig.class, Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        memoryAppender.stop();
    }

    @Test
    void testInitializeActivities() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(true);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList, "example,devTypo");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Map<String, ActivityConfig> activitiesMap = config.getActivitiesMap();
            assertNotNull(activitiesMap, "Activities map should not be null");
            assertEquals(1, activitiesMap.size(), "Activities map size should be 1");
            ActivityConfig activityConfig = activitiesMap.get("example");
            assertEquals("example", activityConfig.name());
            assertEquals(1, activityConfig.minThreshold());
            assertEquals(2, activityConfig.maxThreshold());
            assertEquals(300, activityConfig.healthyTimeout());
            assertEquals(ReactiveActivityState.IDLE, activityConfig.reactiveState());
            assertEquals(200, config.getMaxThreads());
        }
    }

    @Test
    void testResolveDynamicValueAolModeLogsWarn() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(true);
            when(environment.getProperty("invalid.key", "default")).thenReturn("${invalid.expression}");
            when(environment.resolvePlaceholders("${invalid.expression}")).thenReturn("${invalid.expression}");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            String result = config.resolveDynamicValue("invalid.key", "default");
            assertEquals("default", result);
            List<ILoggingEvent> logEvents = memoryAppender.list;
            assertTrue(logEvents.stream()
                    .anyMatch(event -> event.getLevel() == Level.WARN &&
                            event.getFormattedMessage()
                                    .contains("Failed to resolve dynamic value for key 'invalid.key'")));
        }
    }

    @Test
    void testResolveDynamicValueNonAolModeLogsDebug() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            when(environment.getProperty("invalid.key", "default")).thenReturn("${invalid.expression}");
            when(environment.resolvePlaceholders("${invalid.expression}")).thenReturn("${invalid.expression}");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            String result = config.resolveDynamicValue("invalid.key", "default");
            assertEquals("default", result);
            List<ILoggingEvent> logEvents = memoryAppender.list;
            assertTrue(logEvents.stream()
                    .anyMatch(event -> event.getLevel() == Level.DEBUG &&
                            event.getFormattedMessage()
                                    .contains("Failed to resolve dynamic value for key 'invalid.key'")));
        }
    }

    @Test
    void testLogLoadedActivitiesAolModeLogsInfo() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(true);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            new LoadHealerDynamicActivitiesConfig(environment);
            List<ILoggingEvent> logEvents = memoryAppender.list;
            assertTrue(logEvents.stream()
                    .anyMatch(event -> event.getLevel() == Level.INFO &&
                            event.getFormattedMessage().contains("Loaded") &&
                            event.getFormattedMessage().contains("activities:")));
        }
    }

    @Test
    void testLogLoadedActivitiesNonAolModeLogsDebug() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            new LoadHealerDynamicActivitiesConfig(environment);
            List<ILoggingEvent> logEvents = memoryAppender.list;
            assertTrue(logEvents.stream()
                    .anyMatch(event -> event.getLevel() == Level.DEBUG &&
                            event.getFormattedMessage().contains("Loaded") &&
                            event.getFormattedMessage().contains("activities:")));
        }
    }

    @Test
    void testSetPropertyAolModeLogsWarn() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(true);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            new LoadHealerDynamicActivitiesConfig(environment);
            LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder builder =
                    new LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder("test-activity");
            builder.setProperty("threshold", "max", "invalid-number");
            List<ILoggingEvent> logEvents = memoryAppender.list;
            assertTrue(logEvents.stream()
                    .anyMatch(event -> event.getLevel() == Level.WARN &&
                            event.getFormattedMessage()
                                    .contains("Invalid number format for threshold.max: invalid-number")));
        }
    }

    @Test
    void testSetPropertyNonAolModeLogsDebug() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            new LoadHealerDynamicActivitiesConfig(environment);
            LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder builder =
                    new LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder("test-activity");
            builder.setProperty("threshold", "max", "invalid-number");
            List<ILoggingEvent> logEvents = memoryAppender.list;
            assertTrue(logEvents.stream()
                    .anyMatch(event -> event.getLevel() == Level.DEBUG &&
                            event.getFormattedMessage()
                                    .contains("Invalid number format for threshold.max: invalid-number")));
        }
    }

    @Test
    void testProcessYamlPropertiesFiltersActivitiesOnlyProcessesRegisteredActivities() throws Exception {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList, "activity1,activity2");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Properties yamlProperties = new Properties();
            yamlProperties.setProperty("artifactory.lh.activity1.threshold.min", "10");
            yamlProperties.setProperty("artifactory.lh.activity2.threshold.max", "20");
            yamlProperties.setProperty("artifactory.lh.anotherUnregistered.threshold.min", "5");
            Method processMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("processYamlProperties", Properties.class);
            processMethod.setAccessible(true);
            Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder> builderMap =
                    (Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder>)
                            processMethod.invoke(config, yamlProperties);
            assertEquals(2, builderMap.size(), "Only registered activities should be in builder map");
            assertTrue(builderMap.containsKey("activity1"), "activity1 should be processed");
            assertTrue(builderMap.containsKey("activity2"), "activity2 should be processed");
            assertFalse(builderMap.containsKey("unregisteredActivity"),
                    "unregisteredActivity should NOT be processed");
            assertFalse(builderMap.containsKey("anotherUnregistered"),
                    "anotherUnregistered should NOT be processed");
        }
    }

    @Test
    void testProcessYamlPropertiesAlwaysUsesEnabledActivitiesList() throws Exception {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList,
                    "reactiveActivity1,reactiveActivity2,enabledModeActivity");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Set<String> registeredActivities = config.getRegisteredActivitiesList();
            assertEquals(3, registeredActivities.size());
            assertTrue(registeredActivities.contains("reactiveActivity1"));
            assertTrue(registeredActivities.contains("reactiveActivity2"));
            assertTrue(registeredActivities.contains("enabledModeActivity"));
            Properties yamlProperties = new Properties();
            yamlProperties.setProperty("artifactory.lh.reactiveActivity1.threshold.max", "10");
            yamlProperties.setProperty("artifactory.lh.reactiveActivity2.threshold.min", "15");
            yamlProperties.setProperty("artifactory.lh.enabledModeActivity.threshold.max", "20");
            Method processMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("processYamlProperties", Properties.class);
            processMethod.setAccessible(true);
            Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder> builderMap =
                    (Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder>)
                            processMethod.invoke(config, yamlProperties);
            assertEquals(3, builderMap.size());
            assertTrue(builderMap.containsKey("reactiveActivity1"));
            assertTrue(builderMap.containsKey("reactiveActivity2"));
            assertTrue(builderMap.containsKey("enabledModeActivity"),
                    "All activities from enabled list should be processed regardless of mode");
        }
    }

    @Test
    void testProcessYamlPropertiesEmptyActivitiesListProcessesNothing() throws Exception {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList, "");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Set<String> registeredActivities = config.getRegisteredActivitiesList();
            assertTrue(registeredActivities.isEmpty(), "Registered activities list should be empty");
            Properties yamlProperties = new Properties();
            yamlProperties.setProperty("artifactory.lh.activity2.threshold.min", "10");
            Method processMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("processYamlProperties", Properties.class);
            processMethod.setAccessible(true);
            Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder> builderMap =
                    (Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder>)
                            processMethod.invoke(config, yamlProperties);
            assertTrue(builderMap.isEmpty(), "No activities should be processed when list is empty");
        }
    }

    @Test
    void testProcessYamlPropertiesWhitespaceInActivitiesListTrimsCorrectly() throws Exception {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList,
                    " activity1 , activity2 , activity3 ");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Set<String> registeredActivities = config.getRegisteredActivitiesList();
            assertEquals(3, registeredActivities.size());
            assertTrue(registeredActivities.contains("activity1"));
            assertTrue(registeredActivities.contains("activity2"));
            assertTrue(registeredActivities.contains("activity3"));
            Properties yamlProperties = new Properties();
            yamlProperties.setProperty("artifactory.lh.activity1.threshold.max", "5");
            yamlProperties.setProperty("artifactory.lh.activity2.threshold.min", "10");
            yamlProperties.setProperty("artifactory.lh.activity3.threshold.max", "20");
            Method processMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("processYamlProperties", Properties.class);
            processMethod.setAccessible(true);
            Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder> builderMap =
                    (Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder>)
                            processMethod.invoke(config, yamlProperties);
            assertEquals(3, builderMap.size());
            assertTrue(builderMap.containsKey("activity1"));
            assertTrue(builderMap.containsKey("activity2"));
            assertTrue(builderMap.containsKey("activity3"));
        }
    }

    @Test
    void testProcessYamlPropertiesPartialMatchOnlyProcessesMatchingActivities() throws Exception {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList, "allowedActivity");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Properties yamlProperties = new Properties();
            yamlProperties.setProperty("artifactory.lh.allowedActivity.threshold.min", "10");
            yamlProperties.setProperty("artifactory.lh.blockedActivity2.threshold.max", "30");
            Method processMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("processYamlProperties", Properties.class);
            processMethod.setAccessible(true);
            Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder> builderMap =
                    (Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder>)
                            processMethod.invoke(config, yamlProperties);
            assertEquals(1, builderMap.size());
            assertTrue(builderMap.containsKey("allowedActivity"));
            assertFalse(builderMap.containsKey("blockedActivity1"));
            assertFalse(builderMap.containsKey("blockedActivity2"));
        }
    }

    @Test
    void testProcessYamlPropertiesAllActivitiesFilteredEmptyBuilderMap() throws Exception {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList,
                    "otherActivity1,otherActivity2");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Properties yamlProperties = new Properties();
            yamlProperties.setProperty("artifactory.lh.unregistered2.threshold.min", "10");
            yamlProperties.setProperty("artifactory.lh.unregistered3.threshold.max", "20");
            Method processMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("processYamlProperties", Properties.class);
            processMethod.setAccessible(true);
            Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder> builderMap =
                    (Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder>)
                            processMethod.invoke(config, yamlProperties);
            assertTrue(builderMap.isEmpty(),
                    "All activities should be filtered out when none match registered list");
        }
    }

    @Test
    void testGetRegisteredActivitiesListEnabledModeReturnsEnabledActivities() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList,
                    "enabled1,enabled2,enabled3");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Set<String> registeredActivities = config.getRegisteredActivitiesList();
            assertEquals(3, registeredActivities.size());
            assertTrue(registeredActivities.contains("enabled1"));
            assertTrue(registeredActivities.contains("enabled2"));
            assertTrue(registeredActivities.contains("enabled3"));
        }
    }

    @Test
    void testGetRegisteredActivitiesListReactiveModeAlsoUsesEnabledList() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList, "enabled1,enabled2,enabled3");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Set<String> registeredActivities = config.getRegisteredActivitiesList();
            assertEquals(3, registeredActivities.size());
            assertTrue(registeredActivities.contains("enabled1"));
            assertTrue(registeredActivities.contains("enabled2"));
            assertTrue(registeredActivities.contains("enabled3"));
        }
    }

    @Test
    void testGetRegisteredActivitiesListAlwaysUsesEnabledActivities() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList, "default1,default2");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Set<String> registeredActivities = config.getRegisteredActivitiesList();
            assertEquals(2, registeredActivities.size());
            assertTrue(registeredActivities.contains("default1"));
            assertTrue(registeredActivities.contains("default2"));
        }
    }

    @Test
    void testReactiveModeLoadsAllActivitiesNotJustReactiveFilter() {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList,
                    "activity1,activity2,activity3,activity4,activity5,activity6,activity7,activity8,activity9");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Set<String> registeredActivities = config.getRegisteredActivitiesList();
            assertEquals(9, registeredActivities.size(),
                    "Reactive mode should load ALL 9 activities from enabled list");
            assertTrue(registeredActivities.contains("activity1"));
            assertTrue(registeredActivities.contains("activity9"));
        }
    }

    @Test
    void testActivitiesMapOnlyContainsActivitiesInBothConstantListAndYaml() throws Exception {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList,
                    "activity1,activity2,activity3");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Set<String> registeredActivities = config.getRegisteredActivitiesList();
            assertEquals(3, registeredActivities.size());
            assertTrue(registeredActivities.contains("activity1"));
            assertTrue(registeredActivities.contains("activity2"));
            assertTrue(registeredActivities.contains("activity3"));
            Properties yamlProperties = new Properties();
            yamlProperties.setProperty("artifactory.lh.activity1.threshold.min", "10");
            yamlProperties.setProperty("artifactory.lh.activity2.threshold.max", "20");
            Method processMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("processYamlProperties", Properties.class);
            processMethod.setAccessible(true);
            Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder> builderMap =
                    (Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder>)
                            processMethod.invoke(config, yamlProperties);
            Method populateMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("populateActivitiesMap", Map.class);
            populateMethod.setAccessible(true);
            populateMethod.invoke(config, builderMap);
            Map<String, ActivityConfig> activitiesMap = config.getActivitiesMap();
            assertTrue(activitiesMap.containsKey("activity1"),
                    "activity1 should be in activitiesMap (in both constant list and YAML)");
            assertTrue(activitiesMap.containsKey("activity2"),
                    "activity2 should be in activitiesMap (in both constant list and YAML)");
            assertFalse(activitiesMap.containsKey("activity3"),
                    "activity3 should NOT be in activitiesMap (in constant list but NOT in YAML)");
            assertEquals(2, activitiesMap.size(),
                    "activitiesMap should only contain activities present in both constant list and YAML");
        }
    }

    @Test
    void testActivitiesMapEmptyWhenActivityInConstantListButNotInYaml() throws Exception {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList,
                    "nonExistentActivity");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Set<String> registeredActivities = config.getRegisteredActivitiesList();
            assertEquals(1, registeredActivities.size());
            assertTrue(registeredActivities.contains("nonExistentActivity"));
            Properties yamlProperties = new Properties();
            yamlProperties.setProperty("artifactory.lh.differentActivity.threshold.min", "10");
            Method processMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("processYamlProperties", Properties.class);
            processMethod.setAccessible(true);
            Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder> builderMap =
                    (Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder>)
                            processMethod.invoke(config, yamlProperties);
            Method populateMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("populateActivitiesMap", Map.class);
            populateMethod.setAccessible(true);
            populateMethod.invoke(config, builderMap);
            Map<String, ActivityConfig> activitiesMap = config.getActivitiesMap();
            assertFalse(activitiesMap.containsKey("nonExistentActivity"),
                    "nonExistentActivity should NOT be in activitiesMap (in constant list but NOT in YAML)");
            assertFalse(activitiesMap.containsKey("differentActivity"),
                    "differentActivity should NOT be in activitiesMap (in YAML but NOT in constant list)");
            assertTrue(activitiesMap.isEmpty(),
                    "activitiesMap should be empty when no activity is in both constant list and YAML");
        }
    }

    @Test
    void testActivitiesMapRequiresBothConstantListAndYaml() throws Exception {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(false);
            when(environment.getProperty("artifactory.tomcat.connector.maxThreads", "200")).thenReturn("200");
            setStringSystemProperty(ConstantValues.loadHealerActivitiesList,
                    "validActivity,activityOnlyInConstantList");
            LoadHealerDynamicActivitiesConfig config = new LoadHealerDynamicActivitiesConfig(environment);
            Properties yamlProperties = new Properties();
            yamlProperties.setProperty("artifactory.lh.validActivity.threshold.min", "5");
            yamlProperties.setProperty("artifactory.lh.validActivity.threshold.max", "50");
            yamlProperties.setProperty("artifactory.lh.activityOnlyInYaml.threshold.min", "10");
            Method processMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("processYamlProperties", Properties.class);
            processMethod.setAccessible(true);
            Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder> builderMap =
                    (Map<String, LoadHealerDynamicActivitiesConfig.ActivityPropertiesBuilder>)
                            processMethod.invoke(config, yamlProperties);
            Method populateMethod = LoadHealerDynamicActivitiesConfig.class
                    .getDeclaredMethod("populateActivitiesMap", Map.class);
            populateMethod.setAccessible(true);
            populateMethod.invoke(config, builderMap);
            Map<String, ActivityConfig> activitiesMap = config.getActivitiesMap();
            assertTrue(activitiesMap.containsKey("validActivity"),
                    "validActivity should be in activitiesMap (in both constant list and YAML)");
            assertFalse(activitiesMap.containsKey("activityOnlyInConstantList"),
                    "activityOnlyInConstantList should NOT be in activitiesMap (in constant list but NOT in YAML)");
            assertFalse(activitiesMap.containsKey("activityOnlyInYaml"),
                    "activityOnlyInYaml should NOT be in activitiesMap (in YAML but NOT in constant list)");
            assertEquals(1, activitiesMap.size(),
                    "Only activities in both constant list and YAML should be in activitiesMap");
        }
    }
}