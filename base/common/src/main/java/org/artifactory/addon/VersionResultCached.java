package org.artifactory.addon;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Getter
public class VersionResultCached {

    Map<Integer, VersionResult> versionResults = new ConcurrentHashMap<>();

    public void updateVersionResult(Integer hashCode, VersionResult versionResult) {
        versionResults.put(hashCode, versionResult);
    }

    public void clearBeforeReload() {
        versionResults.clear();
    }

    public int size() {
        return versionResults.size();
    }

    public VersionResult getVersionResult(Integer hashcode) {
        return versionResults.getOrDefault(hashcode, null);
    }

}
