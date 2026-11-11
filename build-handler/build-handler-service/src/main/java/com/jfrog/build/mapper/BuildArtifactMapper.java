package com.jfrog.build.mapper;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.api.BuildArtifactsMapping;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves build artifacts mappings patterns according to the given file-infos.
 */
@Slf4j
public class BuildArtifactMapper {

    private static final Pattern PLACE_HOLDER_PATTERN = Pattern.compile("\\$([1-9]\\d*)");

    /**
     * Returns a map of file-info => target relative directory of build artifacts with their regexp mappings.
     *
     * @param fileInfos The file infos to apply the mappings patterns on
     * @param mappings  The regexp input and output mappings
     */
    public static Map<BuildFileInfo, String> resolve(
            Set<BuildFileInfo> fileInfos,
            List<BuildArtifactsMapping> mappings) {
        if (fileInfos == null || fileInfos.isEmpty()) {
            // Nothing to do here
            return null;
        }

        Map<BuildFileInfo, String> resultMap = isNoMappings(fileInfos, mappings);
        if (resultMap != null) {
            return resultMap;
        }

        resultMap = Maps.newHashMap();
        for (BuildArtifactsMapping mapping : mappings) {
            String mappingInput = mapping.getInput();
            if (StringUtils.isBlank(mappingInput)) {
                log.warn("Skipping empty mapping input pattern");
                continue;
            }
            matchMappingArtifacts(mapping, fileInfos, resultMap);
        }

        return resultMap;
    }

    private static Map<BuildFileInfo, String> isNoMappings(
            Set<BuildFileInfo> fileInfos,
            List<BuildArtifactsMapping> mappings) {
        // In case of no mappings, simply return everything
        if (mappings == null) {
            Map<BuildFileInfo, String> resultMap = Maps.newHashMap();
            for (BuildFileInfo fileInfo : fileInfos) {
                resultMap.put(fileInfo, fileInfo.getRelativePath());
            }
            return resultMap;
        }

        return null;
    }

    private static void matchMappingArtifacts(
            BuildArtifactsMapping mapping,
            Set<BuildFileInfo> fileInfos,
            Map<BuildFileInfo, String> resultMap) {
        Pattern mappingInputPattern = Pattern.compile(mapping.getInput());
        for (BuildFileInfo fileInfo : fileInfos) {
            String relPath = fileInfo.getRepoPath().getRelativePath();
            Matcher fileInfoMatcher = mappingInputPattern.matcher(relPath);
            if (fileInfoMatcher.matches()) {
                String mappingOutput = mapping.getOutput();
                if (StringUtils.isBlank(mappingOutput)) {
                    // In case we didn't receive an output pattern, the target will be the full path to the file info
                    if (!resultMap.containsKey(fileInfo)) {
                        resultMap.put(fileInfo, relPath);
                    }
                } else {
                    mapToOutputPath(mapping, fileInfo, resultMap, fileInfoMatcher);
                }
            }
        }
    }

    private static void mapToOutputPath(
            BuildArtifactsMapping mapping,
            BuildFileInfo fileInfo,
            Map<BuildFileInfo, String> resultMap,
            Matcher fileInfoMatcher) {

        Pattern mappingInputPattern = Pattern.compile(mapping.getInput());
        int inputGroupsNum = mappingInputPattern.matcher("").groupCount();
        List<String> placeHoldersList = getPlaceHoldersList(mapping, inputGroupsNum);

        List<String> replacementList = Lists.newArrayList();
        for (int i = 1; i <= inputGroupsNum; i++) {
            replacementList.add(fileInfoMatcher.group(i));
        }

        String[] searchArray = placeHoldersList.toArray(new String[placeHoldersList.size()]);
        String[] replacementArray = replacementList.toArray(new String[replacementList.size()]);
        String replacedRelPath = StringUtils.replaceEach(mapping.getOutput(), searchArray, replacementArray);
        if (!resultMap.containsKey(fileInfo)) {
            resultMap.put(fileInfo, replacedRelPath);
        }
    }

    private static List<String> getPlaceHoldersList(BuildArtifactsMapping mapping, int inputGroupsNum) {
        List<String> placeHoldersList = Lists.newLinkedList();
        String mappingOutput = mapping.getOutput();
        if (StringUtils.isBlank(mappingOutput) || inputGroupsNum <= 0) {
            // No output mapping or input has no capturing groups
            return placeHoldersList;
        }

        Matcher placeHoldersMatcher = PLACE_HOLDER_PATTERN.matcher(mappingOutput);
        while (placeHoldersMatcher.find()) {
            String group = placeHoldersMatcher.group(1);
            int placeHolderGroupNum;
            try {
                placeHolderGroupNum = Integer.parseInt(group);
                if (placeHolderGroupNum > inputGroupsNum) {
                    String errorMessage = String.format(
                            "Place holder number '%s' exceeds the number of capture groups ('%s') in the matching " +
                                    "mapping input '%s'",
                            placeHolderGroupNum, inputGroupsNum, mapping.getInput());
                    throw new IllegalArgumentException(errorMessage);
                }
            } catch (NumberFormatException e) {
                String errorMessage = String.format(
                        "Place holder '%s' from mapping output '%s' is not a valid number", group, mappingOutput);
                throw new IllegalArgumentException(errorMessage);
            }
            String placeHolderKey = "$" + group;
            if (!placeHoldersList.contains(placeHolderKey)) {
                placeHoldersList.add(placeHolderKey);
            }
        }

        if (placeHoldersList.size() != inputGroupsNum) {
            String errorMessage = String.format(
                    "Number of place holders in output pattern (%s) is different from the number of groups (%s) in " +
                            "input pattern",
                    placeHoldersList.size(), inputGroupsNum);
            throw new IllegalArgumentException(errorMessage);
        }

        Collections.sort(placeHoldersList);
        return placeHoldersList;
    }
}
