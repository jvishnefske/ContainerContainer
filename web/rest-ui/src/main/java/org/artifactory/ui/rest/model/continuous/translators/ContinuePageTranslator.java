package org.artifactory.ui.rest.model.continuous.translators;


import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.ui.rest.model.continuous.dtos.ContinuePageDto;
import org.jfrog.commons.continues.ContinueIndexPage;
import org.jfrog.commons.continues.ContinuePage;

@UtilityClass
class ContinuePageTranslator {

    static ContinuePage toContinuePage(ContinuePageDto continuePageDto) {
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

    static ContinueIndexPage toContinueIndex(ContinuePageDto continuePageDto) {
        ContinuePage continuePage = toContinuePage(continuePageDto);
        ContinueIndexPage continueIndex = new ContinueIndexPage(continuePage);
        if (StringUtils.isNotBlank(continuePageDto.getContinueState())) {
            continueIndex.setContinueIndex(Long.parseLong(continuePageDto.getContinueState()));
        }
        return continueIndex;
    }

}
