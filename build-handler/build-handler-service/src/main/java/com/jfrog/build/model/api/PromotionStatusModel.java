package com.jfrog.build.model.api;

import lombok.Builder;
import lombok.Data;
import org.jfrog.build.api.release.PromotionStatus;

@Data
@Builder
public class PromotionStatusModel {

    private String repository;
    private String comment;
    private String ciUser;
    private String user;
    private String status;
    private String timestamp;

    public static PromotionStatusModel from(PromotionStatus promotionStatus) {
        return PromotionStatusModel.builder()
                .status(promotionStatus.getStatus())
                .ciUser(promotionStatus.getCiUser())
                .comment(promotionStatus.getComment())
                .repository(promotionStatus.getRepository())
                .timestamp(promotionStatus.getTimestamp())
                .user(promotionStatus.getUser())
                .build();
    }
}
