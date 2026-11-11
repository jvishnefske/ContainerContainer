package org.artifactory.api.search.remote;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.search.SearchControlsBase;

/**
 * @author Oleksandr Legenkiy
 */
public class RemoteSearchControls extends SearchControlsBase {

    private String searchKey;

    @Override
    public boolean isEmpty() {
        return StringUtils.isBlank(searchKey);
    }

    @Override
    public boolean isWildcardsOnly() {
        return isWildcardsOnly(searchKey);
    }

    public String getSearchKey() {
        return searchKey;
    }

    public void setSearchKey(String searchKey) {
        this.searchKey = searchKey;
    }
}
