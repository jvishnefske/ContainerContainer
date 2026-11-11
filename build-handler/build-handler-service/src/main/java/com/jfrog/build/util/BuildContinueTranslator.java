package com.jfrog.build.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.model.BuildIdImpl;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.model.api.ContinueBuildDto;
import com.jfrog.build.model.api.ContinuePageDto;
import com.jfrog.build.model.api.GeneralBuildInfo;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.common.JsonUtils;
import org.jfrog.commons.continues.ContinuePage;
import org.jfrog.commons.continues.util.Direction;

import java.io.IOException;
import java.util.Base64;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
@UtilityClass
public class BuildContinueTranslator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().setDefaultPropertyInclusion(NON_NULL);

    public static ContinueBuildFilter toBuildFilter(ContinueBuildDto continueBuildDto) {
        ContinuePage continuePage = toContinuePage(continueBuildDto);
        ContinueBuildFilter continueBuildFilter = new ContinueBuildFilter(continuePage);
        continueBuildFilter.setSearchStr(continueBuildDto.getSearchStr());
        if (StringUtils.isNotBlank(continueBuildDto.getOrderBy())) {
            continueBuildFilter
                    .setOrderBy(ContinueBuildFilter.OrderBy.valueOf(continueBuildDto.getOrderBy().toUpperCase()));
        }
        if (continueBuildDto.getDirection() == null &&
                continueBuildFilter.getOrderBy() == ContinueBuildFilter.OrderBy.BUILD_DATE) {
            continueBuildFilter.setDirection(Direction.DESC);
        }

        BuildId continueBuildId = base64ToBuildId(continueBuildDto.getContinueState());
        if (continueBuildId != null) {
            continueBuildFilter.setContinueBuildId(continueBuildId);
        } else {
            String buildRepo = continueBuildDto.getBuildRepo();
            if (StringUtils.isNotBlank(buildRepo)) {
                continueBuildFilter.setBuildRepo(buildRepo);
            }
        }
        return continueBuildFilter;
    }

    private static ContinuePage toContinuePage(ContinuePageDto continuePageDto) {
        ContinuePage continuePage = new ContinuePage();
        continuePage.setMustInclude(continuePageDto.getMustInclude());
        if (continuePageDto.getDirection() != null) {
            continuePage.setDirection(continuePageDto.getDirection());
        }
        if (continuePageDto.getLimit() != null) {
            continuePage.setLimit(continuePageDto.getLimit());
        }
        return continuePage;
    }

    static GeneralBuildInfo base64ToGeneralBuildInfo(String b64) {
        return isNotBlank(b64) ?
                JsonUtils.getInstance().readValue(Base64.getDecoder().decode(b64), GeneralBuildInfo.class) :
                null;
    }

    static BuildId base64ToBuildId(String b64) {
        GeneralBuildInfo generalBuildInfo = base64ToGeneralBuildInfo(b64);
        return generalBuildInfo != null ?
                new BuildIdImpl(-1, generalBuildInfo.getBuildName(),
                        generalBuildInfo.getBuildNumber(), generalBuildInfo.getTime(),
                        generalBuildInfo.getBuildRepo()) : null;
    }

    public static String buildIdToBase64(GeneralBuildInfo buildId) {
        if (buildId == null) {
            return null;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(buildId);
            return Base64.getEncoder().encodeToString(json.getBytes());
        } catch (IOException e) {
            log.error("Failed to write Value for build id '{}'", buildId);
            return null;
        }
    }
}
