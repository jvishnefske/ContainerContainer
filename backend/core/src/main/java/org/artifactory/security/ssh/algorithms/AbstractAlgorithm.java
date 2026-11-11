package org.artifactory.security.ssh.algorithms;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.common.NamedResource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractAlgorithm<T extends NamedResource> {

    public List<T> setUpAlgorithms() {
        Map<String, T> avail;
        List<String> include = getInclude();
        List<T> algorithms;
        if (!include.isEmpty()) {
            avail = getAll().stream()
                    .collect(Collectors.toMap(NamedResource::getName, Function.identity()));
            algorithms = includeOnly(avail);
        } else {
            avail = getDefaults().stream()
                    .collect(Collectors.toMap(NamedResource::getName, Function.identity()));
            algorithms = filterAlgorithmByBlackList(avail);
        }
        return algorithms;
    }

    List<T> filterAlgorithmByBlackList(Map<String, T> algorithms) {
        List<String> exclude = getExclude();
        if (!exclude.isEmpty()) {
            return algorithms.entrySet().stream()
                    .filter(algorithm -> !exclude.contains(algorithm.getKey()))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>(algorithms.values());
    }

    List<T> includeOnly(Map<String, T> algorithms) {
        List<T> list = new ArrayList<>();
        List<String> requestedAlgorithms = getInclude();
        for (String requested : requestedAlgorithms) {
            if (StringUtils.isNotBlank(requested)) {
                algorithms.entrySet().stream().filter(alg -> requested.trim().equals(alg.getKey())).findFirst()
                        .map(Map.Entry::getValue)
                        .ifPresentOrElse(list::add, () -> log.warn("Unable to find algorithm {}", requested));
            }
        }
        return list;
    }

    List<String> split(String split) {
        if (StringUtils.isBlank(split)) {
            return Collections.emptyList();
        }
        return Arrays.asList(split.split(","));
    }

    @NonNull
    abstract List<String> getInclude();

    @NonNull
    abstract List<String> getExclude();

    @NonNull
    abstract List<T> getAll();

    @NonNull
    abstract List<T> getDefaults();
}
