package org.artifactory.repo.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.util.AolUtils;

/**
 * Remote registry URL and name.
 * The model is used to represent all the remote registries supported by Artifactory and to determine which one is the
 * default (See {@link DefaultState}).
 */
@AllArgsConstructor
@Getter
@Setter
public class RemoteRegistry {

    private String url;
    private String name;
    @JsonIgnore
    private DefaultState defaultState;

    @JsonProperty("default")
    public boolean isDefault() {
        if (defaultState == DefaultState.ALL) {
            return true;
        } else if (AolUtils.isAol()) {
            return defaultState == DefaultState.AOL;
        } else {
            return defaultState == DefaultState.ONPREM;
        }
    }

    public static RemoteRegistry ofDefault(String url, String name) {
        return new RemoteRegistry(url, name, DefaultState.ALL);
    }

    public static RemoteRegistry of(String url, String name) {
        return new RemoteRegistry(url, name, DefaultState.NONE);
    }

    /**
     * Determine what is default state of the current remote URL.
     * {@link DefaultState#ALL} - The URL is default for both onPrem and AOL.
     * {@link DefaultState#ONPREM} - The URL is default for onPrem only.
     * {@link DefaultState#AOL} - The URL is default for AOL only.
     * {@link DefaultState#NONE} - This is an additional registry URL.
     */
    public enum DefaultState {
        NONE, AOL, ONPREM, ALL
    }
}
