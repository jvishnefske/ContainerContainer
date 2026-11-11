package org.artifactory.ui.rest.model.federated;

import lombok.Getter;
import org.artifactory.common.config.Conflict;
import org.artifactory.rest.common.model.BaseModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Getter
public class NegotiationConflictsModel extends BaseModel {

    private final String error;
    private List<NegotiationConflictMemberModel> members;

    public NegotiationConflictsModel(String error, Map<String, Collection<Conflict>> conflictsPerMember) {
        this.error = error;
        if (conflictsPerMember == null || conflictsPerMember.isEmpty()) {
            return;
        }
        this.members = new ArrayList<>();
        conflictsPerMember.forEach((url, conflicts) ->
                this.members.add(new NegotiationConflictMemberModel(url, conflicts)));
    }
}
