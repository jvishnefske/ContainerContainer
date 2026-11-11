package com.jfrog.build.model.api;

import com.jfrog.build.acl.model.ProducedBy;
import com.jfrog.build.acl.model.UsedBy;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * The builds related to an artifact: the builds that use it as a dependency ({@code usedBy}) and the builds that
 * produced it as an output ({@code producedBy}).
 */
@Value
@Builder
@Jacksonized
public class ArtifactBuildsModel {

    List<UsedBy> usedBy;
    List<ProducedBy> producedBy;
}
