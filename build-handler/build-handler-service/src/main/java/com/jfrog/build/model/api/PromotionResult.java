package com.jfrog.build.model.api;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.LinkedList;
import java.util.List;

public class PromotionResult {

    public List<PromotionResultMessages> messages = new LinkedList<>();

    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionResultMessages {

        public String level;
        public String message;
    }

    public boolean errorsOrWarningHaveOccurred() {
        for (PromotionResultMessages message : messages) {
            if ("ERROR".equals(message.level) || "WARNING".equals(message.level)) {
                return true;
            }
        }
        return false;
    }
}
