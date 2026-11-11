package org.artifactory.ui.rest.model.federated;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.artifactory.common.config.Conflict;

import java.util.Collection;

@Getter
@AllArgsConstructor
public class NegotiationConflictMemberModel {
    private String url;
    private Collection<Conflict> conflicts;
}
