package com.jfrog.build.model.api;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jfrog.commons.continues.util.Direction;

import java.util.Map;

import static java.util.Objects.nonNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class ContinuePageDto {

    String continueState;
    String mustInclude;
    Long limit;
    Direction direction;

    public ContinuePageDto(Map<String, String[]> params) {
        this.continueState = getFirst(params, "continueState");
        this.mustInclude = getFirst(params, "mustInclude");
        String limitStr = getFirst(params, "limit");
        if (limitStr != null) {
            this.limit = Long.parseLong(limitStr);
        }
        String directionStr = getFirst(params, "direction");
        if (directionStr != null) {
            this.direction = Direction.valueOf(directionStr.toUpperCase());
        }
    }

    ContinuePageDto(ContinuePageDto orig) {
        this(orig.continueState, orig.mustInclude, orig.limit, orig.direction);
    }

    protected String getFirst(Map<String, String[]> params, String key) {
        return nonNull(params.get(key)) && params.get(key).length > 0 ? params.get(key)[0] : null;
    }
}
