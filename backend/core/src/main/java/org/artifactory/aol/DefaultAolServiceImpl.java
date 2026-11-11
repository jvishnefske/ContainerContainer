package org.artifactory.aol;

import org.artifactory.security.UserInfo;
import org.springframework.stereotype.Service;

/**
 * @author Alexei Vainshtein
 */
@Service
public class DefaultAolServiceImpl implements AolService {

    @Override
    public boolean isDashboardUser() {
        return false;
    }

    @Override
    public boolean isAolAdmin(UserInfo userInfo) {
        return false;
    }

    @Override
    public boolean isAol() {
        return false;
    }
}
