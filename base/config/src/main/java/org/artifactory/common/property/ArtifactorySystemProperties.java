/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.common.property;

import com.google.common.collect.ImmutableMap;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.confprop.ConfProps;
import org.artifactory.version.CompoundVersionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.SYS_PROP_PREFIX;

/**
 * @author yoavl
 */
public class ArtifactorySystemProperties implements ConfProps {
    private static final Logger log = LoggerFactory.getLogger(ArtifactorySystemProperties.class);

    private static final ImmutableMap<String, PropertyMapper> DEPRECATED =
            ImmutableMap.<String, PropertyMapper>builder()
                    .put("artifactory.authenticationCacheIdleTimeSecs",
                            new SamePropertyMapper("artifactory.authentication.cache.idleTimeSecs"))
                    .put("artifactory.maven.suppressPomConsistencyChecks", new NullPropertyMapper())
                    .put("artifactory.metadataCacheIdleTimeSecs", new NullPropertyMapper())
                    .put("artifactory.logs.refreshrate.secs",
                            new SamePropertyMapper("artifactory.logs.viewRefreshRateSecs"))
                    .put("artifactory.spring.configPath", new SamePropertyMapper("artifactory.spring.configDir"))
                    .put("artifactory.lockTimeoutSecs", new SamePropertyMapper("artifactory.locks.timeoutSecs"))
                    .put("artifactory.xmlAdditionalMimeTypeExtensions", new NullPropertyMapper())
                    .put("repo.cleanup.intervalHours", new NullPropertyMapper())
                    .put("bintray.system.user", new MovedToConfigPropertyMapper(
                            "The bintray config section in the admin page"))
                    .put("bintray.system.api.key", new MovedToConfigPropertyMapper(
                            "The bintray config section in the admin page"))
                    .put("artifactory.security.useBase64", new NullPropertyMapper())
                    .put("artifactory.security.authentication.encryptedPassword.surroundChars",
                            new NullPropertyMapper())
                    .build();
    /**
     * The combined properties of System, artifactory.system.properties file and artifactory.properties file. All system
     * properties starting with 'artifactory.' will be included.
     */
    private Properties nonEnumArtifactoryProperties = new Properties();
    private EnumMap<ConstantValues, String> artifactoryProperties = new EnumMap<>(ConstantValues.class);
    private Map<String, ConstantValues> artifactoryPropertiesByKeys = new HashMap<>();

    /**
     * Caching parseLong values from the properties above
     */
    private final EnumMap<ConstantValues, Long> artifactoryLongProperties = new EnumMap<>(ConstantValues.class);
    private final EnumMap<ConstantValues, Boolean> artifactoryBooleanProperties = new EnumMap<>(ConstantValues.class);
    private final EnumMap<ConstantValues, List<String>> artifactoryListProperties = new EnumMap<>(ConstantValues.class);

    private Map<String, String> stringsCache = new ConcurrentHashMap<>();
    private Map<String, Integer> intsCache = new ConcurrentHashMap<>();
    private Map<String, Long> longsCache = new ConcurrentHashMap<>();
    private Map<String, Boolean> booleansCache = new ConcurrentHashMap<>();

    /**
     * A map of substitute repo keys (oldKey:newKey) for supporting old repository keys (that are invalid xml ids).
     */
    private Map<String, String> substituteRepoKeys = new HashMap<>();

    private void loadSystemProperties(Properties result) {
        Properties properties = (Properties) System.getProperties().clone(); // avoid concurrent modification
        for (Object key : properties.keySet()) {
            String sKey = (String) key;
            if (sKey.startsWith(SYS_PROP_PREFIX)) {
                result.put(sKey, properties.getProperty(sKey));
            }
        }
    }

    private static void validateConstants(Properties artProps) {
        String chrootPropertyName = ConstantValues.uiChroot.getPropertyName();
        String chroot = (String) artProps.get(chrootPropertyName);
        if (StringUtils.isNotBlank(chroot) && !new File(chroot).exists()) {
            artProps.remove(chrootPropertyName);
            log.error("Selected chroot '{}' does not exist. Ignoring property value!", chroot);

        }
    }

    private static void handleDeprecatedProps(Properties artProps) {
        //Test the deprecated props against the current props
        Properties autoReplaced = new Properties();
        for (Object key : artProps.keySet()) {
            String prop = (String) key;
            if (DEPRECATED.containsKey(prop)) {
                PropertyMapper mapper = DEPRECATED.get(prop);
                String newProp = mapper.getNewPropertyName();
                String suggestion = newProp == null ? "this property is no longer in use." :
                        "please use: '" + newProp + "' instead.";
                log.warn("Usage of deprecated artifactory system property detected: '{}' - '{}'", prop, suggestion);
                //Check if property can be automatically replaced
                String value = (String) artProps.get(prop);
                String newValue = mapper.map(value);
                if (newProp == null) {
                    newProp = prop;
                }
                if (newValue != null) {
                    autoReplaced.put(newProp, newValue);
                    log.warn("Deprecated artifactory system property '{}={}' auto-replaced with '{}={}'.", prop, value,
                            newProp, newValue);
                }
            }
        }
        artProps.putAll(autoReplaced);
    }

    private static Map<String, String> fillRepoKeySubstitute(Properties artProps) {
        Map<String, String> result = new HashMap<>();
        String prefix = ConstantValues.substituteRepoKeys.getPropertyName();
        for (Object o : artProps.keySet()) {
            String key = (String) o;
            if (key.startsWith(prefix)) {
                String oldRepoKey = key.substring(prefix.length());
                String newRepoKey = (String) artProps.get(key);
                result.put(oldRepoKey, newRepoKey);
            }
        }
        // Remove the keys used
        for (String key : result.keySet()) {
            artProps.remove(prefix + key);
        }
        return result;
    }

    public String getProperty(ConstantValues key) {
        String result = artifactoryProperties.get(key);
        if (result == null) {
            return key.getDefValue();
        }
        return result;
    }

    public boolean hasProperty(ConstantValues key) {
        return artifactoryProperties.containsKey(key);
    }

    public String getProperty(String key, @Nullable String defaultValue) {
        return nonEnumArtifactoryProperties.getProperty(key, defaultValue);
    }

    public Long getLongProperty(ConstantValues key) {
        Long result = artifactoryLongProperties.get(key);
        if (result == null) {
            String strValue = getProperty(key);
            if (strValue == null) {
                result = 0L;
            } else {
                result = Long.parseLong(strValue.trim());
            }
            artifactoryLongProperties.put(key, result);
        }
        return result;
    }

    public Boolean getBooleanProperty(ConstantValues key) {
        Boolean result = artifactoryBooleanProperties.get(key);
        if (result == null) {
            String strValue = getProperty(key);
            if (strValue == null) {
                result = Boolean.FALSE;
            } else {
                result = Boolean.parseBoolean(strValue);
            }
            artifactoryBooleanProperties.put(key, result);
        }
        return result;
    }

    @NonNull
    public List<String> getListProperty(ConstantValues key) {
        List<String> result = artifactoryListProperties.get(key);
        if (result == null) {
            result = parseList(getProperty(key));
            artifactoryListProperties.put(key, result);
        }
        return result;
    }

    public Map<String, String> getSubstituteRepoKeys() {
        return substituteRepoKeys;
    }

    public void loadArtifactorySystemProperties(File systemPropertiesFile,
            @Nullable CompoundVersionDetails runningVersion, @Nullable CompoundVersionDetails runningProductVersion) {
        Properties combinedProperties = new Properties();
        if (systemPropertiesFile != null && systemPropertiesFile.exists()) {
            try (FileInputStream fis = new FileInputStream(systemPropertiesFile);) {
                combinedProperties.load(fis);
            } catch (Exception e) {
                throw new RuntimeException("Could not read default system properties from '" +
                        systemPropertiesFile.getAbsolutePath() + "'.", e);
            }
        }
        // Override with System properties
        loadSystemProperties(combinedProperties);

        Set<String> setAsSystemProp = new HashSet<>();
        //Cleanup all non-artifactory system properties and set them as system properties
        for (Object key : combinedProperties.keySet()) {
            String propName = (String) key;
            String propValue = combinedProperties.getProperty(propName);
            if (!propName.startsWith(SYS_PROP_PREFIX)) {
                // TODO: mainly for derby db properties, find another way of doing it
                System.setProperty(propName, propValue);
                setAsSystemProp.add(propName);
            }
        }
        for (String key : setAsSystemProp) {
            combinedProperties.remove(key);
        }

        substituteRepoKeys = fillRepoKeySubstitute(combinedProperties);

        //Test for deprecated properties and warn
        handleDeprecatedProps(combinedProperties);

        validateConstants(combinedProperties);

        // Use the EnumMap as much as possible
        EnumMap<ConstantValues, String> newArtifactoryProperties = new EnumMap<>(ConstantValues.class);
        Map<String, ConstantValues> newArtifactoryPropertiesByKeys = new HashMap<>();

        for (ConstantValues constVal : ConstantValues.values()) {
            Object val = combinedProperties.remove(constVal.getPropertyName());
            if (val != null) {
                newArtifactoryProperties.put(constVal, (String) val);
            }
        }
        if (runningVersion != null) {
            newArtifactoryProperties.put(ConstantValues.artifactoryServiceVersion, runningVersion.getVersionName());
            newArtifactoryProperties
                    .put(ConstantValues.artifactoryServiceRevision, String.valueOf(runningVersion.getRevision()));
            newArtifactoryProperties
                    .put(ConstantValues.artifactoryServicePackageHandlerVersion,
                            String.valueOf(runningVersion.getPackageHandlerVersion()));
            newArtifactoryProperties.put(ConstantValues.artifactoryServiceBuildNumber, runningVersion.getBuildNumber());
            newArtifactoryProperties
                    .put(ConstantValues.artifactoryServiceTimestamp, Long.toString(runningVersion.getTimestamp()));
        }
        if (runningProductVersion != null) {
            newArtifactoryProperties.put(ConstantValues.artifactoryProductVersion,
                    runningProductVersion.getVersionName());
            newArtifactoryProperties.put(ConstantValues.artifactoryProductRevision,
                    String.valueOf(runningProductVersion.getRevision()));
            newArtifactoryProperties.put(ConstantValues.artifactoryProductBuildNumber,
                    runningProductVersion.getBuildNumber());
            newArtifactoryProperties.put(ConstantValues.artifactoryProductTimestamp,
                    Long.toString(runningProductVersion.getTimestamp()));
        }
        Arrays.stream(ConstantValues.values()).forEach(cv -> {
            newArtifactoryPropertiesByKeys.put(cv.getPropertyName(), cv);
        });
        artifactoryProperties = newArtifactoryProperties;
        artifactoryPropertiesByKeys = newArtifactoryPropertiesByKeys;

        // TODO: Print a message when combined props is not empty as this should not happen.
        // It's probably a typo! But it's used for special security access values not declared in ConstantValues
        nonEnumArtifactoryProperties = combinedProperties;
        artifactoryBooleanProperties.clear();
        artifactoryLongProperties.clear();
        artifactoryListProperties.clear();
        clearCaches();
    }


    public void storeArtifactorySystemProperties(File systemPropertiesFile) throws IOException {
        Properties propsTostore = getAllArtifactoryProperties();
        try (FileOutputStream os = new FileOutputStream(systemPropertiesFile)) {
            propsTostore.store(os, "");
        }
    }

    public Properties getAllArtifactoryProperties() {
        Properties props = new Properties();
        props.putAll(nonEnumArtifactoryProperties);
        Set<Map.Entry<ConstantValues, String>> constantValues = artifactoryProperties.entrySet();
        for (Map.Entry<ConstantValues, String> entry : constantValues) {
            log.debug("Adding property {}={}", entry.getKey().getPropertyName(), entry.getValue());
            if (entry.getValue() == null) {
                log.error("Property {} is null", entry.getKey().getPropertyName());
            }
            props.setProperty(entry.getKey().getPropertyName(), entry.getValue());
        }
        return props;
    }

    /**
     * Returns the propertiesCopy object
     *
     * @return Properties - The copy of the artifactoryProperties object
     */
    public Properties getPropertiesCopy() {
        Properties propertiesCopy = new Properties();
        for (Map.Entry<ConstantValues, String> entry : artifactoryProperties.entrySet()) {
            propertiesCopy.put(entry.getKey().getPropertyName(), String.valueOf(entry.getValue()));
        }
        propertiesCopy.putAll(nonEnumArtifactoryProperties);
        return propertiesCopy;
    }

    /**
     * Set the value of the given key
     *
     * @param propertyName Property key
     * @param value        Property value
     */
    public void setProperty(String propertyName, String value) {
        ConstantValues key = null;
        for (ConstantValues val : ConstantValues.values()) {
            if (val.getPropertyName().equals(propertyName)) {
                key = val;
                break;
            }
        }
        if (key != null) {
            artifactoryProperties.put(key, value);
            //Update the caches too. Only ConstantValues have a cache entry
            artifactoryLongProperties.computeIfPresent(key, (k, v) -> Long.valueOf(value));
            artifactoryBooleanProperties.computeIfPresent(key, (k, v) -> Boolean.valueOf(value));
            artifactoryListProperties.computeIfPresent(key, (k, v) -> parseList(value));

            clearCaches(key.getPropertyName());

        } else {
            clearCaches(propertyName);
            nonEnumArtifactoryProperties.put(propertyName, value);
        }
    }

    /**
     * Parses a string by comma (,) delimiter, and trim the results.
     */
    @NonNull
    private List<String> parseList(String value) {
        if (value == null) {
            return List.of();
        }
        try {
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(r -> !r.isEmpty())
                    .collect(Collectors.toUnmodifiableList());
        } catch (Exception e) {
            log.error("Unable to parse {} as a list: {}", value, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Removes the value of the given key
     *
     * @param key Property constant values enum entry
     * @return the prev value or null
     */
    public String removeProperty(ConstantValues key) {
        artifactoryBooleanProperties.remove(key);
        artifactoryLongProperties.remove(key);
        artifactoryListProperties.remove(key);

        clearCaches(key.getPropertyName());

        return artifactoryProperties.remove(key);
    }

    private void clearCaches(String key) {
        stringsCache.remove(key);
        intsCache.remove(key);
        longsCache.remove(key);
        booleansCache.remove(key);
    }

    private void clearCaches() {
        stringsCache.clear();
        intsCache.clear();
        longsCache.clear();
        booleansCache.clear();
    }

    @Override
    public String getStringValue(String key, String def) {
        return getValue(key, def, (value) -> value, ConstantValues::getString, stringsCache);
    }

    @Override
    public boolean isSet(String key) {
        key = SYS_PROP_PREFIX + key;
        return artifactoryPropertiesByKeys.containsKey(key) && artifactoryPropertiesByKeys.get(key).isSet();
    }

    @Override
    public Boolean getBooleanValue(String key, Boolean def) {
        return getValue(key, def, Boolean::valueOf, ConstantValues::getBoolean, booleansCache);
    }

    @Override
    public Long getLongValue(String key, Long def) {
        return getValue(key, def, Long::valueOf, ConstantValues::getLong, longsCache);
    }

    @Override
    public Integer getIntValue(String key, Integer def) {
        return getValue(key, def, Integer::valueOf, ConstantValues::getInt, intsCache);
    }

    public List<String> getListProperty(@NonNull String key, List<String> def) {
        String value = getStringValue(key, "");
        List<String> result = parseList(value);
        if (CollectionUtils.isEmpty(result)) {
            result = def;
        }
        return result;
    }


    private <T> T getValue(String key, T def, Function<String, T> fromString,
            Function<ConstantValues, T> fromConstantValue, Map<String, T> cache) {
        return cache.computeIfAbsent(SYS_PROP_PREFIX + key, (longKey) -> {
            T result = null;
            String value = getString(longKey, key);
            if (value != null) {
                result = fromString.apply(value);
            } else {
                ConstantValues constantValue = artifactoryPropertiesByKeys.get(longKey);
                if (constantValue != null) {
                    result = fromConstantValue.apply(constantValue);
                }
            }
            return result != null ? result : def;
        });
    }

    private String getString(String... keys) {
        for (String key : keys) {
            String value = getProperty(key, null);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}

