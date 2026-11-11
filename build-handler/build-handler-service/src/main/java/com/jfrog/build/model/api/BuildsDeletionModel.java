package com.jfrog.build.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BuildsDeletionModel {

    private String buildName;
    private String buildRepo;
    private String project;
    private List<String> buildNumbers;

    private boolean deleteArtifacts = false;
    private boolean deleteAll = false;
}