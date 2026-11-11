package org.artifactory.util;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class ThreadMarkerData {

    private final Map<String, Object> data = new LinkedHashMap<>();
}