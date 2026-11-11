package com.jfrog.build.model.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jfrog.build.acl.model.BuildId;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jfrog.commons.continues.ContinuePage;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor(onConstructor_={@JsonIgnore})
@Getter
@Setter
public class ContinueBuildFilter extends ContinuePage {

    BuildId continueBuildId;
    OrderBy orderBy = OrderBy.BUILD_DATE;
    String searchStr;
    String buildRepo;

    public ContinueBuildFilter(ContinuePage continuePage) {
        super(continuePage);
    }

    public ContinueBuildFilter(ContinueBuildFilter other) {
        super(other);
        this.orderBy = other.orderBy;
        this.searchStr = other.searchStr;
        this.continueBuildId = other.continueBuildId;
        this.buildRepo = other.buildRepo;
    }

    public String getOrderByStr() {
        return orderBy.name().toLowerCase();
    }

    public enum OrderBy {
        BUILD_NAME, BUILD_NUMBER, BUILD_ID, BUILD_DATE;

        public String lower() {
            return this.name().toLowerCase();
        }
    }

}
