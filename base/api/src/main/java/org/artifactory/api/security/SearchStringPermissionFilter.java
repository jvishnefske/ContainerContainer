package org.artifactory.api.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jfrog.commons.continues.ContinueIndexPage;


@Data
@EqualsAndHashCode(callSuper=false)
@NoArgsConstructor
@AllArgsConstructor
public class SearchStringPermissionFilter extends ContinueIndexPage {

    String searchStr;

    public SearchStringPermissionFilter(ContinueIndexPage continueIndex) {
        super(continueIndex);
    }

    public SearchStringPermissionFilter(SearchStringPermissionFilter other) {
        super(other);
        this.searchStr = other.searchStr;
    }

}
