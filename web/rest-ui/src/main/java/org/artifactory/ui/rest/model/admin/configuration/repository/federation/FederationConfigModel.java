package org.artifactory.ui.rest.model.admin.configuration.repository.federation;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.rest.common.model.RestModel;

import java.util.ArrayList;
import java.util.List;

/**
 * @author AndreiK.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@JsonTypeName("federation")
public class FederationConfigModel implements RestModel {

    protected List<MirrorMemberConfigModel> mirrors;
    protected String sourceRepo;
    private String proxy;
    private String sourceRepoType;

    public List<String> mirrorsToUrls() {
        List<String> urls = new ArrayList<>();
        mirrors.forEach(mirrorMemberConfigModel -> urls.add(mirrorMemberConfigModel.getUrl()));
        return urls;
    }
}
