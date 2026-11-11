package org.artifactory.missioncontrol;

import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;

/**
 * @author Alexei Vainshtein
 */

public interface MissionControlService {

    void importFrom(ImportSettings settings);

    void exportTo(ExportSettings settings);

    boolean isMissionControlEnabled();

    String getServiceToken();
}
