package org.artifactory.missioncontrol;

import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.springframework.stereotype.Service;

/**
 * @author Alexei Vainshtein
 */
@Service
public class DefaultMissionControlServiceImpl implements MissionControlService {

    @Override
    public void importFrom(ImportSettings settings) {
        throw new UnsupportedOperationException("Operation on Mission Control not allowed under this license.");
    }

    @Override
    public void exportTo(ExportSettings settings) {
        throw new UnsupportedOperationException("Operation on Mission Control not allowed under this license.");
    }

    @Override
    public String getServiceToken() {
        throw new UnsupportedOperationException("Operation on Mission Control not allowed under this license.");
    }

    @Override
    public boolean isMissionControlEnabled() {
        return false;
    }
}
