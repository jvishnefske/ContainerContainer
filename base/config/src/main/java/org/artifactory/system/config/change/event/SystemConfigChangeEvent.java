package org.artifactory.system.config.change.event;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import jakarta.validation.constraints.NotNull;

/**
 * Represent system configuration change event
 *
 * @author shayb
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class SystemConfigChangeEvent {
    private @NotNull SystemConfigChangeEventType systemConfigChangeEventType;
    private Object oldConfig;
    private Object newConfig;

    /**
     * System configuration event type
     */
    public enum SystemConfigChangeEventType {

        ARTIFACTORY_CONFIG_DESCRIPTOR("descriptor"),
        KEYSTORE_CONFIG_REMOVE_KEY_PAIR("remove key pair"),
        KEYSTORE_CONFIG_REMOVE_KEY_STORE("remove key store");

        private final String type;

        SystemConfigChangeEventType(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }
}
