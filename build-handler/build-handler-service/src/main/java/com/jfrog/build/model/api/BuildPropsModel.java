package com.jfrog.build.model.api;

import com.jfrog.build.model.BuildProps;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BuildPropsModel {

    private String key;
    private String value;
    private String status;
    private String prevValue;
    private String prevKey;

    public static BuildPropsModel from(BuildProps buildProps) {
        String status = buildProps.getStatus() != null ? buildProps.getStatus() : null;
        return BuildPropsModel.builder()
                .key(buildProps.getKey())
                .value(buildProps.getValue())
                .status(status)
                .build();
    }
}
